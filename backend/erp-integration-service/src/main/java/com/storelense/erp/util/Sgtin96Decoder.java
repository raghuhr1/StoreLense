package com.storelense.erp.util;

import java.math.BigInteger;

/**
 * Decodes a GS1 SGTIN-96 EPC hex string into an EAN-13 barcode string.
 * Pure static utility — no Spring context required.
 *
 * SGTIN-96 bit layout (96 bits = 24 hex chars, MSB first):
 *   [95-88] header   (8 bits) — must be 0x30
 *   [87-85] filter   (3 bits) — ignored
 *   [84-82] partition (3 bits)
 *   [81-(82-M)] company prefix (M bits, from partition table)
 *   [38+N-1-38] item reference (N bits, where N = 44-M)
 *   [37-0]  serial   (38 bits) — ignored
 *
 * Partition table: M = company prefix bits, L = company prefix digits.
 * Item reference field D = 13-L digits, where the first digit is the GTIN
 * indicator digit (always 0 for EAN-13).
 * EAN-13 = zeroPad(cp,L) + irStr[1..D] + gs1CheckDigit  (total 13 digits).
 */
public final class Sgtin96Decoder {

    private Sgtin96Decoder() {}

    // partition → {prefixBits (M), prefixDigits (L)}
    // itemRefBits = 44 - M;  itemRefDigits D = 13 - L  (includes indicator)
    private static final int[][] PT = {
        {40, 12},   // 0
        {37, 11},   // 1
        {34, 10},   // 2
        {30,  9},   // 3
        {27,  8},   // 4
        {24,  7},   // 5
        {20,  6},   // 6
    };

    /**
     * @param hexEpc 24-character hex string (96-bit EPC, case-insensitive)
     * @return EAN-13 string, or {@code null} if the input is not a valid SGTIN-96
     *         for an EAN-13 (indicator digit ≠ 0 or any parse failure)
     */
    public static String decode(String hexEpc) {
        if (hexEpc == null) return null;
        String hex = hexEpc.strip();
        if (hex.length() != 24) return null;

        BigInteger epc;
        try {
            epc = new BigInteger(hex, 16);
        } catch (NumberFormatException e) {
            return null;
        }

        // Header (bits 95-88) must be 0x30 = SGTIN-96
        if ((epc.shiftRight(88).intValue() & 0xFF) != 0x30) return null;

        // Partition (bits 84-82, 3 bits; bits 87-85 = filter value, skipped)
        int partition = epc.shiftRight(82).intValue() & 0x7;
        if (partition > 6) return null;

        int M = PT[partition][0];   // company prefix bits
        int L = PT[partition][1];   // company prefix digits
        int N = 44 - M;             // item reference bits (serial at bits 37-0)
        int D = 13 - L;             // item reference digits (first digit = indicator)

        // Company prefix: M bits at positions 81..(82-M)
        // After header(8)+filter(3)+partition(3) = 14 bits from MSB.
        // Shift right by (38+N) to bring company prefix to bits M-1..0.
        long cp = epc.shiftRight(38 + N).longValue() & ((1L << M) - 1);

        // Item reference: N bits at positions (37+N)..(38), i.e., shiftRight(38)
        long ir = epc.shiftRight(38).longValue() & ((1L << N) - 1);

        String cpStr = String.format("%0" + L + "d", cp);
        String irStr = String.format("%0" + D + "d", ir);

        // First digit of irStr is the GTIN indicator; must be '0' for EAN-13
        if (irStr.charAt(0) != '0') return null;

        // EAN-13 = company prefix (L) + item ref without indicator (12-L) + check digit
        String body = cpStr + irStr.substring(1);   // 12 digits
        return body + gs1CheckDigit(body);
    }

    /**
     * GS1 check digit: from right, alternating weights 3 and 1 (rightmost = ×3).
     */
    private static int gs1CheckDigit(String digits) {
        int sum = 0;
        int len = digits.length();
        for (int i = 0; i < len; i++) {
            int d = digits.charAt(i) - '0';
            int weight = ((len - 1 - i) % 2 == 0) ? 3 : 1;
            sum += d * weight;
        }
        return (10 - sum % 10) % 10;
    }
}
