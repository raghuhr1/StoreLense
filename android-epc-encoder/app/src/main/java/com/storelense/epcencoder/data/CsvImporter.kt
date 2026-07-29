package com.storelense.epcencoder.data

import android.content.Context
import android.net.Uri
import java.io.BufferedReader
import java.io.InputStreamReader

data class ImportResult(
    val rows: List<EpcRow>,
    val errors: List<String>
)

private val EPC_PATTERN = Regex("^[0-9A-Fa-f]{16,64}$")

object CsvImporter {

    /** Expects header row with (case-insensitive) columns: Sr.No, EPC, EAN-13, dept, style, description, color, size */
    fun import(context: Context, uri: Uri): ImportResult {
        val errors = mutableListOf<String>()
        val rows = mutableListOf<EpcRow>()
        val seenEpcs = HashSet<String>()

        context.contentResolver.openInputStream(uri)?.use { input ->
            BufferedReader(InputStreamReader(input)).use { reader ->
                val headerLine = reader.readLine() ?: run {
                    errors.add("File is empty")
                    return ImportResult(emptyList(), errors)
                }
                val headers = headerLine.split(",").map { it.trim().lowercase() }
                val idx = mapOf(
                    "srno" to headers.indexOfFirst { it.replace(".", "").replace(" ", "") == "srno" },
                    "epc" to headers.indexOf("epc"),
                    "ean" to headers.indexOfFirst { it.startsWith("ean") },
                    "dept" to headers.indexOf("dept"),
                    "style" to headers.indexOf("style"),
                    "description" to headers.indexOf("description"),
                    "color" to headers.indexOf("color"),
                    "size" to headers.indexOf("size")
                )
                if (idx["epc"] == -1 || idx["ean"] == -1) {
                    errors.add("CSV must have EPC and EAN-13 columns")
                    return ImportResult(emptyList(), errors)
                }

                var lineNo = 1
                reader.forEachLine { line ->
                    lineNo++
                    if (line.isBlank()) return@forEachLine
                    val cols = line.split(",").map { it.trim() }
                    fun col(key: String): String {
                        val i = idx[key] ?: -1
                        return if (i in cols.indices) cols[i] else ""
                    }

                    val epc = col("epc").uppercase()
                    val ean = col("ean")

                    if (!EPC_PATTERN.matches(epc)) {
                        errors.add("Line $lineNo: invalid EPC format '$epc'")
                        return@forEachLine
                    }
                    if (ean.isBlank()) {
                        errors.add("Line $lineNo: blank EAN")
                        return@forEachLine
                    }
                    if (!seenEpcs.add(epc)) {
                        errors.add("Line $lineNo: duplicate EPC '$epc'")
                        return@forEachLine
                    }

                    rows.add(
                        EpcRow(
                            srNo = col("srno"),
                            targetEpc = epc,
                            ean = ean,
                            dept = col("dept"),
                            style = col("style"),
                            description = col("description"),
                            color = col("color"),
                            size = col("size")
                        )
                    )
                }
            }
        } ?: errors.add("Could not open file")

        return ImportResult(rows, errors)
    }
}
