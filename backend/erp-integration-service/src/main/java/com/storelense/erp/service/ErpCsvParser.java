package com.storelense.erp.service;

import com.storelense.erp.domain.entity.ErpImportBatch;
import com.storelense.erp.domain.entity.ErpSohSnapshot;
import com.storelense.erp.exception.CsvParseException;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class ErpCsvParser {

    private static final String[] REQUIRED_HEADERS = {"EAN", "EXPECTED_QTY", "ZONE_REGION"};

    /**
     * Parses an ERP export CSV and maps every data row to an {@link ErpSohSnapshot}.
     * The caller owns the InputStream lifecycle (open / close).
     *
     * @throws CsvParseException on missing headers or malformed rows
     * @throws IOException       on read failures
     */
    public List<ErpSohSnapshot> parse(InputStream is, ErpImportBatch batch) throws IOException {
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

            int eanCol  = idx.get("EAN");
            int qtyCol  = idx.get("EXPECTED_QTY");
            int zoneCol = idx.get("ZONE_REGION");
            int minCols = Math.max(eanCol, Math.max(qtyCol, zoneCol)) + 1;

            List<ErpSohSnapshot> snapshots = new ArrayList<>();
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

                snapshots.add(ErpSohSnapshot.builder()
                        .batch(batch)
                        .ean(ean)
                        .expectedQty(qty)
                        .zoneRegion(zone.isEmpty() ? null : zone)
                        .build());
            }

            return snapshots;
        }
    }
}
