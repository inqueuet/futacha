@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.createAppStateStore
import com.valoser.futacha.shared.state.createPlatformStateStorage
import com.valoser.futacha.shared.ui.FutachaApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * M74: when the compatibility database cannot be read, the store keeps its placeholders
 * private (M7). The app must then explain it and offer a retry instead of an endless
 * loading screen in every mode, or a lock screen whose input never becomes available.
 */
class CompatibilityLoadFailureScreenInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val boardUrl = "https://may.2chan.net/b/"
    private val errorText = "保存データ（板・履歴・設定）を読み込めませんでした"

    @Test fun anUnreadableLoadOffersARetryThatOpensTheAppOnceTheDataCanBeRead() = withUnreadableStore(lockPassword = null) { name, stateStore, store ->
        rule.setContent { FutachaApp(stateStore = stateStore, compatibilityStore = store) }
        rule.waitUntil(10_000) { rule.onAllNodesWithText(errorText, substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("再試行").assertIsDisplayed()
        assertFalse("the placeholders must stay private", store.isLoaded.value)
        restore(name)
        rule.onNodeWithText("再試行").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithContentDescription("メニュー").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(store.isLoaded.value)
        rule.onNodeWithText(errorText, substring = true).assertDoesNotExist()
    }

    @Test fun withTheAppLockTheRetryLeadsToAUsableLockScreen() = withUnreadableStore(lockPassword = "audit-m74") { name, stateStore, store ->
        rule.setContent { FutachaApp(stateStore = stateStore, compatibilityStore = store) }
        rule.waitUntil(10_000) { rule.onAllNodesWithText(errorText, substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("解除").assertDoesNotExist()
        rule.onNodeWithContentDescription("メニュー").assertDoesNotExist()
        restore(name)
        rule.onNodeWithText("再試行").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("解除").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("パスワード").performTextInput("audit-m74")
        rule.waitUntil(10_000) { runCatching { rule.onNodeWithText("解除").assertIsEnabled() }.isSuccess }
        rule.onNodeWithText("解除").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithContentDescription("メニュー").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun withUnreadableStore(lockPassword: String?, block: (String, AppStateStore, AndroidCompatibilityStore) -> Unit) {
        val context = rule.activity.applicationContext
        val name = "audit_m74_${System.nanoTime()}.db"
        val stateStore: AppStateStore = createAppStateStore(context)
        // The Android delegate shares one process-wide DataStore; restore the lock afterwards.
        val storage = createPlatformStateStorage(context)
        val previousPasswordHash = runBlocking { stateStore.appLockPasswordHash.first() }
        var store = AndroidCompatibilityStore(context, databaseName = name)
        try {
            runBlocking {
                store.ensureInitialized()
                store.upsertBoard(CompatBoard(compatBoardKey(boardUrl), "保存した板", boardUrl, boardUrl, 0))
                store.closeForTest()
                if (lockPassword == null) stateStore.clearAppLockPassword() else stateStore.setAppLockPassword(lockPassword)
            }
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
                db.execSQL("ALTER TABLE compat_board RENAME TO audit_saved_board")
                db.execSQL("CREATE VIEW compat_board AS SELECT 1 AS unreadable")
            }
            store = AndroidCompatibilityStore(context, databaseName = name)
            var failed = false
            try { runBlocking { store.ensureInitialized() } } catch (_: SQLiteException) { failed = true }
            assertTrue("The test must actually fail initialization", failed)
            block(name, stateStore, store)
        } finally {
            runBlocking {
                storage.updateAppLockPasswordHash(previousPasswordHash.orEmpty())
                store.closeForTest()
            }
            context.deleteDatabase(name)
            stateStore.close()
        }
    }

    private fun restore(name: String) {
        rule.activity.applicationContext.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL("DROP VIEW compat_board")
            db.execSQL("ALTER TABLE audit_saved_board RENAME TO compat_board")
        }
    }
}
