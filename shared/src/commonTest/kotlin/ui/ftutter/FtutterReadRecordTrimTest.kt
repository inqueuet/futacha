package com.valoser.futacha.shared.ui.ftutter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** L129: read records are cut by characters with the open tabs and protected threads kept first, not to 200 entries. */
class FtutterReadRecordTrimTest {
    private fun url(n: Int) = "https://may.2chan.net/b/res/${1442000000 + n}.htm"

    @Test fun moreThan200RecordsFitAndTheTabsAtTheEndAreKept() {
        // Newest first; the open tabs are the oldest records.
        val counts = (0 until 500).associate { url(it) to it }
        val tabs = setOf(url(498), url(499))
        val trimmed = ftutterTrimReadCounts(counts, tabs)
        assertTrue(trimmed.size > 200, "Kept ${trimmed.size}")
        assertTrue(encodeFtutterReadCounts(trimmed).length <= FTUTTER_READ_RECORD_MAX_CHARS)
        assertTrue(tabs.all { it in trimmed }, "The open tabs keep their counts")
        assertEquals(counts.keys.filter { it in trimmed }, trimmed.keys.toList(), "The order is unchanged")
        assertTrue(url(0) in trimmed)
    }

    @Test fun anImportedRecordSetLosesAtMostOneEntryOnTheNextWrite() {
        val imported = (0 until 400).associate { url(it) to it }
        val stored = ftutterTrimReadCounts(imported, imported.keys)
        val next = linkedMapOf(url(1000) to 1).apply { putAll(stored) }
        val written = ftutterTrimReadCounts(next, emptySet())
        assertTrue(written.size >= stored.size, "${stored.size} -> ${written.size}")
    }

    @Test fun boundariesKeepTheOpenTabsFirst() {
        var raw: String? = null
        val tab = url(0)
        raw = ftutterReadBoundariesAfter(raw, tab, 3, "123")
        repeat(400) { raw = ftutterReadBoundariesAfter(raw, url(it + 1), 2, "9999999999999999", keepFirst = setOf(tab)) ?: raw }
        assertTrue(raw!!.length <= FTUTTER_READ_RECORD_MAX_CHARS)
        assertEquals("123", ftutterReadBoundaryId(raw, tab, 3), "The open tab's boundary survives")
    }
}
