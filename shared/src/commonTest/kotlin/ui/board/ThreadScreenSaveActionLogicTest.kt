package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogNavEntryConfig
import com.valoser.futacha.shared.model.QuoteReference
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.SaveStatus
import com.valoser.futacha.shared.model.SavedPost
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.SavedThreadMetadata
import com.valoser.futacha.shared.model.ThreadMenuEntryId
import com.valoser.futacha.shared.model.ThreadMenuEntryConfig
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.model.defaultThreadMenuEntries
import com.valoser.futacha.shared.model.ThreadPageContent
import com.valoser.futacha.shared.network.ArchiveSearchItem
import com.valoser.futacha.shared.network.ArchiveSearchScope
import com.valoser.futacha.shared.network.NetworkException
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.service.AUTO_SAVE_DIRECTORY
import com.valoser.futacha.shared.service.DEFAULT_MANUAL_SAVE_ROOT
import com.valoser.futacha.shared.service.SavedMediaFile
import com.valoser.futacha.shared.service.SavedMediaType
import com.valoser.futacha.shared.service.ThreadSaveService
import com.valoser.futacha.shared.service.buildThreadStorageId
import com.valoser.futacha.shared.util.ImageData
import com.valoser.futacha.shared.util.SaveDirectorySelection
import io.ktor.client.HttpClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import androidx.compose.ui.unit.Density
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ThreadScreenSaveActionLogicTest {
    @Test
    fun threadMediaPreviewHelpers_reduceSelectionNavigationAndSaveRequestState() {
        val entries = listOf(
            MediaPreviewEntry(
                url = "https://may.2chan.net/b/src/1.png",
                mediaType = MediaType.Image,
                postId = "1",
                title = "one"
            ),
            MediaPreviewEntry(
                url = "https://may.2chan.net/b/src/2.webm",
                mediaType = MediaType.Video,
                postId = "2",
                title = "two"
            )
        )

        val initial = emptyThreadMediaPreviewState()
        val opened = openThreadMediaPreview(
            currentState = initial,
            entries = entries,
            url = entries[1].url,
            mediaType = MediaType.Video
        )
        assertEquals(1, opened.previewMediaIndex)
        assertEquals(entries[1], currentThreadMediaPreviewEntry(opened, entries))
        assertEquals(
            ThreadMediaPreviewDialogState(
                entry = entries[1],
                currentIndex = 1,
                totalCount = 2,
                isSaveEnabled = true,
                isSaveInProgress = false
            ),
            resolveThreadMediaPreviewDialogState(
                state = opened,
                entries = entries,
                isSaveInProgress = false
            )
        )

        val next = moveToNextThreadMediaPreview(opened, totalCount = entries.size)
        assertEquals(0, next.previewMediaIndex)

        val previous = moveToPreviousThreadMediaPreview(next, totalCount = entries.size)
        assertEquals(1, previous.previewMediaIndex)

        val normalized = normalizeThreadMediaPreviewState(
            currentState = previous,
            totalCount = 1
        )
        assertNull(normalized.previewMediaIndex)
        assertEquals(
            null,
            resolveThreadMediaPreviewNormalizationState(
                currentState = ThreadMediaPreviewState(previewMediaIndex = 0),
                totalCount = 2
            )
        )
        assertEquals(
            ThreadMediaPreviewState(previewMediaIndex = null),
            resolveThreadMediaPreviewNormalizationState(
                currentState = ThreadMediaPreviewState(previewMediaIndex = 0),
                totalCount = 0
            )
        )
        assertEquals(
            ThreadMediaPreviewState(previewMediaIndex = 0),
            resolveThreadMediaClickState(
                currentState = initial,
                entries = entries,
                url = entries[0].url,
                mediaType = MediaType.Image
            )
        )
        assertEquals(
            null,
            resolveThreadMediaClickState(
                currentState = initial,
                entries = entries,
                url = "missing",
                mediaType = MediaType.Image
            )
        )
        assertEquals(
            initial,
            openThreadMediaPreview(
                currentState = initial,
                entries = entries,
                url = "missing",
                mediaType = MediaType.Image
            )
        )
        assertNull(currentThreadMediaPreviewEntry(dismissThreadMediaPreview(opened), entries))
        assertEquals(
            ThreadMediaPreviewDialogState(
                entry = entries[1],
                currentIndex = 1,
                totalCount = 2,
                isSaveEnabled = false,
                isSaveInProgress = true
            ),
            resolveThreadMediaPreviewDialogState(
                state = opened,
                entries = entries,
                isSaveInProgress = true
            )
        )

        assertEquals(
            ThreadMediaSaveRequestState(
                canStartSave = false,
                message = buildThreadSaveBusyMessage()
            ),
            resolveThreadMediaSaveRequestState(
                isAnySaveInProgress = true,
                isRemoteMedia = true,
                requiresManualLocationSelection = false,
                hasStorageDependencies = true
            )
        )
        assertEquals(
            ThreadMediaSaveRequestState(
                canStartSave = false,
                message = buildThreadSaveLocationRequiredMessage(),
                shouldOpenSaveDirectoryPicker = true
            ),
            resolveThreadMediaSaveRequestState(
                isAnySaveInProgress = false,
                isRemoteMedia = true,
                requiresManualLocationSelection = true,
                hasStorageDependencies = true
            )
        )
        assertEquals(
            ThreadMediaSaveRequestState(canStartSave = true),
            resolveThreadMediaSaveRequestState(
                isAnySaveInProgress = false,
                isRemoteMedia = true,
                requiresManualLocationSelection = false,
                hasStorageDependencies = true
            )
        )
    }

    @Test
    fun threadAndMediaSaveAvailability_prioritizeBlockingReasons() {
        assertEquals(
            ThreadSaveAvailability.Busy,
            resolveThreadSaveAvailability(
                isAnySaveInProgress = true,
                requiresManualLocationSelection = true,
                hasStorageDependencies = false,
                isThreadReady = false
            )
        )
        assertEquals(
            ThreadSaveAvailability.LocationRequired,
            resolveThreadSaveAvailability(
                isAnySaveInProgress = false,
                requiresManualLocationSelection = true,
                hasStorageDependencies = true,
                isThreadReady = true
            )
        )
        assertEquals(
            ThreadSaveAvailability.NotReady,
            resolveThreadSaveAvailability(
                isAnySaveInProgress = false,
                requiresManualLocationSelection = false,
                hasStorageDependencies = true,
                isThreadReady = false
            )
        )
        assertEquals(
            ThreadSaveAvailability.Unavailable,
            resolveThreadSaveAvailability(
                isAnySaveInProgress = false,
                requiresManualLocationSelection = false,
                hasStorageDependencies = false,
                isThreadReady = true
            )
        )
        assertEquals(
            ThreadSaveAvailability.Ready,
            resolveThreadSaveAvailability(
                isAnySaveInProgress = false,
                requiresManualLocationSelection = false,
                hasStorageDependencies = true,
                isThreadReady = true
            )
        )

        assertEquals(
            MediaSaveAvailability.Busy,
            resolveMediaSaveAvailability(
                isAnySaveInProgress = true,
                isRemoteMedia = false,
                requiresManualLocationSelection = true,
                hasStorageDependencies = false
            )
        )
        assertEquals(
            MediaSaveAvailability.Unsupported,
            resolveMediaSaveAvailability(
                isAnySaveInProgress = false,
                isRemoteMedia = false,
                requiresManualLocationSelection = false,
                hasStorageDependencies = true
            )
        )
        assertEquals(
            MediaSaveAvailability.LocationRequired,
            resolveMediaSaveAvailability(
                isAnySaveInProgress = false,
                isRemoteMedia = true,
                requiresManualLocationSelection = true,
                hasStorageDependencies = true
            )
        )
        assertEquals(
            MediaSaveAvailability.Unavailable,
            resolveMediaSaveAvailability(
                isAnySaveInProgress = false,
                isRemoteMedia = true,
                requiresManualLocationSelection = false,
                hasStorageDependencies = false
            )
        )
        assertEquals(
            MediaSaveAvailability.Ready,
            resolveMediaSaveAvailability(
                isAnySaveInProgress = false,
                isRemoteMedia = true,
                requiresManualLocationSelection = false,
                hasStorageDependencies = true
            )
        )
    }

    @Test
    fun resolveReadAloudControlState_derivesPlaybackUiState() {
        val segments = listOf(
            ReadAloudSegment(postIndex = 0, postId = "10", body = "a"),
            ReadAloudSegment(postIndex = 1, postId = "20", body = "b"),
            ReadAloudSegment(postIndex = 2, postId = "30", body = "c")
        )

        val speaking = resolveReadAloudControlState(
            segments = segments,
            currentIndex = 1,
            visibleSegmentIndex = 2,
            status = ReadAloudStatus.Speaking(segments[1])
        )
        assertEquals(3, speaking.totalSegments)
        assertEquals(1, speaking.completedSegments)
        assertEquals("20", speaking.currentSegment?.postId)
        assertTrue(speaking.canSeek)
        assertEquals(1f, speaking.sliderValue)
        assertEquals("30", speaking.visiblePostId)
        assertTrue(speaking.canSeekToVisible)
        assertEquals("再生", speaking.playLabel)
        assertFalse(speaking.isPlayEnabled)
        assertTrue(speaking.isPauseEnabled)
        assertTrue(speaking.isStopEnabled)

        val paused = resolveReadAloudControlState(
            segments = segments,
            currentIndex = 5,
            visibleSegmentIndex = -1,
            status = ReadAloudStatus.Paused(segments[2])
        )
        assertEquals(3, paused.completedSegments)
        assertEquals(2f, paused.sliderValue)
        assertEquals("再開", paused.playLabel)
        assertTrue(paused.isPlayEnabled)
        assertFalse(paused.isPauseEnabled)
        assertTrue(paused.isStopEnabled)
        assertFalse(paused.canSeekToVisible)

        val idleEmpty = resolveReadAloudControlState(
            segments = emptyList(),
            currentIndex = 0,
            visibleSegmentIndex = 0,
            status = ReadAloudStatus.Idle
        )
        assertEquals(0, idleEmpty.totalSegments)
        assertEquals(0, idleEmpty.completedSegments)
        assertEquals(null, idleEmpty.currentSegment)
        assertFalse(idleEmpty.canSeek)
        assertEquals(0f, idleEmpty.sliderValue)
        assertEquals(null, idleEmpty.visiblePostId)
        assertFalse(idleEmpty.canSeekToVisible)
        assertFalse(idleEmpty.isPlayEnabled)
        assertFalse(idleEmpty.isPauseEnabled)
        assertFalse(idleEmpty.isStopEnabled)
    }

    @Test
    fun threadActionSheetAndSettingsHelpers_mapExpectedBehavior() {
        assertFalse(resolveThreadPostActionSheetState(isSelfPost = true).isSaidaneEnabled)
        assertTrue(resolveThreadPostActionSheetState(isSelfPost = false).isSaidaneEnabled)

        assertEquals(
            ThreadSettingsActionState(showNgManagement = true),
            resolveThreadSettingsActionState(ThreadMenuEntryId.NgManagement)
        )
        assertEquals(
            ThreadSettingsActionState(openExternalApp = true),
            resolveThreadSettingsActionState(ThreadMenuEntryId.ExternalApp)
        )
        assertEquals(
            ThreadSettingsActionState(togglePrivacy = true),
            resolveThreadSettingsActionState(ThreadMenuEntryId.Privacy)
        )
        assertEquals(
            ThreadSettingsActionState(showReadAloudControls = true),
            resolveThreadSettingsActionState(ThreadMenuEntryId.ReadAloud)
        )
        assertEquals(
            ThreadSettingsActionState(reopenSettingsSheet = true),
            resolveThreadSettingsActionState(ThreadMenuEntryId.Settings)
        )
        assertEquals(
            ThreadSettingsActionState(delegateToMainActionHandler = true),
            resolveThreadSettingsActionState(ThreadMenuEntryId.Refresh)
        )
        assertEquals(
            "https://may.2chan.net/img/res/123.htm",
            buildThreadExternalAppUrl(
                effectiveBoardUrl = "https://may.2chan.net/img/futaba.php",
                threadId = "123"
            )
        )
    }

    @Test
    fun threadMenuEntryResolvers_filterNormalizedEntriesByPlacement() {
        val entries = defaultThreadMenuEntries()

        assertEquals(
            listOf(
                ThreadMenuEntryId.Reply,
                ThreadMenuEntryId.ScrollToTop,
                ThreadMenuEntryId.ScrollToBottom,
                ThreadMenuEntryId.Refresh,
                ThreadMenuEntryId.Gallery,
                ThreadMenuEntryId.Save,
                ThreadMenuEntryId.Filter,
                ThreadMenuEntryId.Settings
            ),
            resolveThreadActionBarEntries(entries).map { it.id }
        )
        assertEquals(
            listOf(
                ThreadMenuEntryId.NgManagement,
                ThreadMenuEntryId.ExternalApp,
                ThreadMenuEntryId.ReadAloud,
                ThreadMenuEntryId.Privacy
            ),
            resolveThreadSettingsMenuEntries(entries).map { it.id }
        )
    }

    @Test
    fun threadRefreshAndLoadMessages_mapKnownCases() {
        assertEquals(
            "ネットワーク接続不可: ローカルコピーを表示しています",
            buildThreadRefreshSuccessMessage(usedOffline = true)
        )
        assertEquals(
            "スレッドを更新しました",
            buildThreadRefreshSuccessMessage(usedOffline = false)
        )
        assertEquals(
            "更新に失敗しました: スレッドが見つかりません (404)",
            buildThreadRefreshFailureMessage(IllegalStateException("x"), 404)
        )
        assertEquals(
            "更新に失敗しました: スレッドは削除済みです (410)",
            buildThreadRefreshFailureMessage(IllegalStateException("x"), 410)
        )
        assertEquals(
            "更新に失敗しました: boom",
            buildThreadRefreshFailureMessage(IllegalStateException("boom"), null)
        )
        assertEquals(
            "タイムアウト: サーバーが応答しません",
            buildThreadInitialLoadErrorMessage(IllegalStateException("request timeout"), null)
        )
        assertEquals(
            "スレッドが見つかりません (404)",
            buildThreadInitialLoadErrorMessage(IllegalStateException("x"), 404)
        )
        assertEquals(
            "スレッドは削除済みです (410)",
            buildThreadInitialLoadErrorMessage(IllegalStateException("x"), 410)
        )
        assertEquals(
            "サーバーエラー (503)",
            buildThreadInitialLoadErrorMessage(IllegalStateException("x"), 503)
        )
        assertEquals(
            "ネットワークエラー: HTTP error: reset",
            buildThreadInitialLoadErrorMessage(IllegalStateException("HTTP error: reset"), null)
        )
        assertEquals(
            "データサイズが大きすぎます",
            buildThreadInitialLoadErrorMessage(IllegalStateException("body exceeds maximum size"), null)
        )
        assertEquals(
            "スレッドを読み込めませんでした: boom",
            buildThreadInitialLoadErrorMessage(IllegalStateException("boom"), null)
        )
    }

    @Test
    fun threadSaveAndReadAloudMessages_matchUiCopy() {
        assertEquals("保存処理を実行中です…", buildThreadSaveBusyMessage())
        assertEquals("保存先が未選択です。設定からフォルダを選択してください。", buildThreadSaveLocationRequiredMessage())
        assertEquals("保存機能が利用できません", buildThreadSaveUnavailableMessage())
        assertEquals("スレッドの読み込みが完了していません", buildThreadSaveNotReadyMessage())
        assertEquals("保存先の権限が失われました。フォルダを再選択してください。", buildThreadSavePermissionLostMessage())
        assertEquals("保存に失敗しました: boom", buildThreadSaveFailureMessage(IllegalStateException("boom")))
        assertEquals("エラーが発生しました: boom", buildThreadSaveUnexpectedErrorMessage(IllegalStateException("boom")))
        assertEquals("読み上げ対象がありません", buildReadAloudNoTargetMessage())
        assertEquals("読み上げを一時停止しました", buildReadAloudPausedMessage())
        assertEquals("読み上げを完了しました", buildReadAloudCompletedMessage())
        assertEquals("読み上げを停止しました", buildReadAloudStoppedMessage())
        assertEquals(
            "読み上げ中にエラーが発生しました: boom",
            buildReadAloudFailureMessage(IllegalStateException("boom"))
        )
    }

    @Test
    fun threadManualSaveHelpers_buildSuccessAndPermissionRecoveryState() {
        val savedThread = SavedThread(
            threadId = "123",
            boardId = "may-b",
            boardName = "may/b",
            title = "title",
            storageId = null,
            thumbnailPath = null,
            savedAt = 1L,
            postCount = 10,
            imageCount = 1,
            videoCount = 0,
            totalSize = 100L,
            status = com.valoser.futacha.shared.model.SaveStatus.COMPLETED
        )

        val successState = buildThreadManualSaveSuccessState(
            savedThread = savedThread,
            manualSaveDirectory = "/tmp/futacha",
            manualSaveLocation = SaveLocation.Path("/tmp/futacha"),
            resolvedManualSaveDirectory = "/tmp/futacha"
        )

        val expectedPath = "/tmp/futacha/${buildThreadStorageId(savedThread.boardId, savedThread.threadId)}"
        assertEquals(expectedPath, successState.displayedSavePath)
        assertEquals("スレッドを保存しました: $expectedPath", successState.message)

        assertEquals(
            ThreadManualSaveErrorState(
                message = "保存先の権限が失われました。フォルダを再選択してください。",
                shouldResetManualSaveDirectory = true,
                shouldResetSaveDirectorySelection = true,
                shouldOpenDirectoryPicker = true
            ),
            resolveThreadManualSaveErrorState(
                error = IllegalStateException("cannot resolve tree uri"),
                isUnexpected = false
            )
        )
        assertEquals(
            ThreadManualSaveErrorState(
                message = "保存に失敗しました: boom"
            ),
            resolveThreadManualSaveErrorState(
                error = IllegalStateException("boom"),
                isUnexpected = false
            )
        )
        assertEquals(
            ThreadManualSaveErrorState(
                message = "エラーが発生しました: boom"
            ),
            resolveThreadManualSaveErrorState(
                error = IllegalStateException("boom"),
                isUnexpected = true
            )
        )
    }

    @Test
    fun threadManualSaveRunner_mapsSuccessAndFailureKinds() = runBlocking {
        val config = ThreadManualSaveRunnerConfig(
            threadId = "123",
            boardId = "may-b",
            boardName = "may/b",
            boardUrl = "https://may.2chan.net/b",
            title = "title",
            expiresAtLabel = "12:34",
            posts = emptyList(),
            baseSaveLocation = SaveLocation.Path("/tmp/futacha"),
            baseDirectory = "/tmp/futacha"
        )
        val savedThread = SavedThread(
            threadId = "123",
            boardId = "may-b",
            boardName = "may/b",
            title = "title",
            storageId = null,
            thumbnailPath = null,
            savedAt = 1L,
            postCount = 10,
            imageCount = 1,
            videoCount = 0,
            totalSize = 100L,
            status = com.valoser.futacha.shared.model.SaveStatus.COMPLETED
        )

        assertEquals(
            ThreadManualSaveRunResult.Success(savedThread),
            performThreadManualSave(
                config = config,
                callbacks = ThreadManualSaveRunnerCallbacks(
                    saveThread = { Result.success(savedThread) }
                )
            )
        )

        val expectedFailure = performThreadManualSave(
            config = config,
            callbacks = ThreadManualSaveRunnerCallbacks(
                saveThread = { Result.failure(IllegalStateException("fail")) }
            )
        )
        assertTrue(expectedFailure is ThreadManualSaveRunResult.Failure)
        assertEquals(false, expectedFailure.isUnexpected)
        assertEquals("fail", expectedFailure.error.message)

        val unexpectedFailure = performThreadManualSave(
            config = config,
            callbacks = ThreadManualSaveRunnerCallbacks(
                saveThread = { throw IllegalStateException("boom") }
            )
        )
        assertTrue(unexpectedFailure is ThreadManualSaveRunResult.Failure)
        assertEquals(true, unexpectedFailure.isUnexpected)
        assertEquals("boom", unexpectedFailure.error.message)
    }

    @Test
    fun threadSaveRunnerConfigBuilders_mapInputsDirectly() {
        assertEquals(
            ThreadManualSaveRunnerConfig(
                threadId = "123",
                boardId = "b",
                boardName = "may/b",
                boardUrl = "https://may.2chan.net/b",
                title = "title",
                expiresAtLabel = "12:34",
                posts = emptyList(),
                baseSaveLocation = SaveLocation.Path("/tmp/futacha"),
                baseDirectory = "/tmp/futacha"
            ),
            buildThreadManualSaveRunnerConfig(
                threadId = "123",
                boardId = "b",
                boardName = "may/b",
                boardUrl = "https://may.2chan.net/b",
                title = "title",
                expiresAtLabel = "12:34",
                posts = emptyList(),
                baseSaveLocation = SaveLocation.Path("/tmp/futacha"),
                baseDirectory = "/tmp/futacha"
            )
        )
        assertEquals(
            ThreadSingleMediaSaveRunnerConfig(
                mediaUrl = "https://may.2chan.net/b/src/1.jpg",
                boardId = "b",
                threadId = "123",
                baseSaveLocation = SaveLocation.Path("/tmp/futacha"),
                baseDirectory = "/tmp/futacha"
            ),
            buildThreadSingleMediaSaveRunnerConfig(
                mediaUrl = "https://may.2chan.net/b/src/1.jpg",
                boardId = "b",
                threadId = "123",
                baseSaveLocation = SaveLocation.Path("/tmp/futacha"),
                baseDirectory = "/tmp/futacha"
            )
        )
        assertEquals(
            ThreadAutoSaveRunnerConfig(
                threadId = "123",
                boardId = "b",
                boardName = "may/b",
                boardUrl = "https://may.2chan.net/b",
                title = "title",
                expiresAtLabel = "12:34",
                posts = emptyList(),
                previousTimestampMillis = 10L,
                attemptStartedAtMillis = 20L,
                completionTimestampMillis = 30L
            ),
            buildThreadAutoSaveRunnerConfig(
                threadId = "123",
                boardId = "b",
                boardName = "may/b",
                boardUrl = "https://may.2chan.net/b",
                title = "title",
                expiresAtLabel = "12:34",
                posts = emptyList(),
                previousTimestampMillis = 10L,
                attemptStartedAtMillis = 20L,
                completionTimestampMillis = 30L
            )
        )
    }

    @Test
    fun threadSaveRuntimeHelpers_clearOnlyMatchingTrackedJob() {
        val trackedJob = Job()
        val otherJob = Job()

        assertEquals(
            null,
            resolveTrackedJobAfterCompletion(
                trackedJob = trackedJob,
                runningJob = trackedJob
            )
        )
        assertEquals(
            trackedJob,
            resolveTrackedJobAfterCompletion(
                trackedJob = trackedJob,
                runningJob = otherJob
            )
        )
        assertEquals(
            null,
            resolveTrackedJobAfterCompletion(
                trackedJob = null,
                runningJob = otherJob
            )
        )
    }

    @Test
    fun threadSaveRuntimeHelpers_bundleSaveCallbacksAndOptionalMediaCallbacks() {
        val saveService = ThreadSaveService(
            httpClient = HttpClient(),
            fileSystem = InMemoryFileSystem()
        )

        val runtime = buildThreadSaveRuntime(saveService)

        assertSame(saveService, runtime.saveService)
        assertNotNull(runtime.manualCallbacks)
        assertNotNull(runtime.autoCallbacks)
        assertNull(
            buildOptionalThreadSingleMediaSaveRunnerCallbacks(
                httpClient = null,
                fileSystem = InMemoryFileSystem()
            )
        )
        assertNull(
            buildOptionalThreadSingleMediaSaveRunnerCallbacks(
                httpClient = HttpClient(),
                fileSystem = null
            )
        )
        assertNotNull(
            buildOptionalThreadSingleMediaSaveRunnerCallbacks(
                httpClient = HttpClient(),
                fileSystem = InMemoryFileSystem()
            )
        )
    }

    @Test
    fun threadActionRunnerHelpers_resolveBusyNoticeAndClassifyResults() = runBlocking {
        assertEquals(
            ThreadActionLaunchState(
                shouldLaunch = true,
                nextLastBusyNoticeAtMillis = 100L
            ),
            resolveThreadActionLaunchState(
                actionInProgress = false,
                lastBusyActionNoticeAtMillis = 100L,
                nowMillis = 150L,
                busyNoticeIntervalMillis = 1_000L
            )
        )
        assertEquals(
            ThreadActionLaunchState(
                shouldLaunch = false,
                nextLastBusyNoticeAtMillis = 1_500L,
                busyMessage = "処理中です…"
            ),
            resolveThreadActionLaunchState(
                actionInProgress = true,
                lastBusyActionNoticeAtMillis = 200L,
                nowMillis = 1_500L,
                busyNoticeIntervalMillis = 1_000L
            )
        )
        assertEquals(
            ThreadActionLaunchState(
                shouldLaunch = false,
                nextLastBusyNoticeAtMillis = 900L,
                busyMessage = null
            ),
            resolveThreadActionLaunchState(
                actionInProgress = true,
                lastBusyActionNoticeAtMillis = 900L,
                nowMillis = 1_200L,
                busyNoticeIntervalMillis = 1_000L
            )
        )

        assertEquals(
            ThreadActionRunResult.Success("ok"),
            performThreadAction { "ok" }
        )
        val failure = performThreadAction<String> { error("boom") }
        assertTrue(failure is ThreadActionRunResult.Failure)
        assertEquals("boom", failure.error.message)
        assertEquals("処理中です…", buildThreadActionBusyMessage())
        assertEquals(
            "Starting thread action: success='成功', failure='失敗'",
            buildThreadActionStartLogMessage("成功", "失敗")
        )
        assertEquals(
            "Thread action succeeded: 成功",
            buildThreadActionSuccessLogMessage("成功")
        )
        assertEquals(
            "Thread action failed: 失敗",
            buildThreadActionFailureLogMessage("失敗")
        )

        val busyMessages = mutableListOf<String>()
        val busyLaunchResult = launchManagedThreadAction(
            actionInProgress = true,
            lastBusyActionNoticeAtMillis = 200L,
            nowMillis = 1_500L,
            busyNoticeIntervalMillis = 1_000L,
            successMessage = "成功",
            failurePrefix = "失敗",
            callbacks = ThreadActionRuntimeCallbacks<String>(
                onActionInProgressChanged = {},
                onShowMessage = { busyMessages += it },
                onDebugLog = {},
                onInfoLog = {},
                onErrorLog = { _, _ -> }
            )
        ) {
            ThreadActionRunResult.Success("unused")
        }
        assertEquals(1_500L, busyLaunchResult.nextLastBusyNoticeAtMillis)
        assertEquals(null, busyLaunchResult.launchedJob)
        assertEquals(listOf("処理中です…"), busyMessages)

        val events = mutableListOf<String>()
        val successLaunchResult = launchManagedThreadAction(
            actionInProgress = false,
            lastBusyActionNoticeAtMillis = 100L,
            nowMillis = 150L,
            busyNoticeIntervalMillis = 1_000L,
            successMessage = "成功",
            failurePrefix = "失敗",
            callbacks = ThreadActionRuntimeCallbacks(
                onActionInProgressChanged = { events += "progress:$it" },
                onSuccess = { value: String -> events += "success:$value" },
                onShowMessage = { message -> events += "message:$message" },
                onDebugLog = { message -> events += "debug:$message" },
                onInfoLog = { message -> events += "info:$message" },
                onErrorLog = { message, _ -> events += "error:$message" }
            )
        ) {
            ThreadActionRunResult.Success("ok")
        }
        successLaunchResult.launchedJob?.join()
        assertEquals(100L, successLaunchResult.nextLastBusyNoticeAtMillis)
        assertEquals(
            listOf(
                "progress:true",
                "debug:Starting thread action: success='成功', failure='失敗'",
                "info:Thread action succeeded: 成功",
                "success:ok",
                "message:成功",
                "progress:false"
            ),
            events
        )

        events.clear()
        val failureLaunchResult = launchManagedThreadAction(
            actionInProgress = false,
            lastBusyActionNoticeAtMillis = 100L,
            nowMillis = 150L,
            busyNoticeIntervalMillis = 1_000L,
            successMessage = "成功",
            failurePrefix = "失敗",
            callbacks = ThreadActionRuntimeCallbacks<String>(
                onActionInProgressChanged = { events += "progress:$it" },
                onShowMessage = { message -> events += "message:$message" },
                onDebugLog = { message -> events += "debug:$message" },
                onInfoLog = { message -> events += "info:$message" },
                onErrorLog = { message, error -> events += "error:$message:${error.message}" }
            )
        ) {
            ThreadActionRunResult.Failure(IllegalStateException("boom"))
        }
        failureLaunchResult.launchedJob?.join()
        assertEquals(
            listOf(
                "progress:true",
                "debug:Starting thread action: success='成功', failure='失敗'",
                "error:Thread action failed: 失敗:boom",
                "message:失敗: boom",
                "progress:false"
            ),
            events
        )
    }

    @Test
    fun threadActionConfigHelpers_buildReplyDeleteAndExecuteCallbacks() = runBlocking {
        val draft = ThreadReplyDraft(
            name = "name",
            email = "sage",
            subject = "subject",
            comment = "comment",
            password = "ignored",
            imageData = ImageData(
                bytes = byteArrayOf(1, 2, 3),
                fileName = "a.jpg"
            )
        )

        assertEquals(
            ThreadDeleteByUserActionConfig(
                boardUrl = "https://may.2chan.net/b",
                threadId = "123",
                postId = "456",
                password = "pass",
                imageOnly = true
            ),
            buildThreadDeleteByUserActionConfig(
                boardUrl = "https://may.2chan.net/b",
                threadId = "123",
                postId = "456",
                password = "pass",
                imageOnly = true
            )
        )
        assertEquals(
            ThreadReplyActionConfig(
                boardUrl = "https://may.2chan.net/b",
                threadId = "123",
                name = "name",
                email = "sage",
                subject = "subject",
                comment = "comment",
                password = "trimmed",
                imageBytes = draft.imageData!!.bytes,
                imageFileName = "a.jpg",
                textOnly = false
            ),
            buildThreadReplyActionConfig(
                boardUrl = "https://may.2chan.net/b",
                threadId = "123",
                draft = draft,
                normalizedPassword = "trimmed"
            )
        )

        assertEquals(
            ThreadActionRunResult.Success(Unit),
            performThreadDeleteByUserAction(
                config = buildThreadDeleteByUserActionConfig(
                    boardUrl = "https://may.2chan.net/b",
                    threadId = "123",
                    postId = "456",
                    password = "pass",
                    imageOnly = false
                ),
                callbacks = ThreadDeleteByUserActionCallbacks(
                    deleteByUser = {}
                )
            )
        )

        assertEquals(
            ThreadActionRunResult.Success("789"),
            performThreadReplyAction(
                config = buildThreadReplyActionConfig(
                    boardUrl = "https://may.2chan.net/b",
                    threadId = "123",
                    draft = draft,
                    normalizedPassword = "trimmed"
                ),
                callbacks = ThreadReplyActionCallbacks(
                    replyToThread = { "789" }
                )
            )
        )

        val deleteFailure = performThreadDeleteByUserAction(
            config = buildThreadDeleteByUserActionConfig(
                boardUrl = "https://may.2chan.net/b",
                threadId = "123",
                postId = "456",
                password = "pass",
                imageOnly = false
            ),
            callbacks = ThreadDeleteByUserActionCallbacks(
                deleteByUser = { error("delete failed") }
            )
        )
        assertTrue(deleteFailure is ThreadActionRunResult.Failure)
        assertEquals("delete failed", deleteFailure.error.message)
    }

    @Test
    fun threadSaveUiOutcomeHelpers_resolveManualSingleAndAutoApplyStates() {
        val savedThread = SavedThread(
            threadId = "123",
            boardId = "b",
            boardName = "may/b",
            title = "title",
            storageId = "b__123",
            thumbnailPath = null,
            savedAt = 1L,
            postCount = 0,
            imageCount = 0,
            videoCount = 0,
            totalSize = 10L,
            status = com.valoser.futacha.shared.model.SaveStatus.COMPLETED
        )

        val manualOutcome = resolveThreadManualSaveUiOutcome(
            saveResult = ThreadManualSaveRunResult.Success(savedThread),
            threadId = "123",
            manualSaveDirectory = "/tmp/futacha",
            manualSaveLocation = SaveLocation.Path("/tmp/futacha"),
            resolvedManualSaveDirectory = "/tmp/futacha"
        )
        assertTrue(manualOutcome is ThreadManualSaveUiOutcome.Success)
        assertEquals("Failed to index manually saved thread 123", manualOutcome.indexFailureMessage)
        assertEquals(
            "スレッドを保存しました: /tmp/futacha/b__123",
            manualOutcome.successState.message
        )
        assertEquals(
            "スレッドを保存しましたが、保存一覧に反映できませんでした: /tmp/futacha/b__123",
            buildThreadManualSaveIndexWarningMessage("/tmp/futacha/b__123")
        )
        assertEquals(
            "スレッドを保存しました: /tmp/futacha/b__123",
            resolveThreadManualSaveCompletionMessage(
                successState = manualOutcome.successState,
                indexResult = Result.success(Unit)
            )
        )
        assertEquals(
            "スレッドを保存しましたが、保存一覧に反映できませんでした: /tmp/futacha/b__123",
            resolveThreadManualSaveCompletionMessage(
                successState = manualOutcome.successState,
                indexResult = Result.failure(IllegalStateException("index failed"))
            )
        )

        val singleOutcome = resolveThreadSingleMediaSaveUiOutcome(
            saveResult = ThreadSingleMediaSaveRunResult.Success(
                SavedMediaFile(
                    fileName = "a.jpg",
                    relativePath = "b__123/images/a.jpg",
                    mediaType = SavedMediaType.IMAGE,
                    byteSize = 10L,
                    savedAtEpochMillis = 1L
                )
            ),
            manualSaveDirectory = "/tmp/futacha",
            manualSaveLocation = SaveLocation.Path("/tmp/futacha"),
            resolvedManualSaveDirectory = "/tmp/futacha"
        )
        assertTrue(singleOutcome is ThreadSingleMediaSaveUiOutcome.Success)
        assertEquals("画像を保存しました: /tmp/futacha/b__123/images/a.jpg", singleOutcome.successState.message)

        val autoApplyState = buildThreadAutoSaveUiApplyState(
            completionState = ThreadAutoSaveCompletionState(
                nextTimestampMillis = 50L,
                savedThread = savedThread
            ),
            threadId = "123"
        )
        assertEquals(50L, autoApplyState.nextTimestampMillis)
        assertEquals(savedThread, autoApplyState.savedThread)
        assertEquals("Failed to index auto-saved thread 123", autoApplyState.indexFailureMessage)
    }

    @Test
    fun threadSingleMediaSaveHelpers_buildSuccessAndReuseRecoveryState() {
        val savedMedia = SavedMediaFile(
            fileName = "a.jpg",
            relativePath = "board__123/images/a.jpg",
            mediaType = SavedMediaType.IMAGE,
            byteSize = 10L,
            savedAtEpochMillis = 1L
        )

        val successState = buildThreadSingleMediaSaveSuccessState(
            savedMedia = savedMedia,
            manualSaveDirectory = "/tmp/futacha",
            manualSaveLocation = SaveLocation.Path("/tmp/futacha"),
            resolvedManualSaveDirectory = "/tmp/futacha"
        )

        assertEquals("画像", successState.mediaLabel)
        assertEquals("/tmp/futacha/board__123/images/a.jpg", successState.displayedSavePath)
        assertEquals("画像を保存しました: /tmp/futacha/board__123/images/a.jpg", successState.message)
        assertEquals(
            resolveThreadManualSaveErrorState(
                error = IllegalStateException("cannot resolve tree uri"),
                isUnexpected = true
            ),
            resolveThreadSingleMediaSaveErrorState(
                error = IllegalStateException("cannot resolve tree uri"),
                isUnexpected = true
            )
        )
    }

    @Test
    fun threadSingleMediaSaveRunner_mapsSuccessAndFailureKinds() = runBlocking {
        val config = ThreadSingleMediaSaveRunnerConfig(
            mediaUrl = "https://may.2chan.net/b/src/1.jpg",
            boardId = "b",
            threadId = "123",
            baseSaveLocation = SaveLocation.Path("/tmp/futacha"),
            baseDirectory = "/tmp/futacha"
        )
        val savedMedia = SavedMediaFile(
            fileName = "a.jpg",
            relativePath = "board__123/images/a.jpg",
            mediaType = SavedMediaType.IMAGE,
            byteSize = 10L,
            savedAtEpochMillis = 1L
        )

        assertEquals(
            ThreadSingleMediaSaveRunResult.Success(savedMedia),
            performThreadSingleMediaSave(
                config = config,
                callbacks = ThreadSingleMediaSaveRunnerCallbacks(
                    saveMedia = { Result.success(savedMedia) }
                )
            )
        )
        val expectedFailure = performThreadSingleMediaSave(
            config = config,
            callbacks = ThreadSingleMediaSaveRunnerCallbacks(
                saveMedia = { Result.failure(IllegalStateException("fail")) }
            )
        )
        assertTrue(expectedFailure is ThreadSingleMediaSaveRunResult.Failure)
        assertEquals(false, expectedFailure.isUnexpected)
        assertEquals("fail", expectedFailure.error.message)

        val unexpectedFailure = performThreadSingleMediaSave(
            config = config,
            callbacks = ThreadSingleMediaSaveRunnerCallbacks(
                saveMedia = { throw IllegalStateException("boom") }
            )
        )
        assertTrue(unexpectedFailure is ThreadSingleMediaSaveRunResult.Failure)
        assertEquals(true, unexpectedFailure.isUnexpected)
        assertEquals("boom", unexpectedFailure.error.message)
    }

    @Test
    fun threadSavePreconditions_andPermissionIssueDetection_workAsExpected() {
        assertTrue(requiresThreadManualSaveLocationSelection(isAndroidPlatform = true, manualSaveLocation = null))
        assertFalse(
            requiresThreadManualSaveLocationSelection(
                isAndroidPlatform = true,
                manualSaveLocation = SaveLocation.Path("/tmp")
            )
        )
        assertFalse(
            requiresThreadManualSaveLocationSelection(
                isAndroidPlatform = true,
                manualSaveLocation = SaveLocation.TreeUri("content://tree")
            )
        )
        assertFalse(
            requiresThreadManualSaveLocationSelection(
                isAndroidPlatform = false,
                manualSaveLocation = null
            )
        )
        assertTrue(isThreadSaveLocationPermissionIssue(IllegalStateException("cannot resolve tree uri")))
        assertTrue(isThreadSaveLocationPermissionIssue(IllegalStateException("invalid bookmark data")))
        assertFalse(isThreadSaveLocationPermissionIssue(IllegalStateException("other error")))
    }

    @Test
    fun threadActionAndValidationMessages_matchUiCopy() {
        assertEquals(
            "返信の送信に失敗しました: boom",
            buildThreadActionFailureMessage("返信の送信に失敗しました", IllegalStateException("boom"))
        )
        assertEquals(
            "返信の送信に失敗しました",
            buildThreadActionFailureMessage("返信の送信に失敗しました", IllegalStateException(""))
        )
        assertEquals("自分のレスにはそうだねできません", buildSelfSaidaneBlockedMessage())
        assertEquals("IDが見つかりませんでした", buildMissingPosterIdMessage())
        assertEquals("削除キーを入力してください", buildDeletePasswordRequiredMessage())
        assertEquals("コメントを入力してください", buildReplyCommentRequiredMessage())
        assertEquals("履歴を更新しました", buildThreadHistoryRefreshSuccessMessage())
        assertEquals("履歴更新はすでに実行中です", buildThreadHistoryRefreshAlreadyRunningMessage())
        assertEquals(
            "履歴の更新に失敗しました: boom",
            buildThreadHistoryRefreshFailureMessage(IllegalStateException("boom"))
        )
        assertEquals("履歴を一括削除しました", buildThreadHistoryBatchDeleteMessage())
    }

    @Test
    fun threadDeleteAndReplyValidation_enforcesRequiredFields() {
        assertEquals(
            "削除キーを入力してください",
            validateThreadDeletePassword("   ")
        )
        assertEquals(null, validateThreadDeletePassword("pass"))

        assertEquals(
            "削除キーを入力してください",
            validateThreadReplyForm(password = " ", comment = "body")
        )
        assertEquals(
            "コメントを入力してください",
            validateThreadReplyForm(password = "pass", comment = "   ")
        )
        assertEquals(
            null,
            validateThreadReplyForm(password = "pass", comment = "body")
        )
    }

    @Test
    fun updateThreadFilterSelection_enforcesSingleSortOption() {
        val withSaidane = updateThreadFilterSelection(
            selectedOptions = setOf(ThreadFilterOption.Url),
            selectedSortOption = null,
            toggledOption = ThreadFilterOption.HighSaidane
        )
        assertEquals(
            setOf(ThreadFilterOption.Url, ThreadFilterOption.HighSaidane),
            withSaidane.selectedOptions
        )
        assertEquals(ThreadFilterSortOption.Saidane, withSaidane.selectedSortOption)

        val withReplies = updateThreadFilterSelection(
            selectedOptions = withSaidane.selectedOptions,
            selectedSortOption = withSaidane.selectedSortOption,
            toggledOption = ThreadFilterOption.HighReplies
        )
        assertEquals(
            setOf(ThreadFilterOption.Url, ThreadFilterOption.HighReplies),
            withReplies.selectedOptions
        )
        assertEquals(ThreadFilterSortOption.Replies, withReplies.selectedSortOption)

        val withoutReplies = updateThreadFilterSelection(
            selectedOptions = withReplies.selectedOptions,
            selectedSortOption = withReplies.selectedSortOption,
            toggledOption = ThreadFilterOption.HighReplies
        )
        assertEquals(setOf(ThreadFilterOption.Url), withoutReplies.selectedOptions)
        assertEquals(null, withoutReplies.selectedSortOption)
    }

    @Test
    fun threadFilterHelpers_matchExpectedPostsAndSorts() {
        val posts = listOf(
            Post(
                id = "1",
                author = "Alice",
                subject = "猫",
                timestamp = "24/01/01(月)00:00:00",
                posterId = "ID:a",
                messageHtml = "https://example.com 猫",
                imageUrl = "https://img/1.jpg",
                thumbnailUrl = "https://img/1s.jpg",
                saidaneLabel = "そうだね 5",
                referencedCount = 1
            ),
            Post(
                id = "2",
                author = "Bob",
                subject = "犬",
                timestamp = "24/01/01(月)00:00:00",
                posterId = "ID:b",
                messageHtml = "本文",
                imageUrl = null,
                thumbnailUrl = null,
                isDeleted = true,
                saidaneLabel = "そうだね 1",
                referencedCount = 9
            ),
            Post(
                id = "3",
                author = "Carol",
                subject = "鳥",
                timestamp = "24/01/01(月)00:00:00",
                posterId = "ID:c",
                messageHtml = "猫だけ",
                imageUrl = null,
                thumbnailUrl = null,
                saidaneLabel = "そうだね 9",
                referencedCount = 3
            )
        )
        val page = ThreadPage(
            threadId = "100",
            boardTitle = "board",
            expiresAtLabel = null,
            deletedNotice = null,
            posts = posts
        )

        val urlFiltered = applyThreadFilters(
            page = page,
            criteria = ThreadFilterCriteria(
                options = setOf(ThreadFilterOption.Url),
                keyword = "",
                selfPostIdentifiers = emptyList(),
                sortOption = null
            )
        )
        assertEquals(listOf("1"), urlFiltered.posts.map { it.id })

        val keywordFiltered = applyThreadFilters(
            page = page,
            criteria = ThreadFilterCriteria(
                options = setOf(ThreadFilterOption.Keyword),
                keyword = "猫",
                selfPostIdentifiers = emptyList(),
                sortOption = null
            )
        )
        assertEquals(listOf("1", "3"), keywordFiltered.posts.map { it.id })

        val selfFiltered = applyThreadFilters(
            page = page,
            criteria = ThreadFilterCriteria(
                options = setOf(ThreadFilterOption.SelfPosts),
                keyword = "",
                selfPostIdentifiers = listOf("2"),
                sortOption = null
            )
        )
        assertEquals(listOf("2"), selfFiltered.posts.map { it.id })

        val deletedFiltered = applyThreadFilters(
            page = page,
            criteria = ThreadFilterCriteria(
                options = setOf(ThreadFilterOption.Deleted),
                keyword = "",
                selfPostIdentifiers = emptyList(),
                sortOption = null
            )
        )
        assertEquals(listOf("2"), deletedFiltered.posts.map { it.id })

        val sortedBySaidane = sortThreadPosts(posts, ThreadFilterSortOption.Saidane)
        assertEquals(listOf("3", "1", "2"), sortedBySaidane.map { it.id })

        val sortedByReplies = sortThreadPosts(posts, ThreadFilterSortOption.Replies)
        assertEquals(listOf("2", "3", "1"), sortedByReplies.map { it.id })
    }

    @Test
    fun threadFilterPrimitiveHelpers_coverKeywordsHeadersAndCounts() {
        assertTrue(matchesKeyword(lowerText = "本文 猫", subject = "犬", keywordInput = "猫"))
        assertTrue(matchesKeyword(lowerText = "本文", subject = "猫 subject", keywordInput = "猫"))
        assertFalse(matchesKeyword(lowerText = "本文", subject = "犬", keywordInput = "猫"))

        val post = Post(
            id = "12",
            author = "Alice",
            subject = "件名",
            timestamp = "24/01/01(月)00:00:00",
            posterId = "ID:abc",
            messageHtml = "www.example.com",
            imageUrl = null,
            thumbnailUrl = null
        )
        assertTrue(buildPostHeaderText(post).contains("alice"))
        assertTrue(THREAD_FILTER_URL_REGEX.containsMatchIn("go to https://example.com"))
        assertEquals(42, parseSaidaneCount("そうだね 42"))
        assertEquals(null, parseSaidaneCount("なし"))
        assertTrue(matchesSelfFilter(post.copy(id = "99"), setOf("99")))
        assertFalse(matchesSelfFilter(post.copy(id = "98"), setOf("99")))
        assertEquals(
            mapOf("12" to "www.example.com"),
            buildLowerBodyByPostId(listOf(post))
        )
    }

    @Test
    fun quoteSelectionHelpers_buildDefaultsAndAppendQuotedLines() {
        val post = Post(
            id = "12",
            author = "Alice",
            subject = "件名",
            timestamp = "24/01/01(月)00:00:00",
            posterId = "ID:abc",
            messageHtml = "一行目<br>二行目",
            imageUrl = "https://may.2chan.net/b/src/abc123.png",
            thumbnailUrl = null
        )

        val items = buildQuoteSelectionItems(post)

        assertEquals(
            listOf("number-12", "file-12", "line-0", "line-1"),
            items.map { it.id }
        )
        assertEquals(setOf("number-12"), defaultQuoteSelectionIds(items))
        assertEquals(
            "既存\n>No.12\n>一行目\n",
            appendSelectedQuoteLines("既存", listOf(">No.12", ">一行目"))
        )
        assertEquals(
            ">No.12\n",
            appendSelectedQuoteLines("", listOf(">No.12"))
        )
        assertEquals(
            null,
            appendSelectedQuoteLines("既存", emptyList())
        )
    }

    @Test
    fun ngFilterHelpers_filterHeadersAndWords_andHonorDisabledFlag() {
        val posts = listOf(
            Post(
                id = "1",
                author = "Alice",
                subject = "件名",
                timestamp = "24/01/01(月)00:00:00",
                posterId = "ID:abc",
                messageHtml = "本文 猫",
                imageUrl = null,
                thumbnailUrl = null
            ),
            Post(
                id = "2",
                author = "Bob",
                subject = "別件",
                timestamp = "24/01/01(月)00:00:00",
                posterId = "ID:def",
                messageHtml = "安全",
                imageUrl = null,
                thumbnailUrl = null
            )
        )
        val page = ThreadPage(
            threadId = "100",
            boardTitle = "board",
            expiresAtLabel = null,
            deletedNotice = null,
            posts = posts
        )

        val lowerBodies = buildLowerBodyByPostId(posts)
        assertTrue(
            matchesNgFilters(
                post = posts[0],
                headerFilters = listOf("alice"),
                wordFilters = emptyList(),
                lowerBodyByPostId = lowerBodies
            )
        )
        assertTrue(
            matchesNgFilters(
                post = posts[0],
                headerFilters = emptyList(),
                wordFilters = listOf("猫"),
                lowerBodyByPostId = lowerBodies
            )
        )
        assertFalse(
            matchesNgFilters(
                post = posts[1],
                headerFilters = listOf("alice"),
                wordFilters = listOf("猫"),
                lowerBodyByPostId = lowerBodies
            )
        )

        val filtered = applyNgFilters(
            page = page,
            ngHeaders = listOf("alice"),
            ngWords = listOf("猫"),
            enabled = true,
            precomputedLowerBodyByPostId = lowerBodies
        )
        assertEquals(listOf("2"), filtered.posts.map { it.id })

        val unfiltered = applyNgFilters(
            page = page,
            ngHeaders = listOf("alice"),
            ngWords = listOf("猫"),
            enabled = false,
            precomputedLowerBodyByPostId = lowerBodies
        )
        assertEquals(listOf("1", "2"), unfiltered.posts.map { it.id })
    }

    @Test
    fun stableFingerprints_normalizeCaseWhitespaceAndOptionOrder() {
        assertEquals(
            stableNormalizedListFingerprint(listOf(" Alice ", "BOB")),
            stableNormalizedListFingerprint(listOf("alice", " bob "))
        )
        assertEquals(
            stableThreadFilterOptionSetFingerprint(setOf(ThreadFilterOption.Url, ThreadFilterOption.Keyword)),
            stableThreadFilterOptionSetFingerprint(setOf(ThreadFilterOption.Keyword, ThreadFilterOption.Url))
        )
        assertFalse(
            stableThreadFilterOptionSetFingerprint(setOf(ThreadFilterOption.Url)) ==
                stableThreadFilterOptionSetFingerprint(setOf(ThreadFilterOption.Keyword))
        )
    }
}
