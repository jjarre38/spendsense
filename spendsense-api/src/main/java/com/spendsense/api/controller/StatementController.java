package com.spendsense.api.controller;

import com.spendsense.api.dto.MonthlySpendResponse;
import com.spendsense.api.dto.StatementResponse;
import com.spendsense.api.dto.SummaryResponse;
import com.spendsense.api.dto.TransactionResponse;
import com.spendsense.api.entity.Statement;
import com.spendsense.api.entity.User;
import com.spendsense.api.service.StatementService;
import com.spendsense.api.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/statements")
@RequiredArgsConstructor
public class StatementController {

    private final StatementService statementService;
    private final TransactionService transactionService;

    /**
     * POST /api/statements/upload
     * Accepts a PDF bank statement and parses it into transactions.
     *
     * @param file     the PDF file
     * @param bankType optional bank type (defaults to REGIONS)
     * @param user     the authenticated user (injected by Spring Security)
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StatementResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "bankType", defaultValue = "REGIONS") Statement.BankType bankType,
            @AuthenticationPrincipal User user) {

        Statement statement = statementService.uploadAndParse(file, bankType, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(StatementResponse.from(statement));
    }

    /**
     * GET /api/statements
     * Returns all statements for the authenticated user.
     */
    @GetMapping
    public ResponseEntity<List<StatementResponse>> listStatements(@AuthenticationPrincipal User user) {
        List<StatementResponse> responses = statementService.getByUserId(user.getId())
                .stream()
                .map(StatementResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    /**
     * GET /api/statements/monthly-spend
     * Returns DEBIT spend per calendar month across all of the user's statements.
     */
    @GetMapping("/monthly-spend")
    public ResponseEntity<List<MonthlySpendResponse>> getMonthlySpend(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(transactionService.getMonthlySpend(user.getId()));
    }

    /**
     * GET /api/statements/{id}/transactions
     * Returns all parsed transactions for a statement owned by the authenticated user.
     */
    @GetMapping("/{id}/transactions")
    public ResponseEntity<List<TransactionResponse>> getTransactions(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {

        // Validate ownership
        statementService.getByIdForUser(id, user.getId());

        List<TransactionResponse> responses = transactionService.getByStatementId(id)
                .stream()
                .map(TransactionResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    /**
     * GET /api/statements/{id}/summary
     * Returns aggregated spending by effective category for the dashboard.
     * Effective category = COALESCE(user_category, ai_category).
     */
    @GetMapping("/{id}/summary")
    public ResponseEntity<SummaryResponse> getSummary(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {

        statementService.getByIdForUser(id, user.getId());
        return ResponseEntity.ok(transactionService.getSummary(id));
    }
}
