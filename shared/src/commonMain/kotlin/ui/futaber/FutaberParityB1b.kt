package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.compat.CompatImageNgSource
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.service.MANUAL_SAVE_DIRECTORY
import com.valoser.futacha.shared.service.SingleMediaSaveService
import com.valoser.futacha.shared.ui.board.FutachaImageNgRegistration
import com.valoser.futacha.shared.ui.board.FutachaSharedFeatures
import com.valoser.futacha.shared.ui.board.rememberPhotoSaveDestination
import com.valoser.futacha.shared.ui.compat.LocalCompatibilityPalette
import com.valoser.futacha.shared.ui.compat.compatPreferenceValue
import com.valoser.futacha.shared.ui.compat.rememberCompatManualSaveDestinationLauncher
import com.valoser.futacha.shared.ui.compat.rememberCompatShareLauncher
import com.valoser.futacha.shared.ui.compat.toCompatUserMessage
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.Logger
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/*
 * The second half of the B1 batch of the mode parity work (docs/mode-feature-parity-plan-2026-10-12.md §5.3). Each is
 * behind a switch of the extensions page (off by default), except the volume keys' mode (its default is what the keys did
 * before), the "NGで隠したレス" entry of the extract sheet (that sheet is an extension already) and the prompt panel of
 * the video viewer (the shared media features' own switch, off by default, decides it). The big screens take only calls.
 */

private const val PARITY_B1B_TAG = "FutaberParityB1b"

// ---- T15: what the volume keys do ----

/** The volume keys' choices; [Screen] is what they did before there was a choice. */
internal enum class FutaberVolumeKeyMode(val persistedValue: String, val label: String) {
    Screen("screen", "1画面スクロール"),
    Response("response", "1レスずつスクロール"),
    Thread("thread", "タブのスレッドを切り替え");

    companion object {
        /** A missing or unknown value reads as [Screen]. */
        fun fromPersisted(value: String?): FutaberVolumeKeyMode = entries.firstOrNull { it.persistedValue == value } ?: Screen
    }
}

/**
 * The tab after ([forward]) or before [current] in [tabs] (the order of the tab strip); null at either end, and when the
 * open thread is not a tab (the keys then do nothing).
 */
internal fun futaberAdjacentTab(tabs: List<FutaberTabKey>, current: FutaberTabKey?, forward: Boolean): FutaberTabKey? {
    val index = current?.let(tabs::indexOf)?.takeIf { it >= 0 } ?: return null
    return tabs.getOrNull(if (forward) index + 1 else index - 1)
}

/**
 * The row "1レスずつ" moves to: down is the next row; up is the top of the row partly scrolled past, else the row before.
 * Kept inside 0..[lastIndex].
 */
internal fun futaberResponseStepTarget(firstIndex: Int, firstOffset: Int, lastIndex: Int, up: Boolean): Int {
    if (lastIndex < 0) return 0
    val target = when {
        !up -> firstIndex + 1
        firstOffset > 0 -> firstIndex
        else -> firstIndex - 1
    }
    return target.coerceIn(0, lastIndex)
}

// ---- C05: the catalog's sort order per board ----

private val sortByBoardJson = Json { ignoreUnknownKeys = true }

