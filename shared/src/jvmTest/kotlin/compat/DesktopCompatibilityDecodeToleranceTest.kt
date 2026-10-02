package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.util.JvmFileSystem
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopCompatibilityDecodeToleranceTest {
    private val board = CompatBoard("may-b", "虹裏", "https://may.2chan.net/b/", "https://may.2chan.net/b/", 0)

    private fun rule(id: String, value: String) = CompatNgRule(
        id = id,
        kind = CompatNgKind.THREAD_WORD,
        scopeKey = "*",
        normalizedValue = value,
        createdAtEpochMillis = 1L
    )

    @Test
    fun valuesWrittenByAnotherBuildDropOnlyTheUnreadablePart() = runBlocking {
        val directory = Files.createTempDirectory("compat-decode").toFile()
        val fs = JvmFileSystem(directory)
        var store = DesktopCompatibilityStore(fs)
        try {
            store.initialize()
            store.upsertBoard(board)
            assertTrue(store.upsertNgRule(rule("keep", "残す")))
            assertTrue(store.upsertNgRule(rule("future", "新しい種類")))
            store.saveCatalogPreference(CompatCatalogPreference(board.key, replyPriorityEnabled = false))
            store.close()

            // Simulate a downgrade: a newer build wrote enum values and fields
            // this build does not know.
            val database = DesktopCompatibilityDatabase(fs)
            val payload = Json.parseToJsonElement(assertNotNull(database.readPayload())).jsonObject
            val rules = payload.getValue("ngRules").jsonArray.map { element ->
                val rule = element.jsonObject
                if (rule["id"] == JsonPrimitive("future")) {
                    JsonObject(rule + ("kind" to JsonPrimitive("THREAD_FUTURE_KIND")))
                } else {
                    rule
                }
            }
            val preferences = payload.getValue("catalogPreferences").jsonArray.map { element ->
                JsonObject(element.jsonObject + ("sort" to JsonPrimitive("RANDOM")) + ("layout" to JsonPrimitive("CARDS")))
            }
            val workspace = JsonObject(
                payload.getValue("workspace").jsonObject + ("selectorPresentation" to JsonPrimitive("SIDE"))
            )
            val tampered = JsonObject(
                payload + mapOf(
                    "ngRules" to JsonArray(rules),
                    "catalogPreferences" to JsonArray(preferences),
                    "workspace" to workspace,
                    "fieldFromNewerBuild" to JsonPrimitive(true)
                )
            )
            database.writePayload(tampered.toString(), 2L)
            database.close()

            store = DesktopCompatibilityStore(fs)
            store.initialize()
            assertEquals(listOf(board.key), store.boards.first().map(CompatBoard::key))
            assertEquals(listOf("keep"), store.ngRules.first().map(CompatNgRule::id))
            val preference = store.loadCatalogPreference(board.key)
            assertEquals(CompatCatalogSort.CATALOG, preference.sort)
            assertEquals(CompatCatalogLayout.GRID, preference.layout)
            assertEquals(false, preference.replyPriorityEnabled)
            assertEquals(SelectorPresentation.ABOVE, store.workspace.first().selectorPresentation)
            assertTrue(unreadableBackups(directory).isEmpty())
        } finally {
            store.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun undecodablePayloadIsBackedUpAndNotDeleted() = runBlocking {
        val directory = Files.createTempDirectory("compat-decode-corrupt").toFile()
        val fs = JvmFileSystem(directory)
        var store = DesktopCompatibilityStore(fs)
        try {
            store.initialize()
            store.upsertBoard(board)
            store.close()
            val corrupted = """{"boards":"not a list","tabs":[]}"""
            DesktopCompatibilityDatabase(fs).apply {
                writePayload(corrupted, 2L)
                close()
            }

            repeat(2) {
                store = DesktopCompatibilityStore(fs)
                store.initialize()
                assertTrue(store.boards.first().isEmpty())
                store.close()
            }

            // One copy for the same payload across launches, byte-for-byte.
            val backups = unreadableBackups(directory)
            assertEquals(1, backups.size)
            assertEquals(corrupted, backups.single().readText())
            // The stored row survives until the next real write.
            DesktopCompatibilityDatabase(fs).apply {
                assertEquals(corrupted, readPayload())
                close()
            }

            store = DesktopCompatibilityStore(fs)
            store.initialize()
            store.upsertBoard(board)
            store.close()
            store = DesktopCompatibilityStore(fs)
            store.initialize()
            assertEquals(listOf(board.key), store.boards.first().map(CompatBoard::key))
            assertEquals(corrupted, unreadableBackups(directory).single().readText())
        } finally {
            store.close()
            // The unreadable payload posted a notice (P4-1); do not leak it to other tests.
            while (com.valoser.futacha.shared.state.SettingsRecoveryNotices.notice.value != null) {
                com.valoser.futacha.shared.state.SettingsRecoveryNotices.acknowledge()
            }
            directory.deleteRecursively()
        }
    }

    private fun unreadableBackups(root: File): List<File> =
        root.walkTopDown()
            .filter { it.isFile && it.name.startsWith("unreadable_compatibility_state-") }
            .toList()
}
