package com.spendsense.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlySpendResponse {

    /** Calendar month in ISO format, e.g. "2024-01" */
    private String month;

    /** Total DEBIT spend in that month across all of the user's statements */
    private BigDecimal totalSpend;
}
