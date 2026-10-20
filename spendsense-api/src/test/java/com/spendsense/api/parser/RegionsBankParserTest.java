package com.spendsense.api.parser;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RegionsBankParserTest {

    private final RegionsBankParser parser = new RegionsBankParser();

    private static final String STATEMENT = """
            Regions Bank
            LIFEGREEN CHECKING
            December 16, 2023 through January 15, 2024
            ACCOUNT SUMMARY
            Beginning Balance $1,000.00
            Deposits & Credits $2,500.00
            Withdrawals $245.92
            Fees $8.00
            Checks $225.25
            Ending Balance $3,020.83
            DEPOSITS & CREDITS
            01/05 Payroll Deposit ACME Corp 2,500.00
            Total Deposits & Credits $2,500.00
            WITHDRAWALS
            12/30 Card Purchase 12/29 Kroger #123 Huntsville AL 54.20
            01/03 Card Purchase 01/02 Walmart Sc 1234 Huntsville AL 45.67
            01/08 Netflix.com 15.99
            01/09 Alabama Power Co Bill Pay 130.06
            Total Withdrawals $245.92
            FEES
            01/15 Monthly Fee 8.00
            CHECKS
            Date Check No. Amount Date Check No. Amount
            01/10 1001 150.00 01/12 1002* 75.25
            DAILY BALANCE SUMMARY
            12/30 945.80 01/03 900.13
            01/05 3,400.13 01/08 3,384.14
            Page 1 of 2
            """;

    @Test
    void parsesSectionsWithInferredYearAndTypes() {
        List<ParsedTransaction> txs = parser.parse(STATEMENT);

        assertThat(txs).hasSize(8);

        ParsedTransaction payroll = txs.get(0);
        assertThat(payroll.getDate()).isEqualTo(LocalDate.of(2024, 1, 5));
        assertThat(payroll.getDescription()).isEqualTo("Payroll Deposit ACME Corp");
        assertThat(payroll.getAmount()).isEqualByComparingTo("2500.00");
        assertThat(payroll.getType()).isEqualTo("CREDIT");

        // December row on a January statement belongs to the previous year
        ParsedTransaction kroger = txs.get(1);
        assertThat(kroger.getDate()).isEqualTo(LocalDate.of(2023, 12, 30));
        assertThat(kroger.getType()).isEqualTo("DEBIT");
        assertThat(kroger.getAmount()).isEqualByComparingTo("54.20");

        assertThat(txs.get(5).getDescription()).isEqualTo("Monthly Fee");
        assertThat(txs.get(5).getType()).isEqualTo("DEBIT");

        assertThat(txs.get(6).getDescription()).isEqualTo("Check #1001");
        assertThat(txs.get(7).getDescription()).isEqualTo("Check #1002");
        assertThat(txs.get(7).getAmount()).isEqualByComparingTo("75.25");

        BigDecimal debits = txs.stream()
                .filter(t -> t.getType().equals("DEBIT"))
                .map(ParsedTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(debits).isEqualByComparingTo("479.17"); // 245.92 + 8.00 + 225.25
    }

    @Test
    void parsesFullDatesSignedAmountsAndRunningBalance() {
        String text = """
                01/15/2024 WALMART SUPERCENTER #1234 -45.67 1,234.56
                01/16/2024 DIRECT DEPOSIT EMPLOYER 1,500.00 2,734.56
                01/17/2024 SHELL OIL 57444 32.10-
                """;

        List<ParsedTransaction> txs = parser.parse(text);

        assertThat(txs).hasSize(3);
        assertThat(txs.get(0).getType()).isEqualTo("DEBIT");
        assertThat(txs.get(0).getAmount()).isEqualByComparingTo("45.67");
        assertThat(txs.get(0).getDescription()).isEqualTo("WALMART SUPERCENTER #1234");
        assertThat(txs.get(1).getType()).isEqualTo("CREDIT");
        assertThat(txs.get(1).getAmount()).isEqualByComparingTo("1500.00");
        assertThat(txs.get(2).getType()).isEqualTo("DEBIT");
        assertThat(txs.get(2).getDate()).isEqualTo(LocalDate.of(2024, 1, 17));
    }

    @Test
    void ignoresNonTransactionText() {
        assertThat(parser.parse("Thank you for banking with Regions.\nPage 2 of 2\n")).isEmpty();
    }
}
