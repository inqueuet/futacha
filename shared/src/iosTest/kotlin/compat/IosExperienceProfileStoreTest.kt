package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.normalizeIosThreadDeepLink
import com.valoser.futacha.shared.model.AppIconVariant
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.util.createFileSystem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosExperienceProfileStoreTest {
    @Test
    fun backgroundExistenceCheckUsesLightweightProbeAndPropagatesCancellation() = runBlocking {
        val fileSystem = createFileSystem()
        fileSystem.deleteRecursively("compatibility").getOrThrow()
        try {
            val boardUrl = "https://may.2chan.net/b/"
            val threadUrl = "${boardUrl}res/123456.htm"
            val board = CompatBoard("board", "虹裏", boardUrl, boardUrl, 0)
            val tab = CompatTab(
                key = "tab",
                canonicalUrl = threadUrl,
                originalUrl = threadUrl,
                boardKey = board.key,
                boardName = board.name,
                threadNo = "123456",
                title = "thread",
                insertedAtEpochMillis = 1L,
                contentUpdatedAtEpochMillis = 1L
            )
            val store = IosCompatibilityStore(fileSystem, nowMillis = { 100_000_000L })
            store.initialize()
            store.upsertBoard(board)
            store.openTab(tab, null)
            var fullFetchCount = 0
            val goneRepository = object : BoardRepository by FakeBoardRepository() {
                override suspend fun probeThreadGone(threadUrl: String): Boolean = true
                override suspend fun getThreadByUrl(threadUrl: String): ThreadPage {
                    fullFetchCount++
                    error("full fetch must not run")
                }
            }

            val result = refreshCompatTabsInBackground(
                store = store,
                repository = goneRepository,
                nowEpochMillis = 100_000_000L,
                checkUpdates = false,
                checkExistence = true
            )
            assertEquals(1, result.deadTabs)
            assertEquals(0, fullFetchCount)

            val cancellingRepository = object : BoardRepository by FakeBoardRepository() {
                override suspend fun probeThreadGone(threadUrl: String): Boolean {
                    throw CancellationException("cancel")
                }
            }
            store.updateTab(tab.copy(isDead = false))
            assertFailsWith<CancellationException> {
                refreshCompatTabsInBackground(
                    store = store,
                    repository = cancellingRepository,
                    nowEpochMillis = 100_000_000L,
                    checkUpdates = false,
                    checkExistence = true
                )
            }
            Unit
        } finally {
            fileSystem.deleteRecursively("compatibility").getOrThrow()
        }
    }

    @Test
    fun snapshotCacheHitUpdatesOnlyAccessOverlay() = runBlocking {
        val fileSystem = createFileSystem()
        fileSystem.deleteRecursively("compatibility").getOrThrow()
        var now = 1_000L
        try {
            val snapshot = CompatThreadSnapshot(
                tabKey = "tab",
                revision = 1L,
                fetchedAtEpochMillis = now,
                posts = listOf(
                    CompatPostSnapshot(position = 0, postNo = "1", timestamp = "now", messageHtml = "body")
                )
            )
            val store = IosCompatibilityStore(fileSystem, nowMillis = { now })
            store.initialize()
            assertTrue(store.saveThreadSnapshot(snapshot))
            val database = IosCompatibilityDatabase(fileSystem)
            val payloadBeforeRead = database.readPayload()

            now = 2_000L
            assertEquals(snapshot, store.loadThreadSnapshot(snapshot.tabKey))

            assertEquals(payloadBeforeRead, database.readPayload())
            assertEquals(now, database.readPendingSnapshotAccess()[snapshot.tabKey])
            database.close()
        } finally {
            fileSystem.deleteRecursively("compatibility").getOrThrow()
        }
    }

    @Test
    fun scrollAnchorOverlayPersistsWithoutRewritingFullCompatibilityPayload() = runBlocking {
        val fileSystem = createFileSystem()
        fileSystem.deleteRecursively("compatibility").getOrThrow()
        try {
            val boardUrl = "https://may.2chan.net/b/"
            val threadUrl = "${boardUrl}res/123456.htm"
            val board = CompatBoard("board", "虹裏", boardUrl, boardUrl, 0)
            val tab = CompatTab(
                key = "tab",
                canonicalUrl = threadUrl,
                originalUrl = threadUrl,
                boardKey = board.key,
                boardName = board.name,
                threadNo = "123456",
                title = "thread",
                insertedAtEpochMillis = 1L,
                contentUpdatedAtEpochMillis = 1L
            )
            val history = CompatHistoryEntry(
                canonicalUrl = threadUrl,
                originalUrl = threadUrl,
                boardKey = board.key,
                boardName = board.name,
                threadNo = tab.threadNo,
                title = tab.title,
                contentUpdatedAtEpochMillis = 1L
            )
            val anchor = ScrollAnchor(postNo = "42", offsetPx = 18, fallbackIndex = 7, snapshotRevision = 3L)
            val store = IosCompatibilityStore(fileSystem, nowMillis = { 1_000L })
            store.initialize()
            store.upsertBoard(board)
            store.openTab(tab, history)

            store.updateScrollAnchor(tab.key, anchor)

            val reopened = IosCompatibilityStore(fileSystem, nowMillis = { 2_000L })
            reopened.initialize()
            assertEquals(anchor, reopened.tabs.first().single().scrollAnchor)
            assertEquals(anchor, reopened.history.first().single().scrollAnchor)
        } finally {
            fileSystem.deleteRecursively("compatibility").getOrThrow()
        }
    }

    @Test
    fun archiveReportOutboxDoesNotClaimRowsAfterProfileGenerationChanges() = runBlocking {
        val fileSystem = createFileSystem()
        fileSystem.deleteRecursively("compatibility").getOrThrow()
        try {
            val store = IosCompatibilityStore(fileSystem, nowMillis = { 1_000L })
            store.initialize()
            store.enqueueArchiveReport("https://may.2chan.net/b/res/123456.htm", 1_000L)
            var calls = 0
            val processor = IosArchiveReportOutboxProcessor { payload, _ ->
                calls++
                IosArchiveReportHttpResult(200, ArchiveReportResponse(accepted = true), null)
            }

            processor.process(store) { false }

            assertEquals(0, calls)
            assertEquals(ArchiveReportOutboxStats(total = 1, pendingOrRetry = 1), store.archiveReportOutboxStats())
        } finally {
            fileSystem.deleteRecursively("compatibility").getOrThrow()
        }
    }

    @Test
    fun archiveReportOutboxSendsAcceptedBatchAndPersistsCompletion() = runBlocking {
        val fileSystem = createFileSystem()
        fileSystem.deleteRecursively("compatibility").getOrThrow()
        try {
            val store = IosCompatibilityStore(fileSystem, nowMillis = { 1_000L })
            store.initialize()
            assertTrue(
                store.enqueueArchiveReport(
                    rawThreadUrl = "https://may.2chan.net/b/res/123456.htm",
                    nowEpochMillis = 1_000L
                ).inserted
            )
            val sentRequestIds = mutableListOf<String>()
            val processor = IosArchiveReportOutboxProcessor { payload, _ ->
                sentRequestIds += payload.requestId
                IosArchiveReportHttpResult(
                    status = 200,
                    response = ArchiveReportResponse(accepted = true, received = payload.urls.size),
                    retryAfterMillis = null
                )
            }

            processor.process(store)

            assertEquals(1, sentRequestIds.size)
            assertEquals(ArchiveReportOutboxStats(total = 1, pendingOrRetry = 0), store.archiveReportOutboxStats())
            val reopened = IosCompatibilityStore(fileSystem, nowMillis = { 2_000L })
            reopened.initialize()
            assertEquals(ArchiveReportOutboxStats(total = 1, pendingOrRetry = 0), reopened.archiveReportOutboxStats())
        } finally {
            fileSystem.deleteRecursively("compatibility").getOrThrow()
        }
    }

    @Test
    fun archiveReportOutboxKeepsRetryableFailureForLaterDelivery() = runBlocking {
        val fileSystem = createFileSystem()
        fileSystem.deleteRecursively("compatibility").getOrThrow()
        try {
            val store = IosCompatibilityStore(fileSystem, nowMillis = { 1_000L })
            store.initialize()
            store.enqueueArchiveReport("https://may.2chan.net/b/res/123456.htm", 1_000L)
            val processor = IosArchiveReportOutboxProcessor { _, _ ->
                IosArchiveReportHttpResult(status = 503, response = null, retryAfterMillis = 60_000L)
            }

            processor.process(store)

            assertEquals(ArchiveReportOutboxStats(total = 1, pendingOrRetry = 1), store.archiveReportOutboxStats())
            assertTrue(requireNotNull(store.archiveReportNextAttemptAt()) > 1_000L)
        } finally {
            fileSystem.deleteRecursively("compatibility").getOrThrow()
        }
    }

    @Test
    fun compatibilityHistoryRefreshUpdatesPersistedTabsAndHistory() = runBlocking {
        val fileSystem = createFileSystem()
        fileSystem.deleteRecursively("compatibility").getOrThrow()
        try {
            val boardUrl = "https://may.2chan.net/b/"
            val threadUrl = "https://may.2chan.net/b/res/123456.htm"
            val board = CompatBoard(
                key = "compat_board_may",
                name = "虹裏",
                canonicalUrl = boardUrl,
                originalUrl = boardUrl,
                sortOrder = 0
            )
            val tab = CompatTab(
                key = "compat_tab_123456",
                canonicalUrl = threadUrl,
                originalUrl = threadUrl,
                boardKey = board.key,
                boardName = board.name,
                threadNo = "123456",
                title = "更新対象",
                replyCount = 1,
                insertedAtEpochMillis = 10L,
                contentUpdatedAtEpochMillis = 10L
            )
            val history = CompatHistoryEntry(
                canonicalUrl = threadUrl,
                originalUrl = threadUrl,
                boardKey = board.key,
                boardName = board.name,
                threadNo = tab.threadNo,
                title = tab.title,
                replyCount = tab.replyCount,
                contentUpdatedAtEpochMillis = 10L
            )
            val store = IosCompatibilityStore(fileSystem, nowMillis = { 1_000L })
            store.initialize()
            store.upsertBoard(board)
            store.openTab(tab, history)
            val repository = object : BoardRepository by FakeBoardRepository() {
                override suspend fun getCatalog(board: String, mode: CatalogMode): List<CatalogItem> = listOf(
                    CatalogItem(
                        id = tab.threadNo,
                        threadUrl = threadUrl,
                        title = tab.title,
                        thumbnailUrl = null,
                        fullImageUrl = null,
                        replyCount = 9
                    )
                )
            }

            val result = refreshCompatTabsInBackground(
                store = store,
                repository = repository,
                nowEpochMillis = 1_000L,
                maxTabs = 40,
                checkUpdates = true,
                checkExistence = false,
                checkWatchWords = false
            )

            assertEquals(1, result.updatedTabs)
            assertEquals(0, result.failures)
            assertEquals(9, store.tabs.first().single().replyCount)
            assertEquals(9, store.history.first().single().replyCount)
            val reopened = IosCompatibilityStore(fileSystem, nowMillis = { 2_000L })
            reopened.initialize()
            assertEquals(9, reopened.history.first().single().replyCount)
        } finally {
            fileSystem.deleteRecursively("compatibility").getOrThrow()
        }
    }

    @Test
    fun modernThreadSnapshotCanBeReadByCanonicalUrlAfterStoreReopen() = runBlocking {
        val fileSystem = createFileSystem()
        fileSystem.deleteRecursively("compatibility").getOrThrow()
        try {
            val threadUrl = "https://may.2chan.net/b/res/123456.htm"
            val store = IosCompatibilityStore(fileSystem, nowMillis = { 1_000L })
            store.initialize()
            val snapshot = CompatThreadSnapshot(
                tabKey = "modern-temporary-key",
                revision = 42L,
                fetchedAtEpochMillis = 1_000L,
                boardTitle = "虹裏",
                posts = listOf(
                    CompatPostSnapshot(
                        position = 0,
                        postNo = "123456",
                        timestamp = "2026/08/20",
                        messageHtml = "共有キャッシュ本文"
                    )
                )
            )

            assertTrue(
                store.saveSharedThreadSnapshot(
                    canonicalUrl = threadUrl,
                    originalUrl = threadUrl,
                    boardName = "虹裏",
                    title = "共有対象",
                    thumbnailUrl = null,
                    snapshot = snapshot
                )
            )
            assertEquals(42L, store.loadThreadSnapshotByCanonicalUrl(threadUrl)?.revision)

            val reopened = IosCompatibilityStore(fileSystem, nowMillis = { 2_000L })
            reopened.initialize()
            val restored = requireNotNull(reopened.loadThreadSnapshotByCanonicalUrl(threadUrl))
            assertEquals(42L, restored.revision)
            assertEquals("共有キャッシュ本文", restored.posts.single().messageHtml)
        } finally {
            fileSystem.deleteRecursively("compatibility").getOrThrow()
        }
    }

    @Test
    fun legacyJsonMigratesToSqliteBeforeTheLegacyFileIsRemoved() = runBlocking {
        val fileSystem = createFileSystem()
        fileSystem.deleteRecursively("compatibility").getOrThrow()
        try {
            fileSystem.createDirectory("compatibility").getOrThrow()
            fileSystem.writeString(
                "compatibility/ios_compatibility_state.json",
                """
                {
                  "boards": [{
                    "key": "compat_board_may",
                    "name": "虹裏",
                    "canonicalUrl": "https://may.2chan.net/b/",
                    "originalUrl": "https://may.2chan.net/b/",
                    "sortOrder": 0
                  }],
                  "history": [{
                    "canonicalUrl": "https://may.2chan.net/b/res/123456.htm",
                    "originalUrl": "https://may.2chan.net/b/res/123456.htm",
                    "boardKey": "compat_board_may",
                    "boardName": "虹裏",
                    "threadNo": "123456",
                    "title": "legacy migration",
                    "contentUpdatedAtEpochMillis": 42
                  }]
                }
                """.trimIndent()
            ).getOrThrow()

            val store = IosCompatibilityStore(fileSystem, nowMillis = { 1_000L })
            store.initialize()

            assertEquals(listOf("https://may.2chan.net/b/"), store.boards.first().map { it.canonicalUrl })
            assertEquals(
                listOf("https://may.2chan.net/b/res/123456.htm"),
                store.history.first().map { it.canonicalUrl }
            )
            assertTrue(fileSystem.exists("compatibility/compatibility.db"))
            assertTrue(!fileSystem.exists("compatibility/ios_compatibility_state.json"))

            val reopened = IosCompatibilityStore(fileSystem, nowMillis = { 2_000L })
            reopened.initialize()
            assertEquals(listOf("legacy migration"), reopened.history.first().map { it.title })
        } finally {
            fileSystem.deleteRecursively("compatibility").getOrThrow()
        }
    }

    @Test
    fun documentsCompatibilityDatabaseMigratesToPrivateApplicationSupport() = runBlocking {
        val fileSystem = createFileSystem()
        val documentsCompatibilityDirectory =
            "${fileSystem.getAppDataDirectory().trimEnd('/')}/compatibility"
        val oldDatabasePath = "$documentsCompatibilityDirectory/compatibility.db"
        fileSystem.deleteRecursively("compatibility").getOrThrow()
        fileSystem.deleteRecursively(documentsCompatibilityDirectory).getOrThrow()
        try {
            fileSystem.createDirectory(documentsCompatibilityDirectory).getOrThrow()
            val oldDatabase = IosCompatibilityDatabase(fileSystem, storagePath = oldDatabasePath)
            oldDatabase.writePayload(
                """
                {
                  "boards": [{
                    "key": "compat_board_may",
                    "name": "虹裏",
                    "canonicalUrl": "https://may.2chan.net/b/",
                    "originalUrl": "https://may.2chan.net/b/",
                    "sortOrder": 0
                  }]
                }
                """.trimIndent(),
                1_000L
            )
            oldDatabase.close()
            assertFalse(fileSystem.exists("compatibility/compatibility.db"))

            val store = IosCompatibilityStore(fileSystem, nowMillis = { 2_000L })
            store.initialize()

            assertEquals(listOf("https://may.2chan.net/b/"), store.boards.first().map { it.canonicalUrl })
            assertTrue(fileSystem.exists("compatibility/compatibility.db"))
            assertFalse(fileSystem.exists(oldDatabasePath))
        } finally {
            fileSystem.deleteRecursively("compatibility").getOrThrow()
            fileSystem.deleteRecursively(documentsCompatibilityDirectory).getOrThrow()
        }
    }

    @Test
    fun invalidCompatibilityPayloadIsResetAndStoreRemainsWritable() = runBlocking {
        val fileSystem = createFileSystem()
        fileSystem.deleteRecursively("compatibility").getOrThrow()
        try {
            fileSystem.createDirectory("compatibility").getOrThrow()
            val database = IosCompatibilityDatabase(fileSystem)
            database.writePayload("{not-valid-json", 1_000L)
            database.close()

            val store = IosCompatibilityStore(fileSystem, nowMillis = { 2_000L })
            store.initialize()
            assertTrue(store.boards.first().isEmpty())

            val boardUrl = "https://may.2chan.net/b/"
            store.upsertBoard(
                CompatBoard(
                    key = "compat_board_may",
                    name = "虹裏",
                    canonicalUrl = boardUrl,
                    originalUrl = boardUrl,
                    sortOrder = 0
                )
            )
            val reopened = IosCompatibilityStore(fileSystem, nowMillis = { 3_000L })
            reopened.initialize()
            assertEquals(listOf(boardUrl), reopened.boards.first().map { it.canonicalUrl })
        } finally {
            fileSystem.deleteRecursively("compatibility").getOrThrow()
        }
    }

    @Test
    fun compatibilityStoreImportsModernDataPersistsAndHonorsHistoryTombstone() = runBlocking {
        // The iOS test bundle has its own Documents container.  Removing this
        // exact test-store directory keeps the test repeatable without ever
        // touching an installed app's compatibility database.
        val fileSystem = createFileSystem()
        fileSystem.deleteRecursively("compatibility").getOrThrow()
        try {
            val board = BoardSummary(
                id = "may",
                name = "虹裏",
                category = "テスト",
                url = "https://may.2chan.net/b/",
                description = ""
            )
            val history = ThreadHistoryEntry(
                threadId = "123456",
                boardId = board.id,
                title = "iOS bridge test",
                titleImageUrl = "",
                boardName = board.name,
                boardUrl = board.url,
                lastVisitedEpochMillis = 100L,
                replyCount = 5,
                lastReadItemIndex = 2,
                lastReadItemOffset = 12
            )

            val store = IosCompatibilityStore(fileSystem, nowMillis = { 1_000L })
            store.initialize()
            assertEquals(1, store.importModernBoards(listOf(board)))
            assertEquals(1, store.importModernHistory(listOf(history)))
            assertEquals(1, store.boards.first().size)
            assertEquals(1, store.history.first().size)

            val canonicalUrl = "https://may.2chan.net/b/res/123456.htm"
            store.deleteHistory(canonicalUrl)
            assertTrue(store.history.first().isEmpty())
            // A stale modern import must not resurrect a user-deleted item.
            assertEquals(0, store.importModernHistory(listOf(history)))
            assertTrue(store.history.first().isEmpty())

            val reopened = IosCompatibilityStore(fileSystem, nowMillis = { 2_000L })
            reopened.initialize()
            assertEquals(listOf("https://may.2chan.net/b/"), reopened.boards.first().map { it.canonicalUrl })
            assertTrue(reopened.history.first().isEmpty())
        } finally {
            fileSystem.deleteRecursively("compatibility").getOrThrow()
        }
    }

    @Test
    fun threadDeepLinkNormalizesCustomSchemeAndRejectsOtherHosts() {
        val rawThread = "https://img.2chan.net/b/res/123456.htm"
        val encoded = "futacha://thread?url=https%3A%2F%2Fimg.2chan.net%2Fb%2Fres%2F123456.htm"

        assertEquals(rawThread, normalizeIosThreadDeepLink(encoded))
        assertEquals(rawThread, normalizeIosThreadDeepLink(rawThread))
        assertEquals(
            rawThread,
            normalizeIosThreadDeepLink(
                "futacha://ai?action=open_thread&url=https%3A%2F%2Fimg.2chan.net%2Fb%2Fres%2F123456.htm"
            )
        )
        assertEquals(
            rawThread,
            normalizeIosThreadDeepLink(
                "futacha://ai?action=open_thread&boardUrl=https%3A%2F%2Fimg.2chan.net%2Fb%2F&threadId=123456"
            )
        )
        assertNull(normalizeIosThreadDeepLink("futacha://ai?action=open_thread"))
        assertTrue(normalizeIosThreadDeepLink("futacha://thread?url=not-a-thread") == null)
    }

    @Test
    fun switchPersistsProfileGenerationAndCompletesJournal() = runBlocking {
        val suiteName = "com.valoser.futacha.tests.profile.${NSUUID().UUIDString()}"
        val defaults = requireNotNull(NSUserDefaults(suiteName = suiteName))
        defaults.removePersistentDomainForName(suiteName)
        try {
            val store = IosExperienceProfileStore(defaults)
            val reconciled = mutableListOf<ExperienceProfile>()
            val coordinator = IosModeSwitchCoordinator(store) { profile, _ ->
                reconciled += profile
            }

            val generation = coordinator.switchTo(
                target = ExperienceProfile.TOSHIAKI_COMPAT,
                preferredFutachaIcon = AppIconVariant.Current
            ).getOrThrow()

            assertEquals(1L, generation)
            assertEquals(ExperienceProfile.TOSHIAKI_COMPAT, store.readActiveProfile())
            assertEquals(1L, store.readGeneration())
            assertEquals(listOf(ExperienceProfile.TOSHIAKI_COMPAT), reconciled)
            assertNull(store.readJournal())
        } finally {
            defaults.removePersistentDomainForName(suiteName)
        }
    }

    @Test
    fun fiftySequentialProfileRoundTripsLeaveNoJournalOrStaleGeneration() = runBlocking {
        val suiteName = "com.valoser.futacha.tests.profile.stress.${NSUUID().UUIDString()}"
        val defaults = requireNotNull(NSUserDefaults(suiteName = suiteName))
        defaults.removePersistentDomainForName(suiteName)
        try {
            val store = IosExperienceProfileStore(defaults)
            val reconciled = mutableListOf<ExperienceProfile>()
            val coordinator = IosModeSwitchCoordinator(store) { profile, _ -> reconciled += profile }

            repeat(50) { index ->
                val target = if (index % 2 == 0) ExperienceProfile.TOSHIAKI_COMPAT else ExperienceProfile.FUTACHA
                coordinator.switchTo(target, AppIconVariant.Current).getOrThrow()
                assertNull(store.readJournal())
                assertEquals(target, store.readActiveProfile())
                assertEquals((index + 1).toLong(), store.readGeneration())
            }

            assertEquals(50, reconciled.size)
            assertEquals(ExperienceProfile.FUTACHA, store.readActiveProfile())
        } finally {
            defaults.removePersistentDomainForName(suiteName)
        }
    }

    @Test
    fun recoveryCommitsInterruptedJournalToRequestedProfile() = runBlocking {
        val suiteName = "com.valoser.futacha.tests.recovery.${NSUUID().UUIDString()}"
        val defaults = requireNotNull(NSUserDefaults(suiteName = suiteName))
        defaults.removePersistentDomainForName(suiteName)
        try {
            val store = IosExperienceProfileStore(defaults)
            val pending = store.beginSwitchWithCommitBarrier(
                from = ExperienceProfile.FUTACHA,
                to = ExperienceProfile.TOSHIAKI_COMPAT
            )
            assertEquals(ModeSwitchPhase.SESSION_FLUSHED, pending.phase)

            val reconciled = mutableListOf<ExperienceProfile>()
            val recovered = IosModeSwitchCoordinator(store) { profile, _ ->
                reconciled += profile
            }.recoverIfNeeded().getOrThrow()

            assertEquals(ExperienceProfile.TOSHIAKI_COMPAT, recovered)
            assertEquals(ExperienceProfile.TOSHIAKI_COMPAT, store.readActiveProfile())
            assertEquals(1L, store.readGeneration())
            assertEquals(listOf(ExperienceProfile.TOSHIAKI_COMPAT), reconciled)
            assertNull(store.readJournal())
        } finally {
            defaults.removePersistentDomainForName(suiteName)
        }
    }

    @Test
    fun completedProfileStartupDoesNotForceAlternateIconAgain() = runBlocking {
        val suiteName = "com.valoser.futacha.tests.profile.icon.${NSUUID().UUIDString()}"
        val defaults = requireNotNull(NSUserDefaults(suiteName = suiteName))
        defaults.removePersistentDomainForName(suiteName)
        try {
            val store = IosExperienceProfileStore(defaults)
            val reconciled = mutableListOf<ExperienceProfile>()
            val coordinator = IosModeSwitchCoordinator(store) { profile, _ -> reconciled += profile }
            coordinator.switchTo(ExperienceProfile.TOSHIAKI_COMPAT, AppIconVariant.Current).getOrThrow()
            reconciled.clear()

            assertEquals(ExperienceProfile.TOSHIAKI_COMPAT, coordinator.recoverIfNeeded().getOrThrow())
            assertTrue(reconciled.isEmpty())
        } finally {
            defaults.removePersistentDomainForName(suiteName)
        }
    }

    @Test
    fun recoveryConvergesFromEveryPersistedSwitchPhase() = runBlocking {
        val phases = listOf(
            ModeSwitchPhase.SESSION_FLUSHED,
            ModeSwitchPhase.OLD_PROFILE_QUIESCED,
            ModeSwitchPhase.PROFILE_PERSISTED,
            ModeSwitchPhase.LAUNCHER_ALIAS_UPDATED
        )
        phases.forEach { phase ->
            val suiteName = "com.valoser.futacha.tests.recovery.${phase.name}.${NSUUID().UUIDString()}"
            val defaults = requireNotNull(NSUserDefaults(suiteName = suiteName))
            defaults.removePersistentDomainForName(suiteName)
            try {
                val store = IosExperienceProfileStore(defaults)
                var journal = store.beginSwitchWithCommitBarrier(
                    from = ExperienceProfile.FUTACHA,
                    to = ExperienceProfile.TOSHIAKI_COMPAT
                )
                if (phase >= ModeSwitchPhase.OLD_PROFILE_QUIESCED) {
                    journal = store.advanceSwitch(journal, ModeSwitchPhase.OLD_PROFILE_QUIESCED)
                }
                if (phase >= ModeSwitchPhase.PROFILE_PERSISTED) {
                    journal = store.persistRequestedProfileWithCommitBarrier(journal)
                }
                if (phase >= ModeSwitchPhase.LAUNCHER_ALIAS_UPDATED) {
                    store.advanceSwitch(journal, ModeSwitchPhase.LAUNCHER_ALIAS_UPDATED)
                }

                val recovered = IosModeSwitchCoordinator(store) { _, _ -> }.recoverIfNeeded().getOrThrow()

                assertEquals(ExperienceProfile.TOSHIAKI_COMPAT, recovered, phase.name)
                assertEquals(ExperienceProfile.TOSHIAKI_COMPAT, store.readActiveProfile(), phase.name)
                assertEquals(1L, store.readGeneration(), phase.name)
                assertNull(store.readJournal(), phase.name)
            } finally {
                defaults.removePersistentDomainForName(suiteName)
            }
        }
    }

    @Test
    fun compatibilityTabAndHistoryCleanupMatchesReferenceBoundaries() = runBlocking {
        val fileSystem = createFileSystem()
        fileSystem.deleteRecursively("compatibility").getOrThrow()
        try {
            val boardUrl = "https://may.2chan.net/b/"
            val board = CompatBoard("board", "虹裏", boardUrl, boardUrl, 0)
            val store = IosCompatibilityStore(fileSystem, nowMillis = { 1_000L })
            store.initialize()
            store.upsertBoard(board)

            suspend fun open(number: Int) {
                val threadUrl = "${boardUrl}res/$number.htm"
                val tab = CompatTab(
                    key = "tab-$number",
                    canonicalUrl = threadUrl,
                    originalUrl = threadUrl,
                    boardKey = board.key,
                    boardName = board.name,
                    threadNo = number.toString(),
                    title = "thread-$number",
                    insertedAtEpochMillis = number.toLong(),
                    contentUpdatedAtEpochMillis = number.toLong()
                )
                store.openTab(
                    tab,
                    CompatHistoryEntry(
                        canonicalUrl = threadUrl,
                        originalUrl = threadUrl,
                        boardKey = board.key,
                        boardName = board.name,
                        threadNo = tab.threadNo,
                        title = tab.title,
                        contentUpdatedAtEpochMillis = number.toLong()
                    )
                )
            }

            repeat(100) { open(it + 1) }
            assertEquals(100, store.tabs.first().size)
            assertEquals(100, store.history.first().size)
            open(101)
            assertEquals(90, store.tabs.first().size)
            assertEquals(101, store.history.first().size)
            repeat(100) { open(it + 102) }
            assertEquals(91, store.tabs.first().size)
            assertEquals(190, store.history.first().size)
        } finally {
            fileSystem.deleteRecursively("compatibility").getOrThrow()
        }
    }
}
