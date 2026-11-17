package com.valoser.futacha.shared.compat.shared

import com.valoser.futacha.shared.compat.CompatBuildDraft
import com.valoser.futacha.shared.compat.CompatHistoryEntry
import com.valoser.futacha.shared.compat.CompatLegacyBackupData
import com.valoser.futacha.shared.compat.CompatLegacyBoardRecord
import com.valoser.futacha.shared.compat.CompatLegacyScopedWord
import com.valoser.futacha.shared.compat.CompatNgKind
import com.valoser.futacha.shared.compat.CompatNgRule
import com.valoser.futacha.shared.compat.CompatSettingsBackup
import com.valoser.futacha.shared.compat.DesktopCompatibilityStore
import com.valoser.futacha.shared.compat.ExperienceProfile
import com.valoser.futacha.shared.compat.bridgeProfileBoardsAndHistory
import com.valoser.futacha.shared.compat.compatNgRuleId
import com.valoser.futacha.shared.compat.encodeCompatSettingsBackup
import com.valoser.futacha.shared.compat.modernBoardsToCompatibility
import com.valoser.futacha.shared.compat.shared.SingleMode.img
import com.valoser.futacha.shared.compat.shared.SingleMode.may
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.FakePlatformStateStorage
import com.valoser.futacha.shared.ui.compat.importCompatLegacyBackupData
import com.valoser.futacha.shared.ui.ftutter.ftutterMergeBackupBoards
import com.valoser.futacha.shared.util.JvmFileSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * D1 (docs/mode-data-contract-2026-10-12.md §3, docs/futaber-evolution-data-2026-10-31.md §4.1): a board brought by an
 * import into one store, and what hangs under it (its tabs, history, board NG rules, new-thread draft), must survive the
 * synchronization of every mode. The synchronization used to delete from the compatibility store every board the
 * AppState did not list (and the reverse in としあき(仮)), and the store's cascade took the board's rows with it.
 *
 * Each case imports, then starts the mode's synchronization (as the next start of the app does: the board list was not
 * merged yet, because the app was closed right after the import or another board change came first), and checks the
 * board and its rows on both sides. Then the board is deleted in the mode's own list, and must not come back.
 *
 * The four modes x the settings restore / the old toshiaki 参考アプリ's files / ふたったー's `.ftt`.
 */
class SharedBoardsD1RegressionTest {
    private enum class Import { SETTINGS_RESTORE, LEGACY_APK, FTT }

    @Test fun futachaKeepsABoardRestoredFromTheSettingsBackup() = check(ExperienceProfile.FUTACHA, Import.SETTINGS_RESTORE)
    @Test fun futachaKeepsABoardFromTheOldToshiakiApk() = check(ExperienceProfile.FUTACHA, Import.LEGACY_APK)
    @Test fun futachaKeepsABoardFromAnFttImport() = check(ExperienceProfile.FUTACHA, Import.FTT)
    @Test fun toshiakiKeepsABoardRestoredFromTheSettingsBackup() = check(ExperienceProfile.TOSHIAKI_COMPAT, Import.SETTINGS_RESTORE)
    @Test fun toshiakiKeepsABoardFromTheOldToshiakiApk() = check(ExperienceProfile.TOSHIAKI_COMPAT, Import.LEGACY_APK)
    @Test fun toshiakiKeepsABoardFromAnFttImport() = check(ExperienceProfile.TOSHIAKI_COMPAT, Import.FTT)
    @Test fun futaberKeepsABoardRestoredFromTheSettingsBackup() = check(ExperienceProfile.FUTABER, Import.SETTINGS_RESTORE)
    @Test fun futaberKeepsABoardFromTheOldToshiakiApk() = check(ExperienceProfile.FUTABER, Import.LEGACY_APK)
    @Test fun futaberKeepsABoardFromAnFttImport() = check(ExperienceProfile.FUTABER, Import.FTT)
    @Test fun ftutterKeepsABoardRestoredFromTheSettingsBackup() = check(ExperienceProfile.FTUTTER, Import.SETTINGS_RESTORE)
    @Test fun ftutterKeepsABoardFromTheOldToshiakiApk() = check(ExperienceProfile.FTUTTER, Import.LEGACY_APK)
    @Test fun ftutterKeepsABoardFromAnFttImport() = check(ExperienceProfile.FTUTTER, Import.FTT)

    private val imgKeys = SharedKeys.board(img)
    private val imgBoardKey = imgKeys.compatBoardKey!!
    private val imgThread = SingleMode.threadUrl(img, "200")
    private val imgTab = SingleMode.tab(img, "200", insertedAt = 10)
    private val imgNg = CompatNgRule(
        compatNgRuleId(CompatNgKind.CATALOG_WORD, imgBoardKey, "荒らし"), CompatNgKind.CATALOG_WORD, imgBoardKey, "荒らし", 20
    )
    private val imgHistory = CompatHistoryEntry(
        imgThread, imgThread, imgBoardKey, img.name, "200", "スレ200", contentUpdatedAtEpochMillis = 30, lastVisitedEpochMillis = 30
    )

