package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.CatalogDisplayStyle
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.service.DEFAULT_MANUAL_SAVE_ROOT
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import com.valoser.futacha.shared.util.SaveDirectorySelection
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class AppStateSettingsSupportTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val selfPostSerializer = MapSerializer(String.serializer(), ListSerializer(String.serializer()))

    @Test
    fun decodeCatalogDisplayStyleValue_fallsBackToGrid() {
        assertEquals(CatalogDisplayStyle.List, decodeCatalogDisplayStyleValue("List"))
        assertEquals(CatalogDisplayStyle.Grid, decodeCatalogDisplayStyleValue("unknown"))
        assertEquals(CatalogDisplayStyle.Grid, decodeCatalogDisplayStyleValue(null))
    }

    @Test
    fun decodeCatalogGridColumnsValue_clampsToSupportedRange() {
        assertEquals(5, decodeCatalogGridColumnsValue(null))
        assertEquals(2, decodeCatalogGridColumnsValue("1"))
        assertEquals(8, decodeCatalogGridColumnsValue("99"))
        assertEquals(6, decodeCatalogGridColumnsValue("6"))
    }

    @Test
    fun catalogModeMap_helpers_decodeKnownValues_and_ignoreUnknownOnes() {
        val decoded = decodeCatalogModeMapValue("""{"b":"New","img":"WatchWords","x":"Unknown"}""")

        assertEquals(
            mapOf(
                "b" to CatalogMode.New,
                "img" to CatalogMode.WatchWords
            ),
            decoded
        )
        assertEquals(
            mapOf("b" to "New"),
            encodeCatalogModeMapValue(mapOf("b" to CatalogMode.New))
        )
    }

    @Test
    fun selfPostIdentifier_helpers_decodeAndAggregateDistinctValues() {
        val decoded = decodeSelfPostIdentifierMapValue(
            raw = """{"b::123":[" ID:abc ","id:ABC",""],"img::456":["ID:def"]}""",
            json = json,
            serializer = selfPostSerializer
        )

        val aggregated = aggregateSelfPostIdentifiers(decoded, maxEntries = 10)

        assertEquals(listOf("ID:abc", "ID:def"), aggregated)
    }

    @Test
    fun selfPostIdentifier_helpers_mergeLegacyAndScopedEntries() {
        val merged = mergeSelfPostIdentifierMap(
            currentMap = mapOf(
                "123" to listOf("ID:legacy"),
                "b::123" to listOf("ID:current")
            ),
            threadId = "123",
            identifier = "id:CURRENT",
            boardId = "b",
            maxEntries = 10
        )

        assertEquals(
            listOf("ID:current", "ID:legacy"),
            merged["b::123"]
        )
    }

    @Test
    fun selfPostIdentifier_helpers_removeScopedAndLegacyKeys() {
        val current = mapOf(
            "123" to listOf("ID:legacy"),
            "b::123" to listOf("ID:b"),
            "img::123" to listOf("ID:img"),
            "b::999" to listOf("ID:other")
        )

        assertEquals(
            mapOf("b::999" to listOf("ID:other")),
            removeSelfPostIdentifiersFromMap(current, threadId = "123", boardId = null)
        )
        assertEquals(
            mapOf(
                "123" to listOf("ID:legacy"),
                "img::123" to listOf("ID:img"),
                "b::999" to listOf("ID:other")
            ),
            removeSelfPostIdentifiersFromMap(current, threadId = "123", boardId = "b")
        )
    }

    @Test
    fun buildSelfPostStorageKey_scopesByBoardWhenPresent() {
        assertEquals("b::123", buildSelfPostStorageKey("123", "b"))
        assertEquals("123", buildSelfPostStorageKey("123", null))
    }

    @Test
    fun sanitizeManualSaveDirectoryValue_normalizesBlankAndCurrentDirectoryValues() {
        assertEquals(DEFAULT_MANUAL_SAVE_ROOT, sanitizeManualSaveDirectoryValue(null))
        assertEquals(DEFAULT_MANUAL_SAVE_ROOT, sanitizeManualSaveDirectoryValue("  "))
        assertEquals(DEFAULT_MANUAL_SAVE_ROOT, sanitizeManualSaveDirectoryValue("./manual_saved_threads"))
        assertEquals("Documents/futacha", sanitizeManualSaveDirectoryValue("./Documents/futacha"))
        assertEquals("/storage/emulated/0/futacha", sanitizeManualSaveDirectoryValue(" /storage/emulated/0/futacha "))
    }

    @Test
    fun decodeAttachmentPickerPreferenceValue_defaultsToMediaForUnknownValues() {
        assertEquals(
            AttachmentPickerPreference.ALWAYS_ASK,
            decodeAttachmentPickerPreferenceValue("ALWAYS_ASK")
        )
        assertEquals(
            AttachmentPickerPreference.MEDIA,
            decodeAttachmentPickerPreferenceValue("unknown")
        )
        assertEquals(
            AttachmentPickerPreference.MEDIA,
            decodeAttachmentPickerPreferenceValue(null)
        )
    }

    @Test
    fun decodeSaveDirectorySelectionValue_defaultsToManualInputForUnknownValues() {
        assertEquals(
            SaveDirectorySelection.PICKER,
            decodeSaveDirectorySelectionValue("PICKER")
        )
        assertEquals(
            SaveDirectorySelection.MANUAL_INPUT,
            decodeSaveDirectorySelectionValue("invalid")
        )
        assertEquals(
            SaveDirectorySelection.MANUAL_INPUT,
            decodeSaveDirectorySelectionValue(null)
        )
    }
}
