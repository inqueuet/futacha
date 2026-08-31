package com.valoser.futacha

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageResult
import coil3.request.SuccessResult
import coil3.size.Size
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.compat.CompatTab
import com.valoser.futacha.shared.compat.CompatThreadSnapshot
import com.valoser.futacha.shared.compat.canonicalizeThreadUrl
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.compat.compatTabKey
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogFetchSettings
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.compat.CompatibilityApp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean

class CompatViewerGestureInstrumentedTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "compat_viewer_gesture_${System.currentTimeMillis()}.db"
    private lateinit var store: AndroidCompatibilityStore
    private var testImageLoader: ImageLoader? = null
    private var testHttpClient: HttpClient? = null
    private val boardUrl = "https://may.2chan.net/b/"
    private val threadUrl = "https://may.2chan.net/b/res/1234567890.htm"

    @Before
    fun prepareStore() {
        runBlocking {
            val canonicalThread = canonicalizeThreadUrl(threadUrl) ?: error("canonical thread")
            val boardKey = compatBoardKey(boardUrl)
            val tabKey = compatTabKey(canonicalThread.canonicalUrl)
            store = AndroidCompatibilityStore(context, databaseName = databaseName)
            store.initialize()
            store.savePreference("compat.commonUsedVersion", "1.0")
            store.upsertBoard(CompatBoard(boardKey, "may/b", boardUrl, boardUrl, 0))
            store.openTab(
                CompatTab(
                    key = tabKey,
                    canonicalUrl = canonicalThread.canonicalUrl,
                    originalUrl = threadUrl,
                    boardKey = boardKey,
                    boardName = "may/b",
                    threadNo = "1234567890",
                    title = "Viewer gesture",
                    replyCount = 2,
                    insertedAtEpochMillis = 1L,
                    contentUpdatedAtEpochMillis = 1L,
                    snapshotRevision = 1L
                )
            )
            store.saveThreadSnapshot(
                CompatThreadSnapshot(
                    tabKey = tabKey,
                    revision = 1L,
                    fetchedAtEpochMillis = 1L,
                    posts = listOf(
                        post(0, "1", "https://example.invalid/1.jpg"),
                        post(1, "2", "https://example.invalid/2.jpg")
                    )
                )
            )
        }
    }

    @After
    fun closeStore() {
        testHttpClient?.close()
        testImageLoader?.shutdown()
        if (::store.isInitialized) runBlocking { store.closeForTest() }
        context.deleteDatabase(databaseName)
    }

    @Test
    fun catalogReloadRetriesAVisibleImageThatPreviouslyFailed() {
        val imageUrl = "test://compat-catalog/retry"
        val imageRequests = AtomicInteger(0)
        val interceptor = object : Interceptor {
            override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
                if (chain.request.data.toString() != imageUrl) return chain.proceed()
                return if (imageRequests.incrementAndGet() == 1) {
                    ErrorResult(
                        image = null,
                        request = chain.request,
                        throwable = IllegalStateException("intentional first request failure")
                    )
                } else {
                    SuccessResult(
                        image = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888).apply {
                            eraseColor(android.graphics.Color.GREEN)
                        }.asImage(),
                        request = chain.request,
                        dataSource = DataSource.MEMORY
                    )
                }
            }
        }
        testImageLoader = ImageLoader.Builder(context).components { add(interceptor) }.build()
        val catalogRequests = AtomicInteger(0)
        val repository = object : BoardRepository by FakeBoardRepository() {
            override suspend fun getCatalogWithSettings(
                board: String,
                mode: CatalogMode,
                settings: CatalogFetchSettings
            ): List<CatalogItem> {
                catalogRequests.incrementAndGet()
                return listOf(
                    CatalogItem(
                        id = "retry-image",
                        threadUrl = "${boardUrl}res/987.htm",
                        title = "RETRY-IMAGE",
                        thumbnailUrl = imageUrl,
                        fullImageUrl = null,
                        replyCount = 1
                    )
                )
            }
        }
        rule.setContent {
            MaterialTheme {
                CompatibilityApp(
                    store = store,
                    repository = repository,
                    imageLoader = testImageLoader,
                    catalogImageLoader = testImageLoader,
                    onExitApplication = {}
                )
            }
        }

        rule.onNodeWithText(boardUrl).performClick()
        rule.waitUntil(5_000) { imageRequests.get() == 1 }

        rule.onNodeWithContentDescription("リロード").performClick()
        rule.waitUntil(5_000) { catalogRequests.get() >= 2 && imageRequests.get() >= 2 }
        rule.onNodeWithTag("compat-catalog-image-retry-image", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun threadThumbnailRetriesTransientFailuresBeforeOriginalFallback() {
        val sourceUrl = "test://compat-thread/source"
        val thumbnailUrl = "test://compat-thread/thumbnail"
        runBlocking {
            val tabKey = compatTabKey(canonicalizeThreadUrl(threadUrl)!!.canonicalUrl)
            store.saveThreadSnapshot(
                CompatThreadSnapshot(
                    tabKey = tabKey,
                    revision = 2L,
                    fetchedAtEpochMillis = 2L,
                    posts = listOf(postWithThumbnail(0, "1", sourceUrl, thumbnailUrl))
                )
            )
        }
        val thumbnailRequests = AtomicInteger(0)
        val sourceRequests = AtomicInteger(0)
        val interceptor = object : Interceptor {
            override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
                return when (chain.request.data.toString()) {
                    thumbnailUrl -> if (thumbnailRequests.incrementAndGet() < 3) {
                        ErrorResult(
                            image = null,
                            request = chain.request,
                            throwable = IllegalStateException("intentional transient thumbnail failure")
                        )
                    } else {
                        SuccessResult(
                            image = bitmap(android.graphics.Color.GREEN, 24, 24).asImage(),
                            request = chain.request,
                            dataSource = DataSource.MEMORY
                        )
                    }

                    sourceUrl -> {
                        sourceRequests.incrementAndGet()
                        SuccessResult(
                            image = bitmap(android.graphics.Color.YELLOW, 48, 48).asImage(),
                            request = chain.request,
                            dataSource = DataSource.MEMORY
                        )
                    }

                    else -> chain.proceed()
                }
            }
        }
        testImageLoader = ImageLoader.Builder(context).components { add(interceptor) }.build()
        rule.setContent {
            MaterialTheme {
                CompatibilityApp(
                    store = store,
                    repository = null,
                    imageLoader = testImageLoader,
                    initialThreadDeepLink = threadUrl,
                    onExitApplication = {}
                )
            }
        }

        rule.waitUntil(10_000) {
            thumbnailRequests.get() == 3 &&
                rule.onAllNodesWithTag("compat-thread-thumbnail-1-ready", useUnmergedTree = true)
                    .fetchSemanticsNodes(atLeastOneRootRequired = false)
                    .isNotEmpty()
        }
        check(sourceRequests.get() == 0) {
            "Original image was requested before thumbnail retries were exhausted"
        }
    }

    @Test
    fun apuSmallImageUsesCachedSourceWithoutProbingDerivedThumbnail() {
        val sourceUrl = "https://dec.2chan.net/up2/src/fu7189334.png"
        val derivedThumbnailUrl = "https://dec.2chan.net/up2/thumb/fu7189334s.jpg"
        runBlocking {
            store.savePreference("compat.thread.threadUpsThumbMethod", "表示する")
            val tabKey = compatTabKey(canonicalizeThreadUrl(threadUrl)!!.canonicalUrl)
            store.saveThreadSnapshot(
                CompatThreadSnapshot(
                    tabKey = tabKey,
                    revision = 2L,
                    fetchedAtEpochMillis = 2L,
                    posts = listOf(postWithThumbnail(0, "1", sourceUrl, derivedThumbnailUrl))
                )
            )
        }
        val sourceRequests = AtomicInteger(0)
        val derivedThumbnailRequests = AtomicInteger(0)
        val interceptor = object : Interceptor {
            override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
                return when (chain.request.data.toString()) {
                    sourceUrl -> {
                        sourceRequests.incrementAndGet()
                        SuccessResult(
                            image = bitmap(android.graphics.Color.CYAN, 96, 48).asImage(),
                            request = chain.request,
                            dataSource = DataSource.MEMORY
                        )
                    }
                    derivedThumbnailUrl -> {
                        derivedThumbnailRequests.incrementAndGet()
                        ErrorResult(
                            image = null,
                            request = chain.request,
                            throwable = IllegalStateException("derived thumbnail must not be requested")
                        )
                    }
                    else -> chain.proceed()
                }
            }
        }
        testImageLoader = ImageLoader.Builder(context).components { add(interceptor) }.build()
        rule.setContent {
            MaterialTheme {
                CompatibilityApp(
                    store = store,
                    repository = null,
                    imageLoader = testImageLoader,
                    initialThreadDeepLink = threadUrl,
                    onExitApplication = {}
                )
            }
        }

        rule.waitUntil(10_000) {
            sourceRequests.get() == 1 &&
                rule.onAllNodesWithTag("compat-thread-thumbnail-1-ready", useUnmergedTree = true)
                    .fetchSemanticsNodes(atLeastOneRootRequired = false)
                    .isNotEmpty()
        }
        check(derivedThumbnailRequests.get() == 0) {
            "Apu-small image probed a derived thumbnail before its cached source"
        }
    }

    @Test
    fun galleryReopenDoesNotRepeatStaticPngApngProbe() {
        val sourceUrl = "https://dec.2chan.net/up2/src/fu7189334.png"
        runBlocking {
            val tabKey = compatTabKey(canonicalizeThreadUrl(threadUrl)!!.canonicalUrl)
            store.saveThreadSnapshot(
                CompatThreadSnapshot(
                    tabKey = tabKey,
                    revision = 2L,
                    fetchedAtEpochMillis = 2L,
                    posts = listOf(post(0, "1", sourceUrl))
                )
            )
        }
        val apngProbeRequests = AtomicInteger(0)
        val unexpectedSecondProbe = CompletableDeferred<Unit>()
        val staticPngPrefix = byteArrayOf(
            0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
            0, 0, 0, 0,
            'I'.code.toByte(), 'D'.code.toByte(), 'A'.code.toByte(), 'T'.code.toByte(),
            0, 0, 0, 0
        )
        testHttpClient = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    if (request.url.toString() == sourceUrl) {
                        val count = apngProbeRequests.incrementAndGet()
                        if (count > 1) unexpectedSecondProbe.complete(Unit)
                        check(request.headers[HttpHeaders.Range] == "bytes=0-1048575") {
                            "APNG marker request was not bounded: ${request.headers[HttpHeaders.Range]}"
                        }
                        respond(
                            content = ByteReadChannel(staticPngPrefix),
                            status = HttpStatusCode.PartialContent,
                            headers = headersOf(HttpHeaders.ContentType, "image/png")
                        )
                    } else {
                        respond("", HttpStatusCode.NotFound)
                    }
                }
            }
        }
        val interceptor = object : Interceptor {
            override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
                if (chain.request.data.toString() != sourceUrl) return chain.proceed()
                return SuccessResult(
                    image = bitmap(android.graphics.Color.CYAN, 96, 48).asImage(),
                    request = chain.request,
                    dataSource = DataSource.MEMORY
                )
            }
        }
        testImageLoader = ImageLoader.Builder(context).components { add(interceptor) }.build()
        rule.setContent {
            MaterialTheme {
                CompatibilityApp(
                    store = store,
                    repository = null,
                    httpClient = testHttpClient,
                    imageLoader = testImageLoader,
                    initialThreadDeepLink = threadUrl,
                    onExitApplication = {}
                )
            }
        }

        rule.waitUntil(10_000) {
            rule.onAllNodesWithContentDescription("画像一覧")
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        rule.onNodeWithContentDescription("画像一覧").performClick()
        rule.waitUntil(5_000) { apngProbeRequests.get() == 1 }
        rule.onNodeWithTag("compat-gallery-item-1").assertIsDisplayed()

        androidx.test.espresso.Espresso.pressBack()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithContentDescription("画像一覧")
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        rule.onNodeWithContentDescription("画像一覧").performClick()
        rule.onNodeWithTag("compat-gallery-item-1").assertIsDisplayed()
        rule.waitForIdle()

        val repeated = runBlocking {
            withTimeoutOrNull(750) { unexpectedSecondProbe.await() }
        }
        check(repeated == null && apngProbeRequests.get() == 1) {
            "Static PNG APNG probe repeated after gallery reopen: ${apngProbeRequests.get()} requests"
        }
    }

    @Test
    fun delayedThreadThumbnailShowsProgressInsteadOfBlankSpace() {
        val imageUrl = "test://compat-thread/delayed"
        runBlocking {
            val tabKey = compatTabKey(canonicalizeThreadUrl(threadUrl)!!.canonicalUrl)
            store.saveThreadSnapshot(
                CompatThreadSnapshot(
                    tabKey = tabKey,
                    revision = 2L,
                    fetchedAtEpochMillis = 2L,
                    posts = listOf(postWithThumbnail(0, "1", imageUrl, imageUrl))
                )
            )
        }
        val requestStarted = CompletableDeferred<Unit>()
        val releaseRequest = CompletableDeferred<Unit>()
        val interceptor = object : Interceptor {
            override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
                if (chain.request.data.toString() != imageUrl) return chain.proceed()
                requestStarted.complete(Unit)
                releaseRequest.await()
                return SuccessResult(
                    image = bitmap(android.graphics.Color.MAGENTA, 24, 24).asImage(),
                    request = chain.request,
                    dataSource = DataSource.MEMORY
                )
            }
        }
        testImageLoader = ImageLoader.Builder(context).components { add(interceptor) }.build()
        rule.setContent {
            MaterialTheme {
                CompatibilityApp(
                    store = store,
                    repository = null,
                    imageLoader = testImageLoader,
                    initialThreadDeepLink = threadUrl,
                    onExitApplication = {}
                )
            }
        }

        runBlocking { requestStarted.await() }
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag(
                "compat-thread-thumbnail-1-placeholder",
                useUnmergedTree = true
            ).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        rule.onNodeWithTag("compat-thread-thumbnail-1-placeholder", useUnmergedTree = true)
            .assertIsDisplayed()
        releaseRequest.complete(Unit)
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("compat-thread-thumbnail-1-ready", useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
    }

    @Test
    fun deepLinkIsAcknowledgedAfterThreadOpensAndDefersChangeLog() {
        runBlocking { store.savePreference("compat.commonUsedVersion", "0.9") }
        val consumed = AtomicBoolean(false)
        val activeDeepLink = mutableStateOf<String?>(threadUrl)
        rule.setContent {
            MaterialTheme {
                CompatibilityApp(
                    store = store,
                    repository = null,
                    appVersion = "1.0",
                    imageLoader = testImageLoader,
                    initialThreadDeepLink = activeDeepLink.value,
                    onThreadDeepLinkConsumed = {
                        consumed.set(true)
                        activeDeepLink.value = null
                    },
                    onExitApplication = {}
                )
            }
        }

        rule.waitUntil(10_000) {
            consumed.get() && rule.onAllNodesWithTag("compat-thread-post-1", useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        rule.onAllNodesWithText("更新履歴")
            .assertCountEquals(0)

        androidx.test.espresso.Espresso.pressBack()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("更新履歴")
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        rule.onNodeWithText("更新履歴").assertIsDisplayed()
    }

    @Test
    fun profileSwitchApprovedDeepLinkAddsBoardWithoutASecondDialog() {
        val approvedUrl = "https://img.2chan.net/b/res/987654321.htm"
        val consumed = AtomicBoolean(false)
        val activeDeepLink = mutableStateOf<String?>(approvedUrl)
        rule.setContent {
            MaterialTheme {
                CompatibilityApp(
                    store = store,
                    repository = null,
                    imageLoader = testImageLoader,
                    initialThreadDeepLink = activeDeepLink.value,
                    initialThreadDeepLinkPreapprovedBoardRegistration = true,
                    onThreadDeepLinkConsumed = {
                        consumed.set(true)
                        activeDeepLink.value = null
                    },
                    onExitApplication = {}
                )
            }
        }

        rule.waitUntil(10_000) { consumed.get() }
        rule.onAllNodesWithText("未登録の板").assertCountEquals(0)
        check(
            runBlocking {
                store.boards.first().any { it.canonicalUrl == "https://img.2chan.net/b/" }
            }
        ) { "Preapproved deep link did not persist its board" }
    }

    @Test
    fun viewerBackRestoresCachedThreadWithoutNetworkRefresh() {
        val threadRequests = AtomicInteger(0)
        val repository = object : BoardRepository by FakeBoardRepository() {
            override suspend fun getThreadByUrl(threadUrl: String): ThreadPage {
                threadRequests.incrementAndGet()
                return FakeBoardRepository().getThreadByUrl(threadUrl)
            }
        }
        rule.setContent {
            MaterialTheme {
                CompatibilityApp(
                    store = store,
                    repository = repository,
                    initialThreadDeepLink = threadUrl,
                    onExitApplication = {}
                )
            }
        }
        rule.waitUntil(10_000) {
            rule.onAllNodesWithContentDescription("No.1の画像")
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        check(threadRequests.get() == 0) { "Cached thread unexpectedly fetched on entry" }

        rule.onNodeWithContentDescription("No.1の画像").performClick()
        waitForViewerCounter("1/2")
        rule.onNodeWithTag("compat-viewer-toolbar-icon-back", useUnmergedTree = true).performClick()
        rule.waitUntil(10_000) {
            rule.onAllNodesWithContentDescription("No.1の画像")
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        rule.waitForIdle()
        check(threadRequests.get() == 0) { "Viewer return triggered an unnecessary thread fetch" }
    }

    @Test
    fun thousandReplyThreadOnlyRequestsImagesNearTheVisibleViewport() {
        val imagePrefix = "test://compat-large/"
        runBlocking {
            val tabKey = compatTabKey(canonicalizeThreadUrl(threadUrl)!!.canonicalUrl)
            store.saveThreadSnapshot(
                CompatThreadSnapshot(
                    tabKey = tabKey,
                    revision = 2L,
                    fetchedAtEpochMillis = 2L,
                    posts = (0..1_000).map { position ->
                        val imageUrl = if (position % 10 == 0) "$imagePrefix$position" else null
                        CompatPostSnapshot(
                            position = position,
                            postNo = (position + 1).toString(),
                            timestamp = "26/08/05(水)22:00:00",
                            messageHtml = "large post $position",
                            imageUrl = imageUrl,
                            thumbnailUrl = imageUrl
                        )
                    }
                )
            )
        }
        val imageRequests = AtomicInteger(0)
        val interceptor = object : Interceptor {
            override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
                if (!chain.request.data.toString().startsWith(imagePrefix)) return chain.proceed()
                imageRequests.incrementAndGet()
                return SuccessResult(
                    image = bitmap(android.graphics.Color.CYAN, 24, 24).asImage(),
                    request = chain.request,
                    dataSource = DataSource.MEMORY
                )
            }
        }
        testImageLoader = ImageLoader.Builder(context).components { add(interceptor) }.build()
        rule.setContent {
            MaterialTheme {
                CompatibilityApp(
                    store = store,
                    repository = null,
                    imageLoader = testImageLoader,
                    initialThreadDeepLink = threadUrl,
                    onExitApplication = {}
                )
            }
        }
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText("large post 0")
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        rule.onNodeWithContentDescription("ページ最下部へ").performClick()
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText("large post 1000")
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        rule.onNodeWithTag("compat-thread-thumbnail-1001-ready", useUnmergedTree = true)
            .assertIsDisplayed()
        check(imageRequests.get() < 25) {
            "Large thread eagerly requested too many images: ${imageRequests.get()}"
        }
    }

    @Test
    fun viewerDoesNotPaintThumbnailWhileSourceImageIsStillLoading() {
        // Use URLs that do not occur anywhere in the thread screen. The
        // thumbnail is returned immediately, while the source is held behind
        // a gate. This makes it impossible for the pre-view thread row to
        // warm the source request before the viewer is opened.
        val sourceUrl = "test://compat-viewer/source"
        val thumbnailUrl = "test://compat-viewer/thumbnail"
        runBlocking {
            val tabKey = compatTabKey(canonicalizeThreadUrl(threadUrl)!!.canonicalUrl)
            store.saveThreadSnapshot(
                CompatThreadSnapshot(
                    tabKey = tabKey,
                    revision = 2L,
                    fetchedAtEpochMillis = 2L,
                    posts = listOf(postWithThumbnail(0, "1", sourceUrl, thumbnailUrl))
                )
            )
        }
        val interceptor = GatedViewerImageInterceptor(sourceUrl, thumbnailUrl)
        testImageLoader = ImageLoader.Builder(context)
            .components {
                add(interceptor)
            }
            .build()
        rule.setContent {
            MaterialTheme {
                CompatibilityApp(
                    store = store,
                    repository = null,
                    imageLoader = testImageLoader,
                    initialThreadDeepLink = threadUrl,
                    onExitApplication = {}
                )
            }
        }
        rule.waitUntil(10_000) {
            rule.onAllNodesWithContentDescription("No.1の画像")
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        rule.onNodeWithContentDescription("No.1の画像").performClick()
        waitForViewerCounter("1/1")
        rule.waitUntil(10_000) { interceptor.sourceStarted }
        // The Image node can have no drawable bounds while Coil is still in
        // State.Empty on some Compose versions, so existence of this tagged
        // state node is the reliable assertion here.
        check(
            rule.onAllNodesWithTag("compat-viewer-source-loading", useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        )
        check(
            rule.onAllNodesWithTag("compat-viewer-thumbnail-fallback", useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isEmpty()
        )
        interceptor.releaseSource.complete(Unit)
        rule.waitUntil(10_000) {
            rule.onAllNodesWithTag("compat-viewer-source-ready", useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        check(interceptor.sourceSize == Size.ORIGINAL) {
            "Viewer source request was not original-sized: ${interceptor.sourceSize}"
        }
    }

    @Test
    fun pinchZoomOwnsPanAndPreventsPagerOrDismissUntilReset() {
        rule.setContent {
            MaterialTheme {
                CompatibilityApp(
                    store = store,
                    repository = null,
                    initialThreadDeepLink = threadUrl,
                    onExitApplication = {}
                )
            }
        }
        rule.waitUntil(10_000) {
            rule.onAllNodesWithContentDescription("No.1の画像")
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        rule.onNodeWithContentDescription("No.1の画像").performClick()
        waitForViewerCounter("1/2")
        val image = rule.onNodeWithContentDescription("No.1の画像")
        image.assert(hasStateDescription("拡大率 100% 位置 0,0"))

        image.performTouchInput {
            val c = center
            pinch(
                start0 = c + Offset(-20f, 0f),
                end0 = c + Offset(-300f, 0f),
                start1 = c + Offset(20f, 0f),
                end1 = c + Offset(300f, 0f)
            )
        }
        image.assert(hasStateDescription("拡大率 600% 位置 0,0"))

        image.performTouchInput { swipeLeft() }
        rule.onNodeWithText("1/2").assertIsDisplayed()
        image.performTouchInput { swipeUp() }
        rule.onNodeWithText("1/2").assertIsDisplayed()
    }

    @Test
    fun viewerKeepsPannedTransformWhenTheViewerRecomposes() {
        rule.setContent {
            MaterialTheme {
                CompatibilityApp(
                    store = store,
                    repository = null,
                    initialThreadDeepLink = threadUrl,
                    onExitApplication = {}
                )
            }
        }
        rule.waitUntil(10_000) {
            rule.onAllNodesWithContentDescription("No.1の画像")
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        var image = rule.onNodeWithContentDescription("No.1の画像")
        image.performClick()
        waitForViewerCounter("1/2")

        image = rule.onNodeWithContentDescription("No.1の画像")
        image.performTouchInput {
            val c = center
            pinch(
                start0 = c + Offset(-20f, 0f),
                end0 = c + Offset(-300f, 0f),
                start1 = c + Offset(20f, 0f),
                end1 = c + Offset(300f, 0f)
            )
        }
        rule.waitForIdle()
        val afterPinchDescription = image.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        check(afterPinchDescription.startsWith("拡大率 600%")) {
            "Pinch transform was not retained before pan: $afterPinchDescription"
        }
        image.performTouchInput { swipeRight() }
        rule.waitForIdle()
        val beforeDescription = image.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        check(beforeDescription.contains("位置 ")) { "Pan was not reported: $beforeDescription" }
        check(!beforeDescription.endsWith("位置 0,0")) {
            "Pan unexpectedly returned to the centre: $beforeDescription"
        }

        // A chrome toggle forces the parent Scaffold/Pager to recompose. The
        // transform must remain attached to the media URL, not to the pager
        // item instance that happened to render it.
        image.performClick()
        image = rule.onNodeWithContentDescription("No.1の画像")
        val afterDescription = image.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        check(afterDescription == beforeDescription) {
            "Viewer transform changed during recomposition: before=$beforeDescription after=$afterDescription"
        }
    }

    @Test
    fun viewerSwipeMovesToNextAndPreviousImage() {
        rule.setContent {
            MaterialTheme {
                CompatibilityApp(
                    store = store,
                    repository = null,
                    initialThreadDeepLink = threadUrl,
                    onExitApplication = {}
                )
            }
        }
        rule.waitUntil(10_000) {
            rule.onAllNodesWithContentDescription("No.1の画像")
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }

        rule.onNodeWithContentDescription("No.1の画像").performClick()
        waitForViewerCounter("1/2")
        rule.onNodeWithContentDescription("No.1の画像").performTouchInput { swipeLeft() }
        waitForViewerCounter("2/2")
        rule.onNodeWithContentDescription("No.2の画像").performTouchInput { swipeRight() }
        waitForViewerCounter("1/2")
    }

    private fun waitForViewerCounter(counter: String) {
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText(counter)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        rule.onNodeWithText(counter).assertIsDisplayed()
    }

    private fun post(position: Int, postNo: String, imageUrl: String) = CompatPostSnapshot(
        position = position,
        postNo = postNo,
        timestamp = "26/08/05(水)22:00:00",
        messageHtml = "image $postNo",
        imageUrl = imageUrl,
        thumbnailUrl = imageUrl
    )

    private fun postWithThumbnail(
        position: Int,
        postNo: String,
        imageUrl: String,
        thumbnailUrl: String
    ) = CompatPostSnapshot(
        position = position,
        postNo = postNo,
        timestamp = "26/08/05(水)22:00:00",
        messageHtml = "image $postNo",
        imageUrl = imageUrl,
        thumbnailUrl = thumbnailUrl
    )

    private fun bitmap(color: Int, width: Int, height: Int): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(color)
        }

    private class GatedViewerImageInterceptor(
        private val sourceUrl: String,
        private val thumbnailUrl: String
    ) : Interceptor {
        @Volatile
        var sourceStarted: Boolean = false
            private set

        @Volatile
        var sourceSize: Size? = null
            private set

        val releaseSource = CompletableDeferred<Unit>()

        override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
            return when (chain.request.data.toString()) {
                sourceUrl -> {
                    sourceStarted = true
                    sourceSize = chain.size
                    releaseSource.await()
                    SuccessResult(
                        image = bitmap(android.graphics.Color.YELLOW, 256, 256).asImage(),
                        request = chain.request,
                        dataSource = DataSource.MEMORY
                    )
                }

                thumbnailUrl -> SuccessResult(
                    image = bitmap(android.graphics.Color.DKGRAY, 8, 8).asImage(),
                    request = chain.request,
                    dataSource = DataSource.MEMORY
                )

                else -> chain.proceed()
            }
        }

        private fun bitmap(color: Int, width: Int, height: Int): Bitmap =
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                eraseColor(color)
            }
    }
}