/** The stored board id -> mode pairs; a damaged value, an unknown mode or a blank id is left out. */
internal fun decodeFutaberSortByBoard(raw: String?): Map<String, CatalogMode> {
    if (raw.isNullOrBlank()) return emptyMap()
    val element = try {
        sortByBoardJson.parseToJsonElement(raw).jsonObject
    } catch (_: Exception) {
        return emptyMap()
    }
    // Every sort that can be stored, the B7a extension's two included: they are kept (not dropped by an edit of another
    // board) and read as "通常" while the extension is off ([futaberCatalogSortFor]).
    val known = futaberCatalogSortsFor(extraModes = true).associateBy { it.mode.name }
    return buildMap {
        element.forEach { (boardId, value) ->
            val name = (value as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull ?: return@forEach
            val mode = known[name]?.mode ?: return@forEach
            if (boardId.isNotBlank()) put(boardId, mode)
        }
    }
}

internal fun encodeFutaberSortByBoard(sorts: Map<String, CatalogMode>): String =
    JsonObject(sorts.mapValues { JsonPrimitive(it.value.name) }).toString()

/**
 * The sort the catalog of [boardId] uses: with [perBoard], the board's own record when there is one; otherwise (and
 * always without [perBoard]) the one shared value, as before.
 */
internal fun futaberCatalogSortFor(
    preferences: Map<String, String>,
    boardId: String?,
    perBoard: Boolean,
    /** The B7a extension "そ順・監視": without it a stored "そ順" / "監視" reads as "通常". */
    extraModes: Boolean = false,
    /**
     * The board's shared key ([futaberStoredBoardId]), read before [boardId] (audit L21: the record was kept under the board
     * id, which depends on the order boards were added, so after a restore or on another device it went to another board).
     */
    storedBoardId: String? = null
): CatalogMode {
    val shared = futaberCatalogModeFromPersisted(preferences[FutaberPreferenceKeys.CATALOG_SORT], extraModes)
    if (!perBoard || boardId == null) return shared
    val records = decodeFutaberSortByBoard(preferences[FutaberPreferenceKeys.CATALOG_SORT_BY_BOARD])
    val own = storedBoardId?.let(records::get) ?: records[boardId] ?: return shared
    return if (!extraModes && futaberIsExtraCatalogMode(own)) futaberDefaultCatalogMode else own
}

/**
 * The stored value after [boardId] chose [mode], written under [storedBoardId] (the shared key) when given, and the old
 * record under [boardId] goes (audit L21). The records of boards that are no longer in [boardIds] (by id or by shared key)
 * are dropped.
 */
internal fun futaberSortByBoardAfter(
    stored: String?,
    boardId: String,
    mode: CatalogMode,
    boardIds: Collection<String>,
    storedBoardId: String? = null
): String {
    val live = boardIds.toHashSet()
    val writeKey = storedBoardId ?: boardId
    val next = LinkedHashMap<String, CatalogMode>()
    decodeFutaberSortByBoard(stored).forEach { (id, value) -> if (id in live && id != boardId && id != writeKey) next[id] = value }
    next[writeKey] = mode
    return encodeFutaberSortByBoard(next)
}

// ---- C15: an image NG from the catalog's long press ----

/** The picture of a catalog thread the image NG is made from (the full image, else the thumbnail); null without one. */
internal fun futaberCatalogImageUrl(item: CatalogItem): String? =
    item.fullImageUrl?.takeIf { it.isNotBlank() } ?: item.thumbnailUrl?.takeIf { it.isNotBlank() }

/** What the image NG of a catalog thread is registered with. [scopeKey] is the board of the shared store. */
internal data class FutaberCatalogImageNgTarget(val scopeKey: String, val imageUrl: String, val memo: String)

/**
 * The catalog sheet's "画像NGに登録", or null while the extension is off. It is disabled for a board the shared store
 * cannot hold ([scopeKey] null: the tutorial board, an unofficial board), for a thread without a picture and without the
 * shared services.
 */
internal fun futaberCatalogImageNgAction(
    enabled: Boolean,
    item: CatalogItem,
    scopeKey: String?,
    servicesAvailable: Boolean,
    onRegister: (FutaberCatalogImageNgTarget) -> Unit
): FutaberPostAction? {
    if (!enabled) return null
    val url = futaberCatalogImageUrl(item)
    val target = if (scopeKey != null && url != null) FutaberCatalogImageNgTarget(scopeKey, url, "No.${item.id}") else null
    return FutaberPostAction("image-ng", "画像NGに登録", enabled = servicesAvailable && target != null) {
        target?.let(onRegister)
    }
}

/**
 * What runs before a "この板のみ" image NG of the catalog is written: the shared store learns of this mode's boards (as
 * for "スレッドをNG"). Its failure is reported with the reason instead of the rule being refused for a missing board.
 */
internal fun futaberCatalogImageNgPreparation(store: CompatibilityStore, boards: List<BoardSummary>): suspend () -> Unit = {
    try {
        store.importModernBoards(boards)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Logger.e(PARITY_B1B_TAG, "Failed to share the boards before an image NG", error)
        throw IllegalStateException(
            "板を共有できませんでした（${error.message ?: error::class.simpleName}）。「この板のみ」で登録できませんでした",
            error
        )
    }
}

/**
 * The registration dialog of the image NG (the one ふたちゃ uses, no analytics), in this mode's colours. The shared store
 * first learns of this mode's boards, as for "スレッドをNG".
 */
@Composable
internal fun FutaberCatalogImageNgHost(
    target: FutaberCatalogImageNgTarget?,
    features: FutachaSharedFeatures?,
    boards: List<BoardSummary>,
    onDismiss: () -> Unit
) {
    if (target == null || features == null) return
    val colors = LocalFutaberColors.current
    androidx.compose.runtime.CompositionLocalProvider(LocalCompatibilityPalette provides futaberMediaPalette(colors)) {
        // The boards are shared as a step of the registration ("この板のみ" waits for it), not alongside the dialog.
        FutachaImageNgRegistration(features, target.scopeKey, CompatImageNgSource.CATALOG, target.imageUrl, target.memo, onDismiss,
            prepareBoardScope = futaberCatalogImageNgPreparation(features.store, boards))
    }
}

/**
 * The shared features the borrowed dialogs (image search, image NG, page save) read, made once for the mode and handed to
 * the catalog and the thread. It is not provided to the whole mode ([com.valoser.futacha.shared.ui.board.LocalFutachaSharedFeatures]
 * is given only where a borrowed screen needs it).
 */
@Composable
internal fun rememberFutaberSharedFeatures(services: FutaberMediaServices?, repository: BoardRepository): FutachaSharedFeatures? {
    val preferencesFlow = remember(services) { services?.store?.preferences ?: flowOf(emptyMap()) }
    val preferencesState = preferencesFlow.collectAsState(emptyMap())
    return remember(services, repository) {
        services?.let {
            FutachaSharedFeatures(
                store = it.store,
                preferencesState = preferencesState,
                httpClient = it.httpClient,
                repository = repository,
                fileSystem = it.fileSystem,
                cookieRepository = it.cookieRepository,
                appVersion = "",
                openSettings = {}
            )
        }
    }
}

// ---- A02: deleting only the picture of an own post ----

/** Whether the confirmation of "レス削除" offers "画像だけ消す": with the extension, for a post that has a picture. */
internal fun futaberOffersImageOnlyDelete(enabled: Boolean, action: FutaberRemoteAction, post: Post): Boolean =
    enabled && action == FutaberRemoteAction.DeleteOwn && futaberImageUrlOf(post) != null

internal fun futaberRemoteSuccessMessage(action: FutaberRemoteAction, imageOnly: Boolean): String =
    if (imageOnly && action == FutaberRemoteAction.DeleteOwn) "画像を削除しました" else action.successMessage

internal fun futaberRemoteFailurePrefix(action: FutaberRemoteAction, imageOnly: Boolean): String =
    if (imageOnly && action == FutaberRemoteAction.DeleteOwn) "画像を削除できませんでした" else action.failurePrefix

/** The check box of the delete confirmation. */
@Composable
internal fun FutaberImageOnlyCheckRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = LocalFutaberColors.current
    Row(
        Modifier.heightIn(min = 48.dp).clickable(onClickLabel = "画像だけ消す") { onCheckedChange(!checked) }
            .testTag("futaber-delete-image-only"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked, onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = colors.accent, uncheckedColor = colors.body, checkmarkColor = colors.onAction)
        )
        Text("画像だけ消す", color = colors.body, modifier = Modifier.padding(start = 4.dp))
    }
}

