package com.storelense.rfid.processing.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.Optional;

/**
 * Decodes GS1 SGTIN-96 EPC hex strings.
 *
 * SGTIN-96 bit layout (96 bits total):
 *   [7:0]   Header     = 0x30 (8 bits)
 *   [10:8]  Filter     (3 bits) — ignored for product lookup
 *   [13:11] Partition  (3 bits) — determines company prefix / item ref digit split
 *   [13+cpBits-1:14]  Company Prefix (variable, see partition table)
 *   [13+cpBits+irBits-1:14+cpBits]  Item Reference (variable)
 *   [95:58] Serial     (38 bits)
 */
@Slf4j
@Service
public class EpcDecoderService {

    public record DecodedEpc(
            String companyPrefix,
            String itemReference,
            String serialNumber,
            String gtin14,
            String ean13
    ) {}

    /**
     * Partition table: index = partition value (0–6).
     * Each entry: { companyPrefixBits, itemRefBits, companyPrefixDigits, itemRefDigits }
     */
    private static final int[][] PARTITION_TABLE = {
            {40,  4, 12, 1},
            {37,  7, 11, 2},
            {34, 10, 10, 3},
            {30, 14,  9, 4},
            {27, 17,  8, 5},
            {24, 20,  7, 6},
            {20, 24,  6, 7}
    };

    public Optional<DecodedEpc> decode(String hexEpc) {
        if (hexEpc == null || hexEpc.length() != 24) {
            return Optional.empty();
        }

        try {
            // Parse as 96-bit unsigned big-endian integer
            BigInteger bits = new BigInteger(hexEpc, 16);

            // Header: top 8 bits
            int header = bits.shiftRight(88).and(BigInteger.valueOf(0xFF)).intValue();
            if (header != 0x30) {
                log.trace("Non-SGTIN-96 header 0x{} for EPC {}", Integer.toHexString(header), hexEpc);
                return Optional.empty();
            }

            // Filter: bits [87:85] — not needed for lookup
            // Partition: bits [84:82]
            int partition = bits.shiftRight(82).and(BigInteger.valueOf(0x7)).intValue();
            if (partition > 6) {
                return Optional.empty();
            }

            int[] p          = PARTITION_TABLE[partition];
            int cpBits       = p[0];
            int irBits       = p[1];
            int cpDigits     = p[2];
            int irDigits     = p[3];

            // Company prefix: bits [81 : 82-cpBits]  starting at bit position 81 downward
            // Bit positions counted from MSB=95 down to LSB=0
            // After header(8)+filter(3)+partition(3) = 14 bits consumed, next cpBits are company prefix
            int cpStart = 82 - cpBits;
            BigInteger cpMask = BigInteger.ONE.shiftLeft(cpBits).subtract(BigInteger.ONE);
            long companyPrefixVal = bits.shiftRight(cpStart).and(cpMask).longValue();

            // Item reference: next irBits
            int irStart = cpStart - irBits;
            BigInteger irMask = BigInteger.ONE.shiftLeft(irBits).subtract(BigInteger.ONE);
            long itemRefVal = bits.shiftRight(irStart).and(irMask).longValue();

            // Serial: bottom 38 bits
            BigInteger serialMask = BigInteger.ONE.shiftLeft(38).subtract(BigInteger.ONE);
            long serialVal = bits.and(serialMask).longValue();

            String companyPrefix = String.format("%0" + cpDigits + "d", companyPrefixVal);
            String itemRef       = String.format("%0" + irDigits + "d", itemRefVal);
            String serial        = Long.toString(serialVal);

            // Company Prefix + Item Reference together = 13 digits per the GS1 partition
            // table above (gtin14 below preserves the original interpretation, kept only
            // as a fallback for products seeded with the full 14-digit GTIN as barcode).
            String gtinBody = companyPrefix + itemRef; // 13 digits
            String gtin14   = gtinBody + gs1CheckDigit(gtinBody);

            // Retail EAN-13: per the EPC TDS spec, the item reference field's OWN leading
            // digit is the GTIN indicator digit, not part of the retail barcode body. Drop
            // it, then compute a fresh check digit over the resulting 12-digit body — this
            // is a *different* check digit than gtin14's, since removing one digit flips
            // which positions carry weight 3 vs 1.
            String ean13Body = companyPrefix + itemRef.substring(1); // 12 digits
            String ean13     = ean13Body + gs1CheckDigit(ean13Body);

            return Optional.of(new DecodedEpc(companyPrefix, itemRef, serial, gtin14, ean13));

        } catch (Exception e) {
            log.debug("EPC decode failed for {}: {}", hexEpc, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * GS1 standard check digit: weighted sum mod 10, complement to 10.
     * The rightmost data digit always carries weight 3, alternating leftwards —
     * so the position parity that carries weight 3 depends on the input length,
     * not a hardcoded index parity.
     */
    static char gs1CheckDigit(String digits) {
        int n = digits.length();
        int sum = 0;
        for (int i = 0; i < n; i++) {
            int d = digits.charAt(i) - '0';
            boolean weightThree = (n - 1 - i) % 2 == 0;
            sum += weightThree ? d * 3 : d;
        }
        int check = (10 - (sum % 10)) % 10;
        return (char) ('0' + check);
    }
}
