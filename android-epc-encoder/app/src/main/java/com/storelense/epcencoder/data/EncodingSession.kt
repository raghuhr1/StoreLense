package com.storelense.epcencoder.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Holds the current batch of rows in memory; checkpoints to a JSON file so an app restart resumes. */
object EncodingSession {

    var rows: MutableList<EpcRow> = mutableListOf()
        private set

    private lateinit var checkpointFile: File

    fun init(context: Context) {
        checkpointFile = File(context.filesDir, "epc_session.json")
    }

    fun load(newRows: List<EpcRow>) {
        rows = newRows.toMutableList()
        save()
    }

    fun hasSavedSession(): Boolean = checkpointFile.exists()

    fun restore(): Boolean {
        if (!checkpointFile.exists()) return false
        return try {
            val arr = JSONArray(checkpointFile.readText())
            val restored = mutableListOf<EpcRow>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                restored.add(
                    EpcRow(
                        srNo = o.optString("srNo"),
                        targetEpc = o.optString("targetEpc"),
                        ean = o.optString("ean"),
                        dept = o.optString("dept"),
                        style = o.optString("style"),
                        description = o.optString("description"),
                        color = o.optString("color"),
                        size = o.optString("size"),
                        status = RowStatus.valueOf(o.optString("status", "PENDING")),
                        productId = o.optString("productId").ifBlank { null },
                        errorMessage = o.optString("errorMessage").ifBlank { null }
                    )
                )
            }
            rows = restored
            true
        } catch (e: Exception) {
            false
        }
    }

    fun nextPending(): EpcRow? = rows.firstOrNull { it.status == RowStatus.PENDING || it.status == RowStatus.FAILED }

    fun counts(): Map<RowStatus, Int> = rows.groupingBy { it.status }.eachCount()

    fun updateRow(row: EpcRow) {
        val i = rows.indexOfFirst { it.targetEpc == row.targetEpc }
        if (i >= 0) rows[i] = row
        save()
    }

    fun clear() {
        rows = mutableListOf()
        if (checkpointFile.exists()) checkpointFile.delete()
    }

    fun exportCsv(): String {
        val sb = StringBuilder("SrNo,EAN,TargetEPC,Status,ProductId,Error\n")
        rows.forEach {
            sb.append("${it.srNo},${it.ean},${it.targetEpc},${it.status},${it.productId ?: ""},${it.errorMessage ?: ""}\n")
        }
        return sb.toString()
    }

    private fun save() {
        val arr = JSONArray()
        rows.forEach {
            val o = JSONObject()
            o.put("srNo", it.srNo)
            o.put("targetEpc", it.targetEpc)
            o.put("ean", it.ean)
            o.put("dept", it.dept)
            o.put("style", it.style)
            o.put("description", it.description)
            o.put("color", it.color)
            o.put("size", it.size)
            o.put("status", it.status.name)
            o.put("productId", it.productId ?: "")
            o.put("errorMessage", it.errorMessage ?: "")
            arr.put(o)
        }
        checkpointFile.writeText(arr.toString())
    }
}