// ---- M02: the picture list's columns ----

/** The shared viewer settings' column counts (portrait, landscape), read as the shared picture list reads them. */
internal fun futaberSharedGalleryColumns(preferences: Map<String, String>): Pair<Int, Int> {
    val portrait = preferences.compatPreferenceValue("viewer", "galleryGridViewPortraitClmNum", "縦持ちの列数")
        ?.toIntOrNull()?.coerceIn(2, 16) ?: 5
    val landscape = preferences.compatPreferenceValue("viewer", "galleryGridViewLandscapeClmNum", "横持ちの列数")
        ?.toIntOrNull()?.coerceIn(2, 16) ?: 7
    return portrait to landscape
}

/** The columns for a list [landscape] or not: the shared counts with the extension, else the original's 3. */
internal fun futaberGalleryColumnCount(shared: Pair<Int, Int>?, landscape: Boolean): Int =
    when {
        shared == null -> FUTABER_GALLERY_DEFAULT_COLUMNS
        landscape -> shared.second
        else -> shared.first
    }

internal const val FUTABER_GALLERY_DEFAULT_COLUMNS = 3

// ---- M05: more entries in a picture's long-press menu ----

/**
 * Saving and sharing one picture or video, the way the picture viewer does it (the shared save service, the save
 * destination, the photo library on iOS). One at a time: [isSaving] holds back a second request.
 */
