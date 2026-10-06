package com.dgraciano.breathe.data.model

import java.io.StringWriter
import org.junit.Assert.*
import org.junit.Test

class HistoryCsvTest {
    @Test fun `empty export has explicit units and no invented records`() {
        val writer = StringWriter()
        writeHistoryCsv(emptyList(), writer)
        assertEquals("timestamp_epoch_ms,app_name,package_name,choice,reason,estimated_skipped_minutes\r\n", writer.toString())
    }
    @Test fun `unicode quotes newlines and optional reasons survive CSV escaping`() {
        val writer = StringWriter()
        writeHistoryCsv(listOf(InterventionEvent(packageName = "com.demo", appName = "消息, \"Hello\"\nWorld", timestamp = 123,
            outcome = "OPENED", reason = null, minutesSaved = 0)), writer)
        assertTrue(writer.toString().endsWith("\"123\",\"消息, \"\"Hello\"\"\nWorld\",\"com.demo\",\"OPENED\",\"\",\"0\"\r\n"))
    }
    @Test fun `spreadsheet formula-like app names export as literal text`() {
        for (name in listOf("=1+2", "+cmd", "-cmd", "@SUM(1)", "  =1", "\t=1", "\r=1")) {
            val writer = StringWriter()
            writeHistoryCsv(listOf(InterventionEvent(packageName = "com.demo", appName = name, timestamp = 1, outcome = "DECLINED", minutesSaved = 20)), writer)
            assertTrue(writer.toString().contains("\"'$name\""))
        }
    }
}
