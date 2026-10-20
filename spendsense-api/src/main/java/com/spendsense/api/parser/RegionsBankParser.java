package com.spendsense.api.parser;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for Regions Bank PDF statements.
 *
 * PDFBox (sortByPosition) renders each statement row as a single line with columns separated
 * by single spaces, e.g.:
 *
 *   DEPOSITS &amp; CREDITS
 *   01/05 Payroll Deposit ACME Corp 2,500.00
 *   WITHDRAWALS
 *   01/03 Card Purchase Walmart Sc 1234 Huntsville AL 45.67
 *   CHECKS
 *   01/10 1001 150.00 01/12 1002* 75.25
 *   DAILY BALANCE SUMMARY
 *   01/03 1,954.33 01/05 4,454.33
 *
 * Regions lists withdrawals as positive numbers under a "Withdrawals" heading, so the
 * transaction type is taken from the current section. Outside a known section the sign of
 * the amount decides (negative → DEBIT), falling back to description keywords.
 *
 * Dates may be MM/DD/YYYY or MM/DD; for the latter the year is inferred from the statement
 * period ("January 1, 2024 through January 31, 2024") or from any full date in the text.
 */
@Component
public class RegionsBankParser implements BankParser {

    private enum Section { NONE, CREDITS, DEBITS, CHECKS, SKIP }

    private static final String AMOUNT = "-?\\$?[\\d,]+\\.\\d{2}-?";

    /**
     * date | description | amount | optional running balance
     * Groups: 1 = date, 2 = description, 3 = amount, 4 = balance (optional)
     */
    private static final Pattern TRANSACTION_LINE = Pattern.compile(
            "^(\\d{1,2}/\\d{1,2}(?:/\\d{2,4})?)\\s+(.+?)\\s+(" + AMOUNT + ")(?:\\s+(" + AMOUNT + "))?$"
    );

    /** One check entry within a CHECKS row: date | check number | amount */
    private static final Pattern CHECK_ENTRY = Pattern.compile(
            "(\\d{1,2}/\\d{1,2}(?:/\\d{2,4})?)\\s+(\\d+)\\*?\\s+(" + AMOUNT + ")"
    );

    private static final Pattern LEADING_DATE = Pattern.compile("^\\d{1,2}/\\d{1,2}(?:/\\d{2,4})?\\b");