@Stable
internal class FutaberMediaSaver {
    var isSaving by mutableStateOf(false)
        private set
    var sharingInProgress by mutableStateOf(false)
        private set

    internal var scope: CoroutineScope? = null
    internal var saver: SingleMediaSaveService? = null
    internal var fileSystem: FileSystem? = null
    internal var share: (String, String, String?) -> Unit = { _, _, _ -> }
    internal var withSaveDestination: ((SaveLocation?) -> Unit) -> Unit = {}
    internal var choosePhotoSaveDestination: (String, () -> Unit) -> Unit = { _, next -> next() }
    internal var boardKey: String = ""
    internal var threadKey: String = ""
    internal var screenOnMessage: (String) -> Unit = {}
    /** Where a result goes once the screen that asked for it has gone (the app's notice); null drops it. */
    internal var afterScreenGone: ((String) -> Unit)? = null
    internal var screenGone = false
    internal val onMessage: (String) -> Unit = { message ->
        if (screenGone) afterScreenGone?.invoke(message) else screenOnMessage(message)
    }
    internal var announceStart: Boolean = false

    /** Saves ([shareAfterSave] false) or shares [mediaUrl]; the destination is asked first when there is none yet. */
    fun saveOrShare(mediaUrl: String, shareAfterSave: Boolean) {
        if (isSaving) return
        if (shareAfterSave) withSaveDestination { saveNow(mediaUrl, true, it) }
        else choosePhotoSaveDestination(mediaUrl) { withSaveDestination { saveNow(mediaUrl, false, it) } }
    }

