package com.spendsense.api.dto;

import com.spendsense.api.entity.Transaction;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class TransactionResponse {

    private Long id;
    private Long statementId;
    private LocalDate date;
    private String description;
    private BigDecimal amount;
    private String type;
    private String aiCategory;
    private String userCategory;
    private Boolean isOverridden;

    /** The effective category — user override takes precedence over AI */
    private String effectiveCategory;

    public static TransactionResponse from(Transaction t) {
        String effective = t.getUserCategory() != null ? t.getUserCategory() : t.getAiCategory();
        return TransactionResponse.builder()
                .id(t.getId())
                .statementId(t.getStatement().getId())
                .date(t.getDate())
                .description(t.getDescription())
                .amount(t.getAmount())
                .type(t.getType())
                .aiCategory(t.getAiCategory())
                .userCategory(t.getUserCategory())
                .isOverridden(t.getIsOverridden())
                .effectiveCategory(effective)
                .build();
    }
}
