package com.storelense.epcencoder.rfid

import java.math.BigInteger
import kotlin.random.Random

/*
 * Encodes an EAN-13 into a GS1 SGTIN-96 EPC hex string, using the same bit
 * layout as the backend's Sgtin96Decoder (erp-integration-service). Partition
 * is fixed to match this org's registered GS1 company prefix length: derived
 * from a live sample tag (EAN 8909230108581 -> EPC ...FC6B80A9A974876EB8B),
 * which decodes to partition 5 (7-digit company prefix / 24 prefix bits,
 * 20 item-reference bits). If the org's GS1 prefix length ever changes,
 * update PARTITION below to match.
 *
 * SGTIN-96 bit layout (96 bits = 24 hex chars, MSB first):
 *   [95-88] header    (8 bits)  = 0x30
 *   [87-85] filter    (3 bits)  = 1 (POS trade item)
 *   [84-82] partition (3 bits)  = PARTITION
 *   [81-(82-M)] company prefix (M bits)
 *   [38+N-1-38] item reference (N bits)
 *   [37-0]  serial    (38 bits) = random
 */
object Sgtin96Encoder {

    private const val HEADER: Long = 0x30
    private const val FILTER: Long = 1

    // partition -> (prefixBits M, prefixDigits L); itemRefBits N = 44-M, itemRefDigits D = 13-L
    private val PARTITION_TABLE = mapOf(
        0 to Pair(40, 12),
        1 to Pair(37, 11),
        2 to Pair(34, 10),
        3 to Pair(30, 9),
        4 to Pair(27, 8),
        5 to Pair(24, 7),
        6 to Pair(20, 6),
    )

    private const val PARTITION = 5

    /**
     * @param ean13 a 13-digit EAN/GTIN string (valid GS1 check digit)
     * @return 24-char uppercase hex EPC, or null if [ean13] isn't a well-formed EAN-13
     *         for this org's company-prefix length.
     */
    fun encode(ean13: String, serial: Long = randomSerial()): String? {
        val ean = ean13.trim()
        if (ean.length != 13 || !ean.all { it.isDigit() }) return null

        val (m, l) = PARTITION_TABLE.getValue(PARTITION)
        val n = 44 - m
        val d = 13 - l

        val body = ean.substring(0, 12) // 12 digits, excludes check digit
        val cpStr = body.substring(0, l)
        val irBody = body.substring(l) // 12-l digits
        val irStr = "0" + irBody // prepend GTIN indicator digit (always 0 for EAN-13)
        if (irStr.length != d) return null

        val cp = cpStr.toLong()
        val ir = irStr.toLong()
        val serialMasked = serial and ((1L shl 38) - 1)

        var epc = BigInteger.valueOf(HEADER).shiftLeft(88)
        epc = epc.or(BigInteger.valueOf(FILTER).shiftLeft(85))
        epc = epc.or(BigInteger.valueOf(PARTITION.toLong()).shiftLeft(82))
        epc = epc.or(BigInteger.valueOf(cp).shiftLeft(38 + n))
        epc = epc.or(BigInteger.valueOf(ir).shiftLeft(38))
        epc = epc.or(BigInteger.valueOf(serialMasked))

        return epc.toString(16).uppercase().padStart(24, '0')
    }

    private fun randomSerial(): Long = Random.nextLong(0, 1L shl 38)
}