    private fun saveNow(mediaUrl: String, shareAfterSave: Boolean, location: SaveLocation?) {
        if (isSaving) return
        val launchScope = scope ?: return
        isSaving = true
        sharingInProgress = shareAfterSave
        if (announceStart) onMessage(futaberMediaSaveStartedNotice(shareAfterSave))
        launchScope.launch {
            try {
                val service = saver
                val fs = fileSystem
                if (service == null || fs == null) {
                    onMessage(if (shareAfterSave) "画像共有を初期化できませんでした" else "保存機能を初期化できませんでした")
                    return@launch
                }
                service.saveMedia(
                    mediaUrl, boardKey, threadKey,
                    baseSaveLocation = location,
                    storageDirectoryOverride = if (shareAfterSave) null else "",
                    useTypeSubdirectory = shareAfterSave
                ).onSuccess { saved ->
                    if (shareAfterSave && screenGone) {
                        // The viewer that asked has closed: its share sheet is gone with it, the file is kept.
                        onMessage("画像を保存しました（画面を閉じたため共有はしていません）")
                    } else if (shareAfterSave) {
                        val mime = if (saved.mediaType.name == "VIDEO") "video/*" else "image/*"
                        share(
                            mediaUrl, mime,
                            fs.resolveSavedFile(location ?: SaveLocation.Path(MANUAL_SAVE_DIRECTORY), saved.relativePath).getOrThrow()
                        )
                    } else {
                        onMessage("保存しました")
                    }
                }.onFailure {
                    onMessage(it.toCompatUserMessage(if (shareAfterSave) "画像を共有できませんでした" else "画像を保存できませんでした"))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                // The share step after a successful save (finding the saved file) can fail too; it is told, not left to crash.
                onMessage(error.toCompatUserMessage(if (shareAfterSave) "画像を共有できませんでした" else "画像を保存できませんでした"))
            } finally {
                isSaving = false
            }
        }
    }
}

/**
 * Work of this mode that must outlive the screen that started it (re-audit M19): the history band's "一括更新" and the
 * viewer's save / share went on in the panel's or the viewer's scope and stopped without a word when it closed. They run in
 * the app's scope; what they tell after their screen has gone comes as the app's notice.
 */
@androidx.compose.runtime.Stable
internal class FutaberAppTasks(val scope: CoroutineScope, val notify: (String) -> Unit) {
    var historyRefreshing by androidx.compose.runtime.mutableStateOf(false)
    var historyRefreshMessage by androidx.compose.runtime.mutableStateOf<String?>(null)
}

internal val LocalFutaberAppTasks = androidx.compose.runtime.staticCompositionLocalOf<FutaberAppTasks?> { null }

/** A [FutaberMediaSaver] kept for the screen (its destination pickers must stay while they are open). */
@Composable
internal fun rememberFutaberMediaSaver(
    store: CompatibilityStore,
    preferences: Map<String, String>,
    httpClient: HttpClient?,
    fileSystem: FileSystem?,
    boardKey: String,
    threadKey: String,
    /** Tells "保存中…" / "共有の準備中…" through [onMessage] when a save starts (the viewer shows its own progress row instead). */
    announceStart: Boolean = false,
    onMessage: (String) -> Unit
): FutaberMediaSaver {
    val holder = remember { FutaberMediaSaver() }
    val appTasks = LocalFutaberAppTasks.current
    // The app's scope when there is one, so closing the viewer does not stop a save (re-audit M19).
    val scope = appTasks?.scope ?: rememberCoroutineScope()
    androidx.compose.runtime.DisposableEffect(holder) {
        holder.screenGone = false
        onDispose { holder.screenGone = true }
    }
    val share = rememberCompatShareLauncher()
    val saver = remember(httpClient, fileSystem) {
        if (httpClient != null && fileSystem != null) SingleMediaSaveService(httpClient, fileSystem) else null
    }
    val withSaveDestination = rememberCompatManualSaveDestinationLauncher(store, preferences) {
        holder.onMessage(it.toCompatUserMessage("保存先の設定を記録できませんでした"))
    }
    val choosePhotoSaveDestination = rememberPhotoSaveDestination(httpClient, fileSystem) { holder.onMessage(it) }
    // Set before the first composition ends too, so a press that comes at once already finds them.
    holder.scope = scope
    holder.saver = saver
    holder.fileSystem = fileSystem
    holder.share = share
    holder.withSaveDestination = withSaveDestination
    holder.choosePhotoSaveDestination = choosePhotoSaveDestination
    holder.boardKey = boardKey
    holder.threadKey = threadKey
    holder.screenOnMessage = onMessage
    holder.afterScreenGone = appTasks?.notify
    holder.announceStart = announceStart
    return holder
}

/** The picture saver of the thread (the image menu extension); null while that is off or without the shared services. */
@Composable
internal fun rememberFutaberThreadImageSaver(
    enabled: Boolean,
    services: FutaberMediaServices?,
    preferences: Map<String, String>,
    boardKey: String,
    threadKey: String,
    onMessage: (String) -> Unit
): FutaberMediaSaver? {
    if (!enabled || services == null) return null
    return rememberFutaberMediaSaver(
        services.store, preferences, services.httpClient, services.fileSystem, boardKey, threadKey, announceStart = true, onMessage = onMessage
    )
}

/**
 * The entries the extension adds to a picture's long-press menu (after "画像検索" / "画像NGに登録"): save, share, copy the
 * address, open it elsewhere, and open it in the viewer. Empty while the extension is off.
 */
internal fun futaberImageMenuExtraActions(
    enabled: Boolean,
    url: String?,
    saver: FutaberMediaSaver?,
    viewerAvailable: Boolean,
    onCopyUrl: (String) -> Unit,
    onOpenExternal: (String) -> Unit,
    onOpenViewer: () -> Unit,
    onDismiss: () -> Unit
): List<FutaberPostAction> {
    if (!enabled) return emptyList()
    val canSave = url != null && saver != null && !saver.isSaving
    return listOf(
        FutaberPostAction("image-save", "保存", enabled = canSave) { onDismiss(); url?.let { saver?.saveOrShare(it, false) } },
        FutaberPostAction("image-share", "共有", enabled = canSave) { onDismiss(); url?.let { saver?.saveOrShare(it, true) } },
        FutaberPostAction("image-copy-url", "URLをコピー", enabled = url != null) { onDismiss(); url?.let(onCopyUrl) },
        FutaberPostAction("image-external", "外部で開く", enabled = url != null) { onDismiss(); url?.let(onOpenExternal) },
        FutaberPostAction("image-viewer", "ビューアで表示", enabled = url != null && viewerAvailable) { onDismiss(); onOpenViewer() }
    )
}

/** What is shown while the thread's picture is being saved or shared. */
internal fun futaberMediaSaveStartedNotice(sharing: Boolean): String = if (sharing) "共有の準備中…" else "保存中…"