    private static final Pattern STATEMENT_PERIOD = Pattern.compile(
            "([A-Za-z]+)\\s+(\\d{1,2}),\\s*(\\d{4})\\s+(?:through|thru|to|-)\\s+([A-Za-z]+)\\s+(\\d{1,2}),\\s*(\\d{4})",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern FULL_DATE = Pattern.compile("\\b(\\d{2})/(\\d{2})/(\\d{4})\\b");

    private static final Pattern HAS_LETTER = Pattern.compile("[A-Za-z]");

    private static final List<String> CREDIT_KEYWORDS = List.of(
            "DEPOSIT", "PAYROLL", "DIRECT DEP", "TRANSFER FROM", "REFUND", "INTEREST PAID",
            "INTEREST CREDIT", "CREDIT", "REVERSAL", "CASHBACK REWARD"
    );

    @Override
    public List<ParsedTransaction> parse(String rawText) {
        LocalDate periodEnd = inferPeriodEnd(rawText);
        List<ParsedTransaction> results = new ArrayList<>();
        Section section = Section.NONE;

        for (String line : rawText.split("\\r?\\n")) {
            String trimmed = line.trim().replaceAll("\\s+", " ");
            if (trimmed.isEmpty()) continue;

            if (!LEADING_DATE.matcher(trimmed).find()) {
                Section header = detectSection(trimmed);
                if (header != null) section = header;
                continue;
            }

            if (section == Section.SKIP) continue;

            if (section == Section.CHECKS) {
                results.addAll(parseChecksLine(trimmed, periodEnd));
                continue;
            }

            ParsedTransaction tx = parseTransactionLine(trimmed, section, periodEnd);
            if (tx != null) results.add(tx);
        }

        return results;
    }

    // ── Line parsing ──────────────────────────────────────────────────────

    private ParsedTransaction parseTransactionLine(String line, Section section, LocalDate periodEnd) {
        Matcher m = TRANSACTION_LINE.matcher(line);
        if (!m.matches()) return null;

        String description = m.group(2).trim();
        // Rows in balance tables etc. have no real description
        if (!HAS_LETTER.matcher(description).find()) return null;

        try {
            LocalDate date = parseDate(m.group(1), periodEnd);
            BigDecimal amount = parseAmount(m.group(3));
            if (date == null || amount.signum() == 0) return null;

            String type;
            if (amount.signum() < 0) {
                type = "DEBIT";
            } else if (section == Section.CREDITS) {
                type = "CREDIT";
            } else if (section == Section.DEBITS) {
                type = "DEBIT";
            } else {
                type = looksLikeCredit(description) ? "CREDIT" : "DEBIT";
            }

            return ParsedTransaction.builder()
                    .date(date)
                    .description(description)
                    .amount(amount.abs())
                    .type(type)
                    .build();
        } catch (RuntimeException e) {
            // Malformed line — parsing is best-effort
            return null;
        }
    }

    private List<ParsedTransaction> parseChecksLine(String line, LocalDate periodEnd) {
        List<ParsedTransaction> checks = new ArrayList<>();
        Matcher m = CHECK_ENTRY.matcher(line);
        while (m.find()) {
            try {
                LocalDate date = parseDate(m.group(1), periodEnd);
                BigDecimal amount = parseAmount(m.group(3)).abs();
                if (date == null || amount.signum() == 0) continue;
                checks.add(ParsedTransaction.builder()
                        .date(date)
                        .description("Check #" + m.group(2))
                        .amount(amount)
                        .type("DEBIT")
                        .build());
            } catch (RuntimeException ignored) {
                // skip malformed entry
            }
        }
        return checks;
    }

    /** Returns the section a header line introduces, or null if the line is not a header. */
    private Section detectSection(String line) {
        String upper = line.toUpperCase(Locale.ROOT);
        if (upper.startsWith("TOTAL")) return null;
        if (upper.contains("DAILY BALANCE") || upper.contains("BALANCE SUMMARY")
                || upper.equals("SUMMARY") || upper.contains("ACCOUNT SUMMARY")) {
            return Section.SKIP;
        }
        // Keep headers short so ordinary prose (disclosures etc.) doesn't switch sections
        if (upper.length() > 40) return null;
        if (upper.startsWith("DEPOSITS") || upper.startsWith("CREDITS") || upper.startsWith("OTHER CREDITS")) {
            return Section.CREDITS;
        }
        if (upper.startsWith("CHECKS")) return Section.CHECKS;
        if (upper.startsWith("WITHDRAWALS") || upper.startsWith("FEES") || upper.startsWith("DEBITS")
                || upper.startsWith("OTHER DEBITS") || upper.startsWith("CARD PURCHASES")
                || upper.startsWith("ELECTRONIC WITHDRAWALS")) {
            return Section.DEBITS;
        }
        return null;
    }

    private boolean looksLikeCredit(String description) {
        String upper = description.toUpperCase(Locale.ROOT);
        return CREDIT_KEYWORDS.stream().anyMatch(upper::contains);
    }

    // ── Value parsing ─────────────────────────────────────────────────────

    static BigDecimal parseAmount(String raw) {
        String s = raw.replace("$", "").replace(",", "");
        boolean negative = s.startsWith("-") || s.endsWith("-");
        s = s.replace("-", "");
        BigDecimal value = new BigDecimal(s);
        return negative ? value.negate() : value;
    }

    /**
     * Parses MM/DD/YYYY, MM/DD/YY or MM/DD. For MM/DD the year comes from the statement period
     * end; a month later than the period's end month belongs to the previous year
     * (e.g. a 12/30 row on a statement ending January 15).
     */
    static LocalDate parseDate(String raw, LocalDate periodEnd) {
        String[] parts = raw.split("/");
        int month = Integer.parseInt(parts[0]);
        int day = Integer.parseInt(parts[1]);
        int year;
        if (parts.length == 3) {
            year = Integer.parseInt(parts[2]);
            if (year < 100) year += 2000;
        } else {
            year = periodEnd.getYear();
            if (month > periodEnd.getMonthValue()) year -= 1;
        }
        return LocalDate.of(year, month, day);
    }

    static LocalDate inferPeriodEnd(String text) {
        Matcher period = STATEMENT_PERIOD.matcher(text);
        if (period.find()) {
            try {
                Month month = Month.valueOf(period.group(4).toUpperCase(Locale.ROOT));
                return LocalDate.of(Integer.parseInt(period.group(6)), month,
                        Integer.parseInt(period.group(5)));
            } catch (RuntimeException ignored) {
                // fall through
            }
        }

        LocalDate latest = null;
        Matcher full = FULL_DATE.matcher(text);
        while (full.find()) {
            try {
                LocalDate d = LocalDate.of(Integer.parseInt(full.group(3)),
                        Integer.parseInt(full.group(1)), Integer.parseInt(full.group(2)));
                if (latest == null || d.isAfter(latest)) latest = d;
            } catch (RuntimeException ignored) {
                // not a real date
            }
        }
        return latest != null ? latest : LocalDate.now();
    }
}
