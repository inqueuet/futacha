package com.valoser.futacha

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompatNgRuleReplacementInstrumentedTest {
    private fun rule(id: String) = CompatNgRule(id, CompatNgKind.THREAD_REFUSE, "*", "word", 1)
    private suspend fun withStore(block: suspend (Context, String, AndroidCompatibilityStore) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "audit_ng_replace_${System.nanoTime()}.db"
        val store = AndroidCompatibilityStore(context, databaseName = name)
        try { store.initialize(); block(context, name, store) }
        finally { store.closeForTest(); context.deleteDatabase(name) }
    }
    @Test fun aSqliteWriteFailureRetainsTheOldRuleAndCanBeRetried() = runBlocking {
        withStore { context, name, store ->
            val old = rule("old")
            val replacement = rule("replacement").copy(normalizedValue = "updated")
            assertTrue(store.upsertNgRule(old))
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use {
                it.execSQL("CREATE TRIGGER audit_refuse_ng BEFORE INSERT ON compat_ng_rule WHEN NEW.rule_id='replacement' BEGIN SELECT RAISE(ABORT, 'injected NG write failure'); END")
            }
            val failed = runCatching { store.replaceNgRule(old.id, replacement) }
            assertTrue(failed.isFailure || failed.getOrNull() == false)
            assertEquals(listOf(old), store.ngRules.first())
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { it.execSQL("DROP TRIGGER audit_refuse_ng") }
            assertTrue(store.replaceNgRule(old.id, replacement))
            assertEquals(listOf(replacement), store.ngRules.first())
            val reopened = AndroidCompatibilityStore(context, databaseName = name)
            try { reopened.initialize(); assertEquals(listOf(replacement), reopened.ngRules.first()) }
            finally { reopened.closeForTest() }
        }
    }
    @Test fun aReplacementAtTheLimitKeepsAllOtherRules() = runBlocking {
        withStore { context, name, store ->
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
                db.beginTransaction()
                try {
                    db.compileStatement("INSERT INTO compat_ng_rule(rule_id,kind,scope_key,normalized_value,payload_json,created_at) VALUES(?,?,?,?,?,?)").use { stmt ->
                        repeat(MAX_COMPAT_NG_RULES) { index ->
                            val row = rule("rule-$index").copy(normalizedValue = "word-$index")
                            stmt.bindString(1, row.id); stmt.bindString(2, row.kind.name); stmt.bindString(3, row.scopeKey)
                            stmt.bindString(4, row.normalizedValue); stmt.bindString(5, Json.encodeToString(row)); stmt.bindLong(6, 1)
                            stmt.executeInsert()
                        }
                    }
                    db.setTransactionSuccessful()
                } finally { db.endTransaction() }
            }
            val replacement = rule("replacement")
            assertTrue(store.replaceNgRule("rule-0", replacement))
            val rows = store.ngRules.first()
            assertEquals(MAX_COMPAT_NG_RULES, rows.size)
            assertTrue(replacement in rows)
            assertFalse(rows.any { it.id == "rule-0" })
            assertEquals((1 until MAX_COMPAT_NG_RULES).map { "rule-$it" }.toSet(), rows.filter { it.id != replacement.id }.map { it.id }.toSet())
        }
    }
    @Test fun deletedInvalidAndConflictingEditsDoNotRecreateOrOverwriteRules() = runBlocking {
        withStore { _, _, store ->
            val old = rule("old"); val other = rule("other")
            assertTrue(store.upsertNgRule(old)); assertTrue(store.upsertNgRule(other))
            assertFalse(store.replaceNgRule("old", other))
            assertFalse(store.replaceNgRule("old", rule("new").copy(scopeKey = "closed")))
            assertEquals(setOf(old, other), store.ngRules.first().toSet())
            store.deleteNgRule("old")
            assertFalse(store.replaceNgRule("old", rule("new")))
            assertEquals(listOf(other), store.ngRules.first())
        }
    }
}
