package com.storelense.erp.service;

import com.storelense.erp.domain.entity.ErpSohSnapshot;
import com.storelense.erp.exception.CsvParseException;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class ErpCsvParser {

    private static final String[] REQUIRED_HEADERS = {"EAN", "EXPECTED_QTY", "ZONE_REGION", "STORE_CODE"};

    public record ParseResult(String storeCode, List<ErpSohSnapshot> snapshots) {}

    /**
     * Parses an ERP export CSV. STORE_CODE must be present on every row and consistent
     * throughout the file — it is looked up in erp_store_mapping by the caller.
     *
     * @throws CsvParseException on missing/inconsistent headers or malformed rows
     * @throws IOException       on read failures
     */
    public ParseResult parse(InputStream is) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new CsvParseException("File is empty", 0);
            }

            String[] rawHeaders = headerLine.split(",", -1);
            Map<String, Integer> idx = new HashMap<>();
            for (int i = 0; i < rawHeaders.length; i++) {
                idx.put(rawHeaders[i].trim().toUpperCase(), i);
            }
            for (String required : REQUIRED_HEADERS) {
                if (!idx.containsKey(required)) {
                    throw new CsvParseException("Missing required header: " + required, 1);
                }
            }

            int eanCol       = idx.get("EAN");
            int qtyCol       = idx.get("EXPECTED_QTY");
            int zoneCol      = idx.get("ZONE_REGION");
            int storeCodeCol = idx.get("STORE_CODE");
            int minCols      = Math.max(eanCol, Math.max(qtyCol, Math.max(zoneCol, storeCodeCol))) + 1;

            List<ErpSohSnapshot> snapshots = new ArrayList<>();
            String resolvedStoreCode = null;
            String line;
            int lineNum = 1;

            while ((line = reader.readLine()) != null) {
                lineNum++;
                if (line.isBlank()) continue;

                String[] cols = line.split(",", -1);
                if (cols.length < minCols) {
                    throw new CsvParseException(
                            "Expected at least " + minCols + " columns, got " + cols.length, lineNum);
                }

                String ean = cols[eanCol].trim();
                if (ean.isEmpty()) {
                    throw new CsvParseException("EAN is empty", lineNum);
                }

                int qty;
                try {
                    qty = Integer.parseInt(cols[qtyCol].trim());
                } catch (NumberFormatException e) {
                    throw new CsvParseException(
                            "Invalid EXPECTED_QTY: '" + cols[qtyCol].trim() + "'", lineNum);
                }

                String zone = cols[zoneCol].trim();

                String rowStoreCode = cols[storeCodeCol].trim();
                if (rowStoreCode.isEmpty()) {
                    throw new CsvParseException("STORE_CODE is empty", lineNum);
                }
                if (resolvedStoreCode == null) {
                    resolvedStoreCode = rowStoreCode;
                } else if (!resolvedStoreCode.equals(rowStoreCode)) {
                    throw new CsvParseException(
                            "STORE_CODE must be the same on every row — found '" + rowStoreCode
                            + "' but expected '" + resolvedStoreCode + "'", lineNum);
                }

                snapshots.add(ErpSohSnapshot.builder()
                        .ean(ean)
                        .expectedQty(qty)
                        .zoneRegion(zone.isEmpty() ? null : zone)
                        .build());
            }

            if (resolvedStoreCode == null) {
                throw new CsvParseException("CSV contains no data rows", 1);
            }

            return new ParseResult(resolvedStoreCode, snapshots);
        }
    }
}
