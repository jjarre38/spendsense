package com.spendsense.api.service;

import com.spendsense.api.entity.Statement;
import com.spendsense.api.parser.BankParser;
import com.spendsense.api.parser.ParsedTransaction;
import com.spendsense.api.parser.RegionsBankParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Selects the correct BankParser for a given BankType, extracts PDF text,
 * and delegates to the parser. Adding a new bank requires only:
 *   1. Implement BankParser
 *   2. Add a new entry to the PARSERS registry below
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PdfParserService {

    private final RegionsBankParser regionsBankParser;

    /** Registry map: BankType → parser implementation */
    private Map<Statement.BankType, BankParser> buildRegistry() {
        return Map.of(
                Statement.BankType.REGIONS, regionsBankParser
        );
    }

    /**
     * Extracts raw text from the uploaded PDF and parses it using the bank-specific parser.
     *
     * @param file     the uploaded PDF multipart file
     * @param bankType which bank's parser to use
     * @return list of parsed transactions (may be empty if no matching lines found)
     * @throws IOException if PDFBox cannot read the file
     */
    public List<ParsedTransaction> parse(MultipartFile file, Statement.BankType bankType) throws IOException {
        String rawText = extractText(file);
        log.debug("Extracted {} characters from PDF for bank {}", rawText.length(), bankType);

        BankParser parser = buildRegistry().get(bankType);
        if (parser == null) {
            throw new IllegalArgumentException("No parser registered for bank type: " + bankType);
        }

        List<ParsedTransaction> transactions = parser.parse(rawText);
        log.info("Parsed {} transactions from {} statement", transactions.size(), bankType);
        return transactions;
    }

    private String extractText(MultipartFile file) throws IOException {
        try (PDDocument doc = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(doc);
        }
    }
}
