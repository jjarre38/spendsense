package com.spendsense.api.parser;

import java.util.List;

/**
 * Extension point for bank-specific PDF parsers.
 * To add a new bank: implement this interface and register it in PdfParserService.
 */
public interface BankParser {

    /**
     * Parse raw PDF text extracted by PDFBox into a list of transactions.
     *
     * @param rawText full text of the PDF as extracted by PDFTextStripper
     * @return ordered list of parsed transactions
     */
    List<ParsedTransaction> parse(String rawText);
}
