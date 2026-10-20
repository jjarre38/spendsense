package com.spendsense.api.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendsense.api.entity.Statement;
import com.spendsense.api.entity.Transaction;
import com.spendsense.api.repository.TransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Calls the Claude API (Claude Haiku) to assign spending categories to transactions.
 *
 * Strategy:
 *   - Transactions are batched in groups of 75 to stay well within token limits
 *   - Each batch sends a structured JSON prompt; the response is constrained by a JSON schema
 *     (output_config.format) and additionally validated against the allowed category set
 *   - Up to 3 retries per batch on parse failure; fallback to "Other" after exhaustion
 */
@Service
@Slf4j
public class ClaudeCategorizationService {

    // ── Allowed categories ─────────────────────────────────────────────────
    public static final Set<String> VALID_CATEGORIES = Set.of(
            "Groceries", "Dining", "Utilities", "Transport",
            "Entertainment", "Health", "Shopping", "Income", "Other", "Miscellaneous"
    );

    /** JSON schema passed to output_config.format so Claude's output is structurally guaranteed */
    private static final Map<String, Object> RESPONSE_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "results", Map.of(
                            "type", "array",
                            "items", Map.of(
                                    "type", "object",
                                    "properties", Map.of(
                                            "id", Map.of("type", "integer"),
                                            "category", Map.of("type", "string",
                                                    "enum", VALID_CATEGORIES.stream().sorted().toList())
                                    ),
                                    "required", List.of("id", "category"),
                                    "additionalProperties", false
                            )
                    )
            ),
            "required", List.of("results"),
            "additionalProperties", false
    );

    /** Fallback assigned when a transaction type is unknown or unrecognisable */
    private static final String UNKNOWN_FALLBACK = "Miscellaneous";

    private static final int BATCH_SIZE = 75;
    private static final int MAX_RETRIES = 3;
    private static final String CLAUDE_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String API_VERSION = "2023-06-01";
    private static final String API_KEY_PLACEHOLDER = "YOUR_API_KEY_HERE";

    private final String apiKey;
    private final String model;
    private final String workspaceId;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final TransactionRepository transactionRepository;
    private final StatementService statementService;

    public ClaudeCategorizationService(
            @Value("${claude.api.key}") String apiKey,
            @Value("${claude.model:claude-haiku-5-5}") String model,
            @Value("${claude.workspace.id:}") String workspaceId,
            TransactionRepository transactionRepository,
            StatementService statementService) {
        this.apiKey = apiKey;
        this.model = model;
        this.workspaceId = workspaceId;
        this.transactionRepository = transactionRepository;
        this.statementService = statementService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Categorizes all transactions for a statement.
     * Updates ai_category on each transaction and sets statement status to CATEGORIZED.
     *
     * @param statementId the statement to categorize
     * @param userId      used to validate ownership before proceeding
     */
    @Transactional
    public void categorizeStatement(Long statementId, Long userId) {
        // Ownership check
        statementService.getByIdForUser(statementId, userId);

        if (apiKey == null || apiKey.isBlank() || API_KEY_PLACEHOLDER.equals(apiKey)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Claude API key is not configured. Set claude.api.key in application.properties.");
        }

        List<Transaction> transactions = transactionRepository.findByStatementIdOrderByDateAscIdAsc(statementId);
        if (transactions.isEmpty()) {
            log.warn("No transactions found for statement {}", statementId);
            return;
        }

        log.info("Starting categorization for statement {}: {} transactions in {} batches",
                statementId, transactions.size(),
                (int) Math.ceil((double) transactions.size() / BATCH_SIZE));

        // Process in batches
        for (int i = 0; i < transactions.size(); i += BATCH_SIZE) {
            List<Transaction> batch = transactions.subList(i, Math.min(i + BATCH_SIZE, transactions.size()));
            categorizeBatch(batch);
        }

        // Save all updated transactions
        transactionRepository.saveAll(transactions);

        // Mark statement as CATEGORIZED
        statementService.updateStatus(statementId, Statement.StatementStatus.CATEGORIZED);
        log.info("Categorization complete for statement {}", statementId);
    }

    // ── Private helpers ────────────────────────────────────────────────────

    /**
     * Sends a batch to Claude and updates ai_category on each Transaction in-place.
     * Retries up to MAX_RETRIES times; falls back to UNKNOWN_FALLBACK ("Miscellaneous") on total failure.
     * Transactions whose type is null/blank/unknown are always assigned UNKNOWN_FALLBACK directly.
     */
    private void categorizeBatch(List<Transaction> batch) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                Map<Long, String> categoryMap = callClaude(batch, attempt);
                for (Transaction tx : batch) {
                    if (isUnknownType(tx)) {
                        tx.setAiCategory(UNKNOWN_FALLBACK);
                    } else {
                        String category = categoryMap.getOrDefault(tx.getId(), UNKNOWN_FALLBACK);
                        tx.setAiCategory(VALID_CATEGORIES.contains(category) ? category : UNKNOWN_FALLBACK);
                    }
                }
                return; // success
            } catch (ResponseStatusException e) {
                throw e; // non-retryable (e.g. bad API key)
            } catch (Exception e) {
                log.warn("Batch categorization attempt {}/{} failed: {}", attempt, MAX_RETRIES, e.getMessage());
                if (attempt == MAX_RETRIES) {
                    log.error("All retries exhausted for batch starting at tx {}. Falling back to '{}'.",
                            batch.get(0).getId(), UNKNOWN_FALLBACK);
                    batch.forEach(tx -> tx.setAiCategory(UNKNOWN_FALLBACK));
                }
            }
        }
    }

    /** A transaction type is "unknown" when it is null, blank, or not DEBIT/CREDIT. */
    private boolean isUnknownType(Transaction tx) {
        String type = tx.getType();
        return type == null || type.isBlank()
                || (!type.equalsIgnoreCase("DEBIT") && !type.equalsIgnoreCase("CREDIT"));
    }

    /**
     * Builds the Claude prompt, sends the HTTP request, parses the JSON response,
     * and returns a map of transactionId → category string.
     */
    private Map<Long, String> callClaude(List<Transaction> batch, int attempt) throws Exception {
        String prompt = buildPrompt(batch, attempt);

        // Haiku thinks by default and thinking counts toward max_tokens — leave ample room.
        // Low effort is plenty for classification.
        String requestBody = objectMapper.writeValueAsString(Map.of(
                "model", model,
                "max_tokens", 16000,
                "system", buildSystemPrompt(),
                "output_config", Map.of(
                        "effort", "low",
                        "format", Map.of("type", "json_schema", "schema", RESPONSE_SCHEMA)
                ),
                "messages", List.of(Map.of("role", "user", "content", prompt))
        ));

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(CLAUDE_API_URL))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", API_VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(60));
        // Required when the API key is not scoped to a single workspace
        if (workspaceId != null && !workspaceId.isBlank()) {
            requestBuilder.header("anthropic-workspace-id", workspaceId);
        }
        HttpRequest request = requestBuilder.build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 401 || response.statusCode() == 403) {
            // Not retryable — surface a clear error instead of silently falling back
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Claude API rejected the API key (HTTP " + response.statusCode() + ")");
        }
        if (response.statusCode() >= 400 && response.statusCode() < 500 && response.statusCode() != 429) {
            // Bad request / config problem — retrying won't help and falling back would hide it
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Claude API rejected the request (HTTP " + response.statusCode() + "): " + response.body());
        }
        if (response.statusCode() != 200) {
            throw new RuntimeException("Claude API returned HTTP " + response.statusCode() + ": " + response.body());
        }

        return parseClaudeResponse(response.body());
    }

    /**
     * Parses the Claude API response envelope and extracts the JSON object from the text content block.
     * The response may begin with "thinking" blocks, so the text block is located by type.
     * Expected text payload (enforced by RESPONSE_SCHEMA):
     *   {"results": [{"id": 1, "category": "Groceries"}, ...]}
     */
    @SuppressWarnings("unchecked")
    private Map<Long, String> parseClaudeResponse(String responseBody) throws Exception {
        Map<String, Object> envelope = objectMapper.readValue(responseBody, new TypeReference<>() {});

        if ("max_tokens".equals(envelope.get("stop_reason"))) {
            throw new RuntimeException("Claude response truncated (stop_reason=max_tokens)");
        }
        if ("refusal".equals(envelope.get("stop_reason"))) {
            throw new RuntimeException("Claude declined the request (stop_reason=refusal)");
        }

        List<Map<String, Object>> content = (List<Map<String, Object>>) envelope.get("content");
        if (content == null || content.isEmpty()) {
            throw new RuntimeException("Empty content block in Claude response");
        }

        String text = content.stream()
                .filter(block -> "text".equals(block.get("type")))
                .map(block -> (String) block.get("text"))
                .findFirst()
                .orElse(null);
        if (text == null || text.isBlank()) {
            throw new RuntimeException("No text block in Claude response");
        }

        // Strip markdown code fences defensively
        text = text.trim();
        if (text.startsWith("```")) {
            text = text.replaceAll("^```[a-z]*\\s*", "").replaceAll("\\s*```$", "").trim();
        }

        Map<String, Object> payload = objectMapper.readValue(text, new TypeReference<>() {});
        Object rawResults = payload.get("results");
        if (!(rawResults instanceof List<?>)) {
            throw new RuntimeException("Claude response missing 'results' array");
        }
        List<Map<String, Object>> results = (List<Map<String, Object>>) rawResults;

        return results.stream().collect(Collectors.toMap(
                r -> Long.valueOf(r.get("id").toString()),
                r -> {
                    String cat = String.valueOf(r.getOrDefault("category", UNKNOWN_FALLBACK));
                    return VALID_CATEGORIES.contains(cat) ? cat : UNKNOWN_FALLBACK;
                },
                (a, b) -> a
        ));
    }

    private String buildSystemPrompt() {
        return """
                You are a financial transaction categorizer. Given a list of bank transactions, \
                assign each one exactly one category from this fixed list:
                Groceries, Dining, Utilities, Transport, Entertainment, Health, Shopping, Income, Other, Miscellaneous

                Rules:
                - Respond ONLY with a JSON object of the form {"results": [...]}. No explanation, no markdown.
                - Each element of "results" must have exactly two fields: "id" (integer) and "category" (string).
                - Return one element for every input transaction, using the same id.
                - Use "Miscellaneous" for transactions with an unknown or unrecognisable type.
                - Use "Other" if the type is clear but no specific category fits.
                - Income includes salaries, transfers in, refunds, deposits.

                Example response:
                {"results":[{"id":1,"category":"Groceries"},{"id":2,"category":"Miscellaneous"}]}
                """;
    }

    private String buildPrompt(List<Transaction> batch, int attempt) {
        StringBuilder sb = new StringBuilder();

        if (attempt > 1) {
            sb.append("IMPORTANT: Your previous response was not valid. ")
              .append("Respond ONLY with the JSON object described in the instructions, nothing else.\n\n");
        }

        sb.append("Categorize these transactions:\n\n");
        sb.append("[");
        for (int i = 0; i < batch.size(); i++) {
            Transaction tx = batch.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"id\":").append(tx.getId())
              .append(",\"description\":\"")
              .append(tx.getDescription() == null ? "" : tx.getDescription().replace("\\", " ").replace("\"", "'"))
              .append("\",\"amount\":").append(tx.getAmount())
              .append(",\"type\":\"").append(tx.getType()).append("\"}");
        }
        sb.append("]");
        return sb.toString();
    }
}
