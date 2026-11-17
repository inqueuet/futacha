package com.valoser.futacha.shared.mix

import com.valoser.futacha.shared.compat.DesktopCompatibilityStore
import com.valoser.futacha.shared.util.JvmFileSystem
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.*

class MixStoreTest {
    private fun withStore(block: suspend (MixStore, DesktopCompatibilityStore) -> Unit) = runBlocking {
        val root = Files.createTempDirectory("futacha-mix").toFile()
        try {
            val s = DesktopCompatibilityStore(JvmFileSystem(root)).also { it.initialize() }
            block(MixStore(s), s)
        } finally { root.deleteRecursively() }
    }
    private val may = "https://may.2chan.net/b/"

    @Test fun automaticCreationWaitsForMemberConsentAndCancelDoesNotSave() = withStore { mix, store ->
        val members = listOf(may, "https://nijiurachan.net/b/aimg/")
        var asked = false
        assertEquals(com.valoser.futacha.shared.ui.board.MixOpenResult.Cancelled, com.valoser.futacha.shared.ui.board.firstMixIdOrCreate(store, members, 1) {
            assertEquals(members, it)
            asked = true
            assertTrue((mix.load() as MixLoadResult.Ok).mixes.isEmpty())
            false
        })
        assertTrue(asked)
        assertTrue((mix.load() as MixLoadResult.Ok).mixes.isEmpty())
        val id = (com.valoser.futacha.shared.ui.board.firstMixIdOrCreate(store, members, 2) { true } as com.valoser.futacha.shared.ui.board.MixOpenResult.Opened).id
        assertNotNull(id)
        assertEquals(id, (mix.load() as MixLoadResult.Ok).mixes.single().id)
        // Existing MIXs remain openable without a second creation prompt, even with no remaining boards.
        assertEquals(id, com.valoser.futacha.shared.ui.board.firstMixIdOrCreate(store, emptyList(), 3) { error("must not create") }.idOrReport { error(it) })
    }

    @Test fun createLoadUpdateMoveDelete() = withStore { m, _ ->
        assertEquals(MixWriteResult.Ok, m.create("  A\u0000 ", listOf(may), MixOrderKind.NEW, 1))
        assertEquals(MixWriteResult.Ok, m.create("B", listOf(may), MixOrderKind.ALTERNATE, 2))
        val list = (m.load() as MixLoadResult.Ok).mixes
        assertEquals(listOf("A", "B"), list.map { it.name })
        assertTrue(list[0].id.startsWith("__mix__") && list[0].id != list[1].id)
        assertEquals(MixWriteResult.Ok, m.move(list[1].id, 0))
        assertEquals(listOf("B", "A"), (m.load() as MixLoadResult.Ok).mixes.map { it.name })
        assertEquals(MixWriteResult.Ok, m.update(list[0].id) { it.copy(name = "Z") })
        assertEquals(MixWriteResult.Ok, m.delete(list[1].id))
        assertEquals(listOf("Z"), (m.load() as MixLoadResult.Ok).mixes.map { it.name })
    }

    @Test fun limitsAreRefusedWithoutSaving() = withStore { m, s ->
        assertIs<MixWriteResult.Refused>(m.create("", listOf(may), MixOrderKind.NEW, 1))
        assertIs<MixWriteResult.Refused>(m.create("x".repeat(51), listOf(may), MixOrderKind.NEW, 1))
        assertIs<MixWriteResult.Refused>(m.create("n", emptyList(), MixOrderKind.NEW, 1))
        assertIs<MixWriteResult.Refused>(m.create("n", List(11) { "https://may.2chan.net/b/$it/" }, MixOrderKind.NEW, 1))
        repeat(10) { assertEquals(MixWriteResult.Ok, m.create("n$it", listOf(may), MixOrderKind.NEW, it.toLong())) }
        assertIs<MixWriteResult.Refused>(m.create("11", listOf(may), MixOrderKind.NEW, 99))
        assertEquals(10, (m.load() as MixLoadResult.Ok).mixes.size)
        assertNotNull(s.loadPreference(MIX_DEFINITIONS_KEY))
    }

    @Test fun overTwentyThousandCharsIsRefused() = withStore { m, _ ->
        // 10 mixes x 10 members with long URLs exceed the 20,000-char value limit.
        val long = List(10) { "https://may.2chan.net/" + "b".repeat(200) + "/" }
        var refused = false
        for (i in 0 until 10) if (m.create("n$i", long, MixOrderKind.NEW, i.toLong()) is MixWriteResult.Refused) refused = true
        assertTrue(refused)
        assertTrue((m.load() as MixLoadResult.Ok).mixes.size < 10)
    }

    @Test fun readOnlyBlocksWrites() = withStore { m, s ->
        s.savePreference(MIX_DEFINITIONS_KEY, """{"version":2,"mixes":[]}""")
        assertIs<MixLoadResult.ReadOnly>(m.load())
        assertIs<MixWriteResult.Refused>(m.create("n", listOf(may), MixOrderKind.NEW, 1))
        assertEquals("""{"version":2,"mixes":[]}""", s.loadPreference(MIX_DEFINITIONS_KEY))
    }

    @Test fun updateKeepsHiddenMembers() = withStore { m, _ ->
        m.create("n", listOf(may, "https://nijiurachan.net/b/aimg/"), MixOrderKind.NEW, 1)
        val id = (m.load() as MixLoadResult.Ok).mixes[0].id
        m.update(id) { it.copy(name = "renamed") }
        assertEquals(2, (m.load() as MixLoadResult.Ok).mixes[0].members.size)
    }

    @Test fun concurrentWritesAreSerialized() = withStore { m, _ ->
        kotlinx.coroutines.coroutineScope {
            (0 until 5).map { i -> launch(kotlinx.coroutines.Dispatchers.Default) { m.create("n$i", listOf(may), MixOrderKind.NEW, i.toLong()) } }
        }
        assertEquals(5, (m.load() as MixLoadResult.Ok).mixes.size)
    }
}
