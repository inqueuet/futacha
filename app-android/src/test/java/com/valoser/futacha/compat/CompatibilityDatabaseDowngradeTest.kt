package com.valoser.futacha.compat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompatibilityDatabaseDowngradeTest {
    private fun column(name: String, notNull: Boolean = true, hasDefault: Boolean = false, primaryKey: Boolean = false) =
        CompatSchemaColumn(name, notNull, hasDefault, primaryKey)

    private val expected = mapOf(
        "compat_tab" to listOf(column("tab_key", primaryKey = true), column("title")),
        "compat_metadata" to listOf(column("key", primaryKey = true), column("value"))
    )

    @Test
    fun additiveNewerSchemaIsAccepted() {
        val actual = mapOf(
            "compat_tab" to listOf(
                column("tab_key", primaryKey = true),
                column("title"),
                column("future_flag", hasDefault = true),
                column("future_note", notNull = false)
            ),
            "COMPAT_METADATA" to listOf(column("KEY", primaryKey = true), column("value")),
            "compat_future_table" to listOf(column("id", primaryKey = true), column("required"))
        )
        assertEquals(emptyList<String>(), compatDowngradeIncompatibilities(expected, actual))
    }

    @Test
    fun removedTablesOrColumnsAndRequiredNewColumnsAreRejected() {
        val actual = mapOf(
            "compat_tab" to listOf(column("tab_key", primaryKey = true), column("required_without_default"))
        )
        val reasons = compatDowngradeIncompatibilities(expected, actual)
        assertTrue(reasons.toString(), "missing column compat_tab.title" in reasons)
        assertTrue(reasons.toString(), "required column compat_tab.required_without_default" in reasons)
        assertTrue(reasons.toString(), "missing table compat_metadata" in reasons)
    }
}
