package com.valoser.futacha

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.CompatNgKind
import com.valoser.futacha.shared.compat.CompatNgRule
import com.valoser.futacha.shared.compat.CompatTab
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.compat.compatTabKey
import com.valoser.futacha.shared.compat.decodeCompatSettingsBackup
import com.valoser.futacha.shared.compat.decodeCompatWatchNgBackup
import com.valoser.futacha.shared.compat.encodeCompatSettingsBackup
import com.valoser.futacha.shared.compat.encodeCompatWatchNgBackup
import com.valoser.futacha.shared.compat.settingsOnly
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompatibilitySettingsBackupInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "compat_settings_backup_${System.currentTimeMillis()}.db"
    private lateinit var store: AndroidCompatibilityStore

    @Before
    fun prepare() = runBlocking {
        store = AndroidCompatibilityStore(context, databaseName = databaseName)
        store.initialize()
    }

    @After
    fun cleanUp() {
        if (::store.isInitialized) runBlocking { store.closeForTest() }
        context.deleteDatabase(databaseName)
    }

    @Test
    fun settingsBackupRoundTripIsBoundedAndTransactional() = runBlocking {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        val tabUrl = "${boardUrl}res/123456.htm"
        val tabKey = compatTabKey(tabUrl)
        store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
        store.openTab(
            CompatTab(
                key = tabKey,
                canonicalUrl = tabUrl,
                originalUrl = tabUrl,
                boardKey = boardKey,
                boardName = "mayb",
                threadNo = "123456",
                title = "backup test",
                insertedAtEpochMillis = 100L,
                contentUpdatedAtEpochMillis = 200L
            )
        )
        store.savePreference("compat.catalog.監視ワード", "foo\nbar")
        store.savePreference("compat.catalog.catalogGridViewTitleLength", "20文字")
        val rule = CompatNgRule(
            id = "backup-rule",
            kind = CompatNgKind.THREAD_WORD,
            scopeKey = tabKey,
            normalizedValue = "spoiler",
            createdAtEpochMillis = 300L,
            imageUrl = "https://may.2chan.net/b/src/reference.jpg",
            memo = "同じ構図の広告"
        )
        assertTrue(store.upsertNgRule(rule))

        val payload = store.exportSettingsBackup()
        assertTrue(payload.length < 2 * 1024 * 1024)

        store.savePreference("compat.catalog.監視ワード", "changed")
        val report = store.importSettingsBackup(payload)
        assertEquals(1, report.boardsImported)
        assertEquals(1, report.tabsImported)
        assertEquals(2, report.preferencesImported)
        assertEquals(1, report.ngRulesImported)
        assertEquals("foo\nbar", store.loadPreference("compat.catalog.監視ワード"))
        assertEquals(1, store.ngRules.first().size)
        assertEquals(
            "https://may.2chan.net/b/src/reference.jpg",
            store.ngRules.first().single().imageUrl
        )
        assertEquals("同じ構図の広告", store.ngRules.first().single().memo)
    }

    @Test
    fun ngOnlyRestoreDoesNotOverwriteUserPreferences() = runBlocking {
        val boardUrl = "https://img.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        store.upsertBoard(CompatBoard(boardKey, "imgb", boardUrl, boardUrl, 0))
        store.savePreference("compat.catalog.監視ワード", "keep-me")
        val payload = store.exportSettingsBackup()
        store.savePreference("compat.catalog.監視ワード", "still-keep-me")
        val report = store.importSettingsBackup(payload, restoreUserSettings = false, restoreNgRules = true)
        assertEquals(0, report.preferencesImported)
        assertEquals("still-keep-me", store.loadPreference("compat.catalog.監視ワード"))
    }

    @Test
    fun splitFilesRestoreOnlyTheirOwnVisibleSettings() = runBlocking {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
        store.savePreference("compat.catalog.監視ワード", "watch-before")
        store.savePreference("compat.design.designTheme", "モノクロ")
        assertTrue(
            store.upsertNgRule(
                CompatNgRule(
                    id = "split-rule",
                    kind = CompatNgKind.CATALOG_WORD,
                    scopeKey = boardKey,
                    normalizedValue = "ng-before",
                    createdAtEpochMillis = 500L
                )
            )
        )
        val combined = decodeCompatSettingsBackup(store.exportSettingsBackup())
        val settingsPayload = encodeCompatSettingsBackup(combined.settingsOnly())
        val wordsPayload = encodeCompatSettingsBackup(
            decodeCompatWatchNgBackup(encodeCompatWatchNgBackup(combined))
        )

        store.savePreference("compat.catalog.監視ワード", "watch-after")
        store.savePreference("compat.design.designTheme", "ブラック")
        store.importSettingsBackup(settingsPayload, restoreUserSettings = true, restoreNgRules = false)
        assertEquals("watch-after", store.loadPreference("compat.catalog.監視ワード"))
        assertEquals("モノクロ", store.loadPreference("compat.design.designTheme"))

        store.savePreference("compat.design.designTheme", "ブラック")
        store.importSettingsBackup(wordsPayload, restoreUserSettings = true, restoreNgRules = true)
        assertEquals("watch-before", store.loadPreference("compat.catalog.監視ワード"))
        assertEquals("ブラック", store.loadPreference("compat.design.designTheme"))
        assertTrue(store.ngRules.first().any { it.normalizedValue == "ng-before" })
    }
}
