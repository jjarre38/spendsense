package com.spendsense.api.service;

import com.spendsense.api.dto.MonthlySpendResponse;
import com.spendsense.api.dto.SummaryResponse;
import com.spendsense.api.entity.CategoryFeedback;
import com.spendsense.api.entity.Statement;
import com.spendsense.api.entity.Transaction;
import com.spendsense.api.parser.ParsedTransaction;
import com.spendsense.api.repository.CategoryFeedbackRepository;
import com.spendsense.api.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final CategoryFeedbackRepository categoryFeedbackRepository;

    /**
     * Bulk-inserts parsed transactions linked to the given statement.
     */
    @Transactional
    public List<Transaction> saveParsedTransactions(Statement statement, List<ParsedTransaction> parsed) {
        List<Transaction> entities = parsed.stream()
                .map(p -> Transaction.builder()
                        .statement(statement)
                        .date(p.getDate())
                        .description(p.getDescription())
                        .amount(p.getAmount())
                        .type(p.getType())
                        .build())
                .toList();
        return transactionRepository.saveAll(entities);
    }

    /**
     * Returns all transactions for a statement, scoped to the authenticated user.
     */
    public List<Transaction> getByStatementId(Long statementId) {
        return transactionRepository.findByStatementIdOrderByDateAscIdAsc(statementId);
    }

    /**
     * Loads a single transaction, throwing 404 if not found.
     */
    public Transaction getById(Long id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));
    }

    /**
     * Applies a user category override to a transaction and records the feedback.
     * Both the transaction update and the feedback insert are atomic (@Transactional).
     *
     * @param id       transaction id
     * @param category the corrected category chosen by the user
     * @param userId   used to validate that the transaction belongs to this user's statement
     * @return the updated transaction
     */
    @Transactional
    public Transaction overrideCategory(Long id, String category, Long userId) {
        if (!ClaudeCategorizationService.VALID_CATEGORIES.contains(category)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown category: " + category);
        }

        Transaction tx = getById(id);

        // Ownership check via statement → user
        Long statementOwnerId = tx.getStatement().getUser().getId();
        if (!statementOwnerId.equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        String originalCategory = tx.getUserCategory() != null
                ? tx.getUserCategory()
                : tx.getAiCategory();

        // Update the transaction
        tx.setUserCategory(category);
        tx.setIsOverridden(true);
        transactionRepository.save(tx);

        // Record feedback for future model analysis
        CategoryFeedback feedback = CategoryFeedback.builder()
                .transaction(tx)
                .originalCategory(originalCategory)
                .correctedCategory(category)
                .build();
        categoryFeedbackRepository.save(feedback);

        return tx;
    }

    /**
     * Builds a spending summary for a statement.
     * Effective category = COALESCE(userCategory, aiCategory).
     * Only DEBIT transactions contribute to spend totals.
     */
    public SummaryResponse getSummary(Long statementId) {
        List<Transaction> transactions = transactionRepository.findByStatementIdOrderByDateAscIdAsc(statementId);

        // Aggregate spend by effective category (debits only)
        Map<String, BigDecimal> spendByCategory = transactions.stream()
                .filter(tx -> "DEBIT".equalsIgnoreCase(tx.getType()))
                .filter(tx -> Objects.nonNull(tx.getAmount()))
                .collect(Collectors.groupingBy(
                        tx -> tx.getUserCategory() != null ? tx.getUserCategory() : (tx.getAiCategory() != null ? tx.getAiCategory() : "Uncategorized"),
                        Collectors.reducing(BigDecimal.ZERO, Transaction::getAmount, BigDecimal::add)
                ));

        BigDecimal totalSpend = spendByCategory.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String topCategory = spendByCategory.entrySet().stream()
                .max(Comparator.comparing(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse("N/A");

        return SummaryResponse.builder()
                .totalSpend(totalSpend)
                .transactionCount(transactions.size())
                .topCategory(topCategory)
                .spendByCategory(spendByCategory)
                .build();
    }

    /**
     * Aggregates DEBIT spend per calendar month across all of a user's statements,
     * for the dashboard's month-over-month chart. Months are returned in ascending order.
     */
    public List<MonthlySpendResponse> getMonthlySpend(Long userId) {
        Map<YearMonth, BigDecimal> byMonth = transactionRepository.findAllByUserId(userId).stream()
                .filter(tx -> "DEBIT".equalsIgnoreCase(tx.getType()))
                .filter(tx -> tx.getDate() != null && tx.getAmount() != null)
                .collect(Collectors.groupingBy(
                        tx -> YearMonth.from(tx.getDate()),
                        TreeMap::new,
                        Collectors.reducing(BigDecimal.ZERO, Transaction::getAmount, BigDecimal::add)
                ));

        return byMonth.entrySet().stream()
                .map(e -> MonthlySpendResponse.builder()
                        .month(e.getKey().toString())
                        .totalSpend(e.getValue())
                        .build())
                .toList();
    }
}
