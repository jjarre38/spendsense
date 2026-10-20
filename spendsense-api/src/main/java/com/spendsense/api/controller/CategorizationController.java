package com.spendsense.api.controller;

import com.spendsense.api.entity.User;
import com.spendsense.api.service.ClaudeCategorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/statements")
@RequiredArgsConstructor
public class CategorizationController {

    private final ClaudeCategorizationService categorizationService;

    /**
     * POST /api/statements/{id}/categorize
     * Triggers AI categorization for all transactions on a parsed statement.
     * Idempotent — can be re-called to re-categorize.
     */
    @PostMapping("/{id}/categorize")
    public ResponseEntity<Void> categorize(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {

        categorizationService.categorizeStatement(id, user.getId());
        return ResponseEntity.accepted().build();
    }
}
