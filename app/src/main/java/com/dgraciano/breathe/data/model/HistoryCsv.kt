package com.dgraciano.breathe.data.model

import java.io.Writer

/** UTF-8 CSV with explicit units; protect text fields from spreadsheet formulas. */
fun writeHistoryCsv(events: Iterable<InterventionEvent>, writer: Writer) {
    writer.write("timestamp_epoch_ms,app_name,package_name,choice,reason,estimated_skipped_minutes\r\n")
    for (event in events) {
        val fields = listOf(event.timestamp.toString(), csvText(event.appName), csvText(event.packageName),
            csvText(event.outcome), csvText(event.reason.orEmpty()), event.minutesSaved.toString())
        writer.write(fields.joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" })
        writer.write("\r\n")
    }
}

private fun csvText(text: String): String =
    if (text.firstOrNull() in listOf('\t', '\r', '\n') || text.trimStart().firstOrNull() in listOf('=', '+', '-', '@')) "'$text" else text
