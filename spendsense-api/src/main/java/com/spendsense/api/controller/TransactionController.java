package com.spendsense.api.controller;

import com.spendsense.api.dto.CategoryOverrideRequest;
import com.spendsense.api.dto.TransactionResponse;
import com.spendsense.api.entity.Transaction;
import com.spendsense.api.entity.User;
import com.spendsense.api.service.StatementService;
import com.spendsense.api.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;
    private final StatementService statementService;

    /**
     * GET /api/transactions/{statementId}
     * Returns the full transaction list (with AI and user categories) for a statement.
     */
    @GetMapping("/{statementId}")
    public ResponseEntity<List<TransactionResponse>> getByStatement(
            @PathVariable Long statementId,
            @AuthenticationPrincipal User user) {

        statementService.getByIdForUser(statementId, user.getId());
        List<TransactionResponse> responses = transactionService.getByStatementId(statementId)
                .stream()
                .map(TransactionResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    /**
     * PATCH /api/transactions/{id}/category
     * Applies a user override to a transaction's category and records feedback.
     * Both the transaction update and feedback insert are atomic.
     */
    @PatchMapping("/{id}/category")
    public ResponseEntity<TransactionResponse> overrideCategory(
            @PathVariable Long id,
            @Valid @RequestBody CategoryOverrideRequest request,
            @AuthenticationPrincipal User user) {

        Transaction updated = transactionService.overrideCategory(id, request.getCategory(), user.getId());
        return ResponseEntity.ok(TransactionResponse.from(updated));
    }
}
