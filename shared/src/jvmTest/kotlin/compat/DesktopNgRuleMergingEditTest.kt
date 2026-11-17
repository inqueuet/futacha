package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.util.JvmFileSystem
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/** L139: an NG edit is one write that keeps the old rule on failure, and still merges an edit into an existing rule. */
class DesktopNgRuleMergingEditTest {
    private val board = CompatBoard("b", "B", "https://may.2chan.net/b/", "https://may.2chan.net/b/", 0)
    private val old = CompatNgRule("old", CompatNgKind.CATALOG_IMAGE, "b", "hash", 1, memo = "old")
    private val existing = CompatNgRule("global", CompatNgKind.CATALOG_IMAGE, "*", "hash", 2, memo = "there")

    private fun withStore(block: suspend (DesktopCompatibilityStore) -> Unit) = runBlocking {
        val root = Files.createTempDirectory("ng-merging").toFile()
        val store = DesktopCompatibilityStore(JvmFileSystem(root))
        try {
            store.initialize()
            store.upsertBoard(board)
            assertTrue(store.upsertNgRule(old))
            block(store)
        } finally { store.close(); root.deleteRecursively() }
    }

    @Test fun anEditIntoAnExistingRuleMakesTheTwoThatOne() = withStore { store ->
        assertTrue(store.upsertNgRule(existing))
        val edited = existing.copy(memo = "edited")
        assertTrue(store.replaceNgRuleMerging(old.id, edited))
        assertEquals(listOf(edited), store.ngRules.first())
    }

    @Test fun aFailedWriteKeepsTheOldRule() = withStore { store ->
        assertTrue(store.upsertNgRule(existing))
        val failing = object : CompatibilityStore by store {
            override suspend fun upsertNgRule(rule: CompatNgRule): Boolean = throw IOException("disk full")
            override suspend fun replaceNgRule(previousRuleId: String, rule: CompatNgRule): Boolean = throw IOException("disk full")
        }
        assertFailsWith<IOException> { failing.replaceNgRuleMerging(old.id, existing.copy(memo = "edited")) }
        assertEquals(setOf(old, existing), store.ngRules.first().toSet())
    }

    @Test fun aScopeGoneKeepsTheOldRule() = withStore { store ->
        assertFalse(store.replaceNgRuleMerging(old.id, old.copy(id = "moved", scopeKey = "closed")))
        assertEquals(listOf(old), store.ngRules.first())
    }

    @Test fun aPlainEditReplaces() = withStore { store ->
        val edited = old.copy(id = "new", normalizedValue = "other")
        assertTrue(store.replaceNgRuleMerging(old.id, edited))
        assertEquals(listOf(edited), store.ngRules.first())
    }
}
