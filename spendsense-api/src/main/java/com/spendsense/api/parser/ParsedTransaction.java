package com.spendsense.api.parser;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Intermediate value object produced by a BankParser before DB persistence.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedTransaction {

    private LocalDate date;
    private String description;
    private BigDecimal amount;
    /** "DEBIT" or "CREDIT" */
    private String type;
}
