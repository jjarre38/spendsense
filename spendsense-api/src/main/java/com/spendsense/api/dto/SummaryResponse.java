package com.spendsense.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SummaryResponse {

    /** Total spend across all DEBIT transactions (using effective category) */
    private BigDecimal totalSpend;

    /** Total number of transactions */
    private long transactionCount;

    /** Category with the highest spend */
    private String topCategory;

    /** Spend amount per effective category (COALESCE user_category, ai_category) */
    private Map<String, BigDecimal> spendByCategory;
}
