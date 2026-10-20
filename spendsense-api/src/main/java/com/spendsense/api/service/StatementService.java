package com.spendsense.api.service;

import com.spendsense.api.entity.Statement;
import com.spendsense.api.entity.User;
import com.spendsense.api.parser.ParsedTransaction;
import com.spendsense.api.repository.StatementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class StatementService {

    private final StatementRepository statementRepository;
    private final PdfParserService pdfParserService;
    private final TransactionService transactionService;

    /**
     * Saves the statement record, parses the PDF, and persists all extracted transactions.
     * Statement status transitions: PENDING → PARSED (success) | FAILED (parse error)
     *
     * Deliberately not @Transactional: the FAILED status must be committed even though the
     * method then throws. Transaction inserts are atomic via TransactionService.
     */
    public Statement uploadAndParse(MultipartFile file, Statement.BankType bankType, User user) {
        // 1. Persist statement record in PENDING state
        Statement statement = Statement.builder()
                .user(user)
                .filename(file.getOriginalFilename())
                .bankType(bankType)
                .build();
        statement = statementRepository.save(statement);

        try {
            // 2. Extract and parse transactions from the PDF
            List<ParsedTransaction> parsed = pdfParserService.parse(file, bankType);
            if (parsed.isEmpty()) {
                throw new IllegalStateException(
                        "No transactions found. Is this a " + bankType + " bank statement?");
            }

            // 3. Bulk-insert transactions
            transactionService.saveParsedTransactions(statement, parsed);

            // 4. Mark statement as PARSED
            statement.setStatus(Statement.StatementStatus.PARSED);
            statement = statementRepository.save(statement);

            log.info("Statement {} parsed successfully: {} transactions", statement.getId(), parsed.size());

        } catch (Exception e) {
            log.error("Failed to parse statement {}: {}", statement.getId(), e.getMessage());
            statement.setStatus(Statement.StatementStatus.FAILED);
            statementRepository.save(statement);
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Failed to parse PDF: " + e.getMessage());
        }

        return statement;
    }

    /**
     * Returns all statements owned by the given user, newest first.
     */
    public List<Statement> getByUserId(Long userId) {
        return statementRepository.findByUserIdOrderByUploadedAtDesc(userId);
    }

    /**
     * Loads a statement by ID, validating that it belongs to the given user.
     */
    public Statement getByIdForUser(Long id, Long userId) {
        Statement statement = statementRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Statement not found"));
        if (!statement.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        return statement;
    }

    /**
     * Updates statement status (used by the categorization service in Sub-Task 4).
     */
    @Transactional
    public Statement updateStatus(Long id, Statement.StatementStatus status) {
        Statement statement = statementRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Statement not found"));
        statement.setStatus(status);
        return statementRepository.save(statement);
    }
}