    private fun check(profile: ExperienceProfile, import: Import) = runBlocking<Unit> {
        val root = Files.createTempDirectory("futacha-d1").toFile()
        val state = AppStateStore(FakePlatformStateStorage())
        val compat = DesktopCompatibilityStore(JvmFileSystem(root))
        try {
            compat.initialize()
            // Before the import, both stores list the same board (as after any earlier synchronization).
            state.setBoards(listOf(may))
            compat.upsertBoards(modernBoardsToCompatibility(listOf(may)))
            runSync(profile, state, compat) { boardUrls(state) == setOf(may.url) && compatBoards(compat) == setOf(mayKey) }

            when (import) {
                Import.SETTINGS_RESTORE -> compat.importSettingsBackup(
                    encodeCompatSettingsBackup(
                        CompatSettingsBackup(
                            exportedAtEpochMillis = 1,
                            boards = modernBoardsToCompatibility(listOf(img)),
                            tabs = listOf(imgTab),
                            history = listOf(imgHistory),
                            ngRules = listOf(imgNg),
                            workspace = null
                        )
                    ),
                    restoreUserSettings = true,
                    restoreNgRules = true
                )
                Import.LEGACY_APK -> importCompatLegacyBackupData(
                    compat,
                    listOf(
                        CompatLegacyBackupData(
                            fileType = "keyword",
                            boards = listOf(CompatLegacyBoardRecord(img.name, "https://img.2chan.net/b/", img.url, 0)),
                            catalogNgWords = listOf(CompatLegacyScopedWord("https://img.2chan.net/b/", "荒らし"))
                        )
                    ),
                    compat.boards.first()
                )
                Import.FTT -> {
                    state.updateBoards { current -> ftutterMergeBackupBoards(current, listOf(img)) }
                    state.prependOrReplaceHistoryEntries(listOf(imgModernHistory()))
                }
            }
            val compatSide = import != Import.FTT
            if (compatSide) {
                // What was written under the board after the import: a tab (the settings file brings its own), a
                // new-thread draft.
                if (import == Import.LEGACY_APK) {
                    compat.openTab(imgTab, imgHistory)
                }
                compat.saveBuildDraft(CompatBuildDraft(imgBoardKey, comment = "スレ立ての下書き", updatedAtEpochMillis = 40))
            }

            // The next start of the app, in [profile].
            val settled = runSync(profile, state, compat) {
                img.url in boardUrls(state) && imgBoardKey in compatBoards(compat) &&
                    (!compatSide || compat.tabs.first().any { it.key == imgTab.key })
            }
            // Every loss is told at once.
            val lost = buildList {
                if (img.url !in boardUrls(state)) add("the board in the board list")
                if (imgBoardKey !in compatBoards(compat)) add("the board in the compatibility store")
                if (compatSide) {
                    if (compat.tabs.first().none { it.key == imgTab.key }) add("its tab")
                    if (compat.history.first().none { it.canonicalUrl == imgThread }) add("its history")
                    if (compat.ngRules.first().none { it.scopeKey == imgBoardKey }) add("its NG rule")
                    if (compat.loadBuildDraft(imgBoardKey)?.comment != "スレ立ての下書き") add("its new-thread draft")
                }
            }
            assertEquals(emptyList(), lost, "$profile/$import: lost after the synchronization")
            assertTrue(settled, "$profile/$import: the synchronization settles with the board on both sides")
            assertTrue(may.url in boardUrls(state) && mayKey in compatBoards(compat), "$profile/$import: the other board stays")

            // Deleted in the mode's own list: gone from both, and the next starts do not bring it back.
            if (profile.usesAppStateData) {
                state.updateBoards { current -> current.filterNot { it.url == img.url } }
            } else {
                compat.deleteBoard(imgBoardKey)
            }
            assertTrue(
                runSync(profile, state, compat) { img.url !in boardUrls(state) && imgBoardKey !in compatBoards(compat) },
                "$profile/$import: a board deleted in the mode's own list goes from both stores"
            )
            repeat(2) { round ->
                for (next in ExperienceProfile.entries) {
                    runSync(next, state, compat, settleMillis = 150) { false }
                    assertFalse(img.url in boardUrls(state), "$profile/$import: the deleted board stays deleted ($next, $round)")
                    assertFalse(imgBoardKey in compatBoards(compat), "$profile/$import: and stays out of the store ($next, $round)")
                }
            }
            assertTrue(compat.tabs.first().none { it.boardKey == imgBoardKey }, "$profile/$import: the deleted board's tabs go with it")
        } finally {
            state.close()
            root.deleteRecursively()
        }
    }

    private val mayKey = SharedKeys.board(may).compatBoardKey!!

    private fun imgModernHistory() = ThreadHistoryEntry(
        threadId = "200", boardId = img.id, title = "スレ200", titleImageUrl = "", boardName = img.name,
        boardUrl = imgThread, lastVisitedEpochMillis = 30, replyCount = 3
    )

    private suspend fun boardUrls(state: AppStateStore): Set<String> = state.boards.first().mapTo(HashSet(), BoardSummary::url)

    private suspend fun compatBoards(compat: DesktopCompatibilityStore): Set<String> = compat.boards.first().mapTo(HashSet()) { it.key }

    /**
     * Runs [profile]'s synchronization (the one each host starts with the mode's screens) until [done], or for
     * [settleMillis] when it never is. Returns whether [done] was reached.
     */
    private suspend fun CoroutineScope.runSync(
        profile: ExperienceProfile,
        state: AppStateStore,
        compat: DesktopCompatibilityStore,
        settleMillis: Long = 3_000,
        done: suspend () -> Boolean
    ): Boolean {
        val failures = java.util.concurrent.CopyOnWriteArrayList<Throwable>()
        val sync = launch(Dispatchers.Default) {
            bridgeProfileBoardsAndHistory(state, compat, profile.usesAppStateData) { _, failure -> failures += failure }
        }
        val reached = withTimeoutOrNull(settleMillis) {
            while (!done()) delay(20)
            // Let a pass that is still writing finish.
            delay(100)
            true
        } ?: false
        sync.cancelAndJoin()
        assertTrue(failures.isEmpty(), "$profile: the synchronization failed: $failures")
        return reached
    }
}
