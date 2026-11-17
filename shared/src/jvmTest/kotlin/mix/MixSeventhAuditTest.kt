package com.valoser.futacha.shared.mix

import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.util.JvmFileSystem
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.nio.file.Files
import kotlin.test.*

/** Regression tests for restored definitions and compatibility MIX navigation. */
class MixSeventhAuditTest {
    private suspend fun <T> withStore(block: suspend (DesktopCompatibilityStore) -> T): T {
        val root = Files.createTempDirectory("mix-seventh-audit").toFile()
        val store = DesktopCompatibilityStore(JvmFileSystem(root))
        try { store.initialize(); return block(store) } finally { store.close(); root.deleteRecursively() }
    }
    private fun raw(definitions: List<MixDefinition>, version: Int = 1): String {
        val json = Json.encodeToString(ListSerializer(MixDefinition.serializer()), definitions)
        return "{\"version\":$version,\"mixes\":$json}"
    }
    private suspend fun backup(definitions: List<MixDefinition>) = withStore { store ->
        store.savePreference(MIX_DEFINITIONS_KEY, raw(definitions))
        store.exportSettingsBackup()
    }

    @Test fun restoredOverLimitDefinitionIsReadOnlyAndOnlySafeDefinitionsReachFetching() = runBlocking {
        val boards = (1..11).map { BoardSummary("board$it", "Board $it", "", "https://may.2chan.net/board$it/", "") }
        val def = MixDefinition("over-limit", "Restored", boards.map { it.url }, createdAt = 1)
        val safe = def.copy(id = "safe", name = "Safe", members = listOf(boards.first().url))
        val payload = backup(listOf(def, safe))
        withStore { restored ->
            restored.importSettingsBackup(payload, restoreUserSettings = true, restoreNgRules = false)
            val before = restored.loadPreference(MIX_DEFINITIONS_KEY)
            val loaded = assertIs<MixLoadResult.ReadOnly>(MixStore(restored).load()).mixes.single()
            assertEquals(safe, loaded)
            assertIs<MixWriteResult.Refused>(MixStore(restored).update(safe.id) { it.copy(name = "change") })
            assertEquals(before, restored.loadPreference(MIX_DEFINITIONS_KEY))
            val resolved = resolveMixMembers(loaded, boards, false, { false }, { false })
            val calls = mutableListOf<String>()
            val fetcher = MixCatalogFetcher(fetchPage = { board, _ ->
                calls += board.url
                CatalogPageContent(emptyList())
            }, loadPause = {}, nowMillis = { 1_000 })
            fetcher.fetch(resolved, 1)
            assertEquals(listOf(boards.first().url), calls)
        }
    }

    @Test fun restoredDuplicateIdsAreUnopenableAndCannotOverwriteEitherDefinition() = runBlocking {
        val first = MixDefinition("duplicate", "First", listOf("https://may.2chan.net/b/"), createdAt = 1)
        val second = first.copy(name = "Second", members = listOf("https://img.2chan.net/b/"), createdAt = 2)
        val payload = backup(listOf(first, second))
        withStore { restored ->
            restored.importSettingsBackup(payload, restoreUserSettings = true, restoreNgRules = false)
            val mixStore = MixStore(restored)
            val before = restored.loadPreference(MIX_DEFINITIONS_KEY)
            assertTrue(assertIs<MixLoadResult.ReadOnly>(mixStore.load()).mixes.isEmpty())
            assertIs<MixWriteResult.Refused>(mixStore.update("duplicate") { it.copy(name = "Edited") })
            assertIs<MixWriteResult.Refused>(mixStore.delete("duplicate"))
            assertIs<MixWriteResult.Refused>(mixStore.move("duplicate", 0))
            assertEquals(before, restored.loadPreference(MIX_DEFINITIONS_KEY))
        }
    }
    @Test fun invalidNamesIdsAndEmptyMembersAreNotExposedToTheUi() {
        val valid = MixDefinition("valid", "Valid", listOf("https://may.2chan.net/b/"), createdAt = 1)
        for (invalid in listOf(valid.copy(id = ""), valid.copy(id = "bad\n"), valid.copy(name = " "),
            valid.copy(name = "x".repeat(51)), valid.copy(name = "bad\nname"), valid.copy(members = emptyList()))) {
            assertTrue(assertIs<MixLoadResult.ReadOnly>(decodeMixDefinitions(raw(listOf(invalid)))).mixes.isEmpty())
        }
        for (version in listOf(1, 2)) {
            val tooMany = (1..11).map { valid.copy(id = "mix-$it") }
            val decoded = assertIs<MixLoadResult.ReadOnly>(decodeMixDefinitions(raw(tooMany, version)))
            assertEquals(10, decoded.mixes.size)
            assertEquals(10, decoded.mixes.map { it.id }.distinct().size)
        }
    }

    @Test fun unknownAndUnavailableMemberUrlsRemainValidAndAreNotSilentlyRemoved() {
        val members = listOf("https://unknown.invalid/missing/", "https://nijiurachan.net/b/adult/", "https://may.2chan.net/b/")
        val definition = MixDefinition("valid", "Valid", members, createdAt = 1)
        val loaded = assertIs<MixLoadResult.Ok>(decodeMixDefinitions(raw(listOf(definition)))).mixes.single()
        assertEquals(members, loaded.members)
        assertEquals(3, resolveMixMembers(loaded, emptyList(), false, { false }, { false }).skipped.size)
    }

    @Test fun normalNavigationClearsTheMixButExplicitRowOpeningAndBackKeepIt() {
        fun after(event: CompatibilityEvent, keep: Boolean = false) =
            com.valoser.futacha.shared.ui.compat.compatMixIdAfterNavigation("mix", event, keep)
        assertNull(after(CompatibilityEvent.OpenCatalog("board")))
        assertNull(after(CompatibilityEvent.OpenHost(CompatHost.Main)))
        assertNull(after(CompatibilityEvent.OpenThread("tab", CompatThreadOrigin.DEEP_LINK)))
        assertNull(after(CompatibilityEvent.OpenThread("tab", CompatThreadOrigin.MAIN)))
        assertNull(after(CompatibilityEvent.OpenThread("tab", CompatThreadOrigin.CATALOG)))
        assertEquals("mix", after(CompatibilityEvent.OpenThread("tab", CompatThreadOrigin.MAIN), keep = true))
        assertEquals("mix", after(CompatibilityEvent.Back))
    }

}
