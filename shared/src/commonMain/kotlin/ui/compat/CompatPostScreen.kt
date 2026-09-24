@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    kotlin.time.ExperimentalTime::class
)

package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.ui.image.rememberGenerationMetadata
import com.valoser.futacha.shared.ui.image.PromptAiBadge
import com.valoser.futacha.shared.ui.image.PromptInfoAction

import com.valoser.futacha.shared.ui.image.clearFutachaImageCaches
import com.valoser.futacha.shared.ui.image.originalMediaCacheSizeBytes

import com.valoser.futacha.shared.ui.image.rememberViewerImagePainter

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.RadioButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import futacha.shared.generated.resources.Res
import futacha.shared.generated.resources.post_video_thumb
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.size.Size
import coil3.request.crossfade
import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.CompatCatalogPreference
import com.valoser.futacha.shared.compat.CompatBuildDraft
import com.valoser.futacha.shared.compat.CompatNgKind
import com.valoser.futacha.shared.compat.CompatNgRule
import com.valoser.futacha.shared.compat.CompatSettingsBackupImportReport
import com.valoser.futacha.shared.compat.appliesToThreadImage
import com.valoser.futacha.shared.compat.compatThreadImageNgScopeKey
import com.valoser.futacha.shared.compat.CompatImagePhash
import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.compat.CompatReplyDraft
import com.valoser.futacha.shared.compat.CompatTab
import com.valoser.futacha.shared.compat.CompatToolbarItem
import com.valoser.futacha.shared.compat.CompatToolbarSurface
import com.valoser.futacha.shared.compat.COMPAT_REFERENCE_HELP_TITLE
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.compat.CompatLegacyScopedWord
import com.valoser.futacha.shared.compat.CompatLegacyBackupData
import com.valoser.futacha.shared.compat.ARCHIVE_REPORT_ENABLED_PREFERENCE_KEY
import com.valoser.futacha.shared.compat.ArchiveReportOutboxStats
import com.valoser.futacha.shared.compat.COMPAT_SETTINGS_BACKUP_FILE_NAME
import com.valoser.futacha.shared.compat.COMPAT_WATCH_NG_BACKUP_FILE_NAME
import com.valoser.futacha.shared.compat.MAX_COMPAT_SETTINGS_BACKUP_BYTES
import com.valoser.futacha.shared.compat.MAX_COMPAT_LEGACY_BACKUP_BYTES
import com.valoser.futacha.shared.compat.MAX_COMPAT_NG_MEMO_CHARS
import com.valoser.futacha.shared.compat.CURRENT_COMPAT_SETTINGS_BACKUP_VERSION
import com.valoser.futacha.shared.compat.ExperienceProfile
import com.valoser.futacha.shared.compat.LocalExperienceProfileUiController
import com.valoser.futacha.shared.compat.compatToolbarMaster
import com.valoser.futacha.shared.compat.compatToolbarShowsOverflow
import com.valoser.futacha.shared.compat.compatNgRuleId
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.compat.canonicalizeBoardUrl
import com.valoser.futacha.shared.compat.decodeCompatLegacyBackup
import com.valoser.futacha.shared.compat.decodeCompatSettingsBackup
import com.valoser.futacha.shared.compat.decodeCompatWatchNgBackup
import com.valoser.futacha.shared.compat.encodeCompatSettingsBackup
import com.valoser.futacha.shared.compat.encodeCompatWatchNgBackup
import com.valoser.futacha.shared.compat.settingsOnly
import com.valoser.futacha.shared.compat.watchAndNgOnly
import com.valoser.futacha.shared.compat.ScrollAnchor
import com.valoser.futacha.shared.compat.toCompatPlainText
import com.valoser.futacha.shared.compat.formatCompatCacheUsage
import com.valoser.futacha.shared.compat.CompatImageCacheUsage
import com.valoser.futacha.shared.compat.formatCompatImageCacheUsage
import com.valoser.futacha.shared.compat.reconcileCompatToolbar
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.network.BoardPostingCapabilities
import com.valoser.futacha.shared.network.defaultBoardPostingCapabilities
import com.valoser.futacha.shared.model.SaveLocation.Companion.toRawString
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.SavePhase
import com.valoser.futacha.shared.model.SaveProgress
import com.valoser.futacha.shared.service.ImageZipSaveService
import com.valoser.futacha.shared.service.MANUAL_SAVE_DIRECTORY
import com.valoser.futacha.shared.service.SingleMediaSaveService
import com.valoser.futacha.shared.service.buildCompatManualImageFolderName
import com.valoser.futacha.shared.ui.board.PlatformVideoPlayer
import com.valoser.futacha.shared.ui.board.VideoMediaInfo
import com.valoser.futacha.shared.ui.board.VideoPlaybackError
import com.valoser.futacha.shared.ui.board.VideoPlayerState
import com.valoser.futacha.shared.ui.board.formatVideoMediaInfoLines
import com.valoser.futacha.shared.ui.board.formatVideoPlaybackError
import com.valoser.futacha.shared.ui.board.formatMediaLoadFailure
import com.valoser.futacha.shared.ui.board.rememberAttachmentPickerLauncher
import com.valoser.futacha.shared.ui.board.rememberDirectoryPickerLauncher
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.ui.image.LocalFutachaCatalogImageLoader
import com.valoser.futacha.shared.ui.image.CompatibilityCacheLocation
import com.valoser.futacha.shared.ui.image.getPlatformCacheAvailableBytes
import com.valoser.futacha.shared.ui.image.isPlatformRemovableCacheAvailable
import com.valoser.futacha.shared.ui.util.PlatformBackHandler
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import com.valoser.futacha.shared.util.ImageData
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation
import com.valoser.futacha.shared.util.rememberUrlLauncher
import io.ktor.client.HttpClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.random.Random
import kotlin.time.Clock

private val compatSecondaryPostNumberRegex = Regex("[0-9]+")
private val compatSecondarySageTokenRegex = Regex("(^|\\s)sage($|\\s)", RegexOption.IGNORE_CASE)
private const val COMPAT_POST_NAME_MAX_CHARS = 100
private const val COMPAT_POST_EMAIL_MAX_CHARS = 100
private const val COMPAT_POST_SUBJECT_MAX_CHARS = 100
private const val COMPAT_POST_COMMENT_MAX_CHARS = 10_000

@Suppress("UNUSED_PARAMETER")
internal fun applyCompatMailPreset(email: String, preset: String, isBuild: Boolean): String = when (preset) {
    // The reference labels are upper-case, while the values posted to Futaba
    // intentionally use lower-case ASCII command tokens.
    "ID表示" -> "id表示"
    "IP表示" -> "ip表示"
    "sage" -> "sage"
    else -> email
}

internal fun compatPostMailPresets(isBuild: Boolean): List<String> =
    if (isBuild) listOf("ID表示", "IP表示", "sage") else listOf("sage")

internal data class CompatPostResetFields(
    val name: String,
    val email: String,
    val subject: String,
    val comment: String,
    val deleteKey: String
)

internal fun compatPostResetFields(
    isBuild: Boolean,
    currentDeleteKey: String,
    initialDraft: CompatReplyDraft
): CompatPostResetFields = if (isBuild) {
    // PostBuildActivity clears the editable content but deliberately keeps the
    // current deletion key. PostResponseActivity restores its opening draft.
    CompatPostResetFields("", "", "", "", currentDeleteKey)
} else {
    CompatPostResetFields(
        initialDraft.name,
        initialDraft.email,
        initialDraft.subject,
        initialDraft.comment,
        initialDraft.deleteKey
    )
}

/**
 * The small-upload confirmation used by both compatibility posting screens.
 *
 * Keep this as a directly testable composable: both reference APKs use the
 * same title, field order and button labels, and deliberately ignore an
 * outside tap/back dismissal. Upload progress is shown by the separate
 * non-cancelable waiting dialog after [onSubmit] is invoked.
 */
@Composable
fun CompatUpsUploadDialog(
    fileName: String,
    comment: String,
    deleteKey: String,
    onCommentChange: (String) -> Unit,
    onDeleteKeyChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit
) {
    val commentFocusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(fileName) {
        delay(150)
        commentFocusRequester.requestFocus()
        keyboard?.show()
    }
    AlertDialog(
        onDismissRequest = {},
        modifier = Modifier.testTag("compat-ups-upload-dialog"),
        title = { Text("あぷ小アップロード") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("アップロードファイル")
                Text(fileName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextField(
                    value = comment,
                    onValueChange = { onCommentChange(it.take(COMPAT_POST_COMMENT_MAX_CHARS)) },
                    label = { Text("コメント") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(commentFocusRequester)
                )
                TextField(
                    value = deleteKey,
                    onValueChange = { onDeleteKeyChange(it.take(COMPAT_POST_DELETE_KEY_MAX_LENGTH)) },
                    label = { Text("削除キー") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSubmit) { Text("送信する") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("キャンセル") }
        }
    )
}

/** Attachment block shared by thread creation and reply forms. */
@Composable
fun CompatPostAttachmentPreview(
    attachment: ImageData,
    onImagePreview: () -> Unit,
    onVideoPreview: () -> Unit
) {
    val kind = compatPostAttachmentKind(attachment.fileName)
    Column(
        modifier = Modifier.fillMaxWidth().testTag("compat-post-attachment-preview"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (kind) {
            CompatPostAttachmentKind.IMAGE -> {
                // The reference fixes the width at 150dp. Landscape previews
                // preserve their ratio; portrait previews remain a 150dp square.
                val ratio = compatPostImageAspectRatio(attachment.bytes)?.coerceIn(1f, 20f) ?: 1f
                AsyncImage(
                    model = attachment.bytes,
                    contentDescription = "添付画像をプレビュー",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .width(150.dp)
                        .aspectRatio(ratio)
                        .background(Color.Black)
                        .clickable(onClick = onImagePreview)
                        .testTag("compat-post-attachment-thumbnail")
                )
            }
            CompatPostAttachmentKind.VIDEO -> {
                Image(
                    painter = painterResource(Res.drawable.post_video_thumb),
                    contentDescription = "添付動画を開く",
                    modifier = Modifier
                        .size(100.dp)
                        .clickable(onClick = onVideoPreview)
                        .testTag("compat-post-attachment-thumbnail")
                )
            }
            CompatPostAttachmentKind.UNSUPPORTED -> Unit
        }
        Text(
            attachment.fileName,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(10.dp)
                .clickable(onClick = if (kind == CompatPostAttachmentKind.VIDEO) onVideoPreview else onImagePreview)
                .testTag("compat-post-attachment-file-name")
        )
    }
}

private enum class CompatPostDraftField { NAME, EMAIL, SUBJECT, COMMENT, DELETE_KEY, ATTACHMENT }

@Composable
internal fun CompatPostScreen(
    tab: CompatTab,
    board: CompatBoard,
    repository: BoardRepository?,
    httpClient: HttpClient? = null,
    store: CompatibilityStore,
    toolbarRefreshToken: Long = 0L,
    onPostSent: () -> Unit = {},
    preferences: Map<String, String>,
    appVersion: String,
    fileSystem: FileSystem?,
    onToolbarEdit: () -> Unit,
    isBuild: Boolean = false,
    onBuildCreated: (String?) -> Unit = {},
    onOpenDrawing: () -> Unit = {},
    onOpenHelp: () -> Unit = {},
    onBack: () -> Unit
) {
    val ownerKey = if (isBuild) "build:${board.key}" else tab.key
    val scope = rememberCoroutineScope()
    val palette = LocalCompatibilityPalette.current
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // A delayed restore must not overwrite edits, including an edit back to empty.
    // Mutated/read on the UI thread; this is bookkeeping, not observable UI state.
    val editedDraftFields = remember(ownerKey) { mutableSetOf<CompatPostDraftField>() }
    var name by remember(ownerKey) { mutableStateOf("") }
    var email by remember(ownerKey) { mutableStateOf("") }
    var subject by remember(ownerKey) { mutableStateOf("") }
    // Keep the complete TextFieldValue here.  Rebuilding it from only text and
    // selection drops the IME composition range, which prevents Japanese
    // keyboards from converting an unfinished kana/romaji sequence.
    var commentValue by remember(ownerKey) {
        mutableStateOf(TextFieldValue(text = "", selection = TextRange.Zero))
    }
    val comment = commentValue.text

    fun replaceComment(
        text: String,
        selection: TextRange = TextRange(text.length),
        restoringDraft: Boolean = false
    ) {
        if (!restoringDraft) editedDraftFields.add(CompatPostDraftField.COMMENT)
        // Programmatic edits must clear any stale composition belonging to the
        // previous value. User edits below retain it by assigning the complete
        // TextFieldValue supplied by Compose.
        val limitedText = text.take(COMPAT_POST_COMMENT_MAX_CHARS)
        commentValue = TextFieldValue(
            text = limitedText,
            selection = TextRange(
                selection.start.coerceIn(0, limitedText.length),
                selection.end.coerceIn(0, limitedText.length)
            )
        )
    }
    var deleteKey by remember(ownerKey) { mutableStateOf("") }
    var attachment by remember(ownerKey) { mutableStateOf<ImageData?>(null) }
    var attachmentLocator by remember(ownerKey) { mutableStateOf<String?>(null) }
    var initialDraft by remember(ownerKey) {
        mutableStateOf(CompatReplyDraft(tabKey = ownerKey, updatedAtEpochMillis = 0L))
    }
    var initialAttachment by remember(ownerKey) { mutableStateOf<ImageData?>(null) }
    var initialAttachmentLocator by remember(ownerKey) { mutableStateOf<String?>(null) }
    var draftLoaded by remember(ownerKey) { mutableStateOf(false) }
    var sending by remember(ownerKey) { mutableStateOf(false) }
    var message by remember(ownerKey) { mutableStateOf<String?>(null) }

    fun launchScreenAction(block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit): Job =
        scope.launchCompatScreenAction("CompatPost", { failure ->
            message = failure.toCompatUserMessage("操作に失敗しました")
        }, block)
    var pendingCompression by remember(ownerKey) { mutableStateOf<ImageData?>(null) }
    var attachmentPreviewOpen by remember(ownerKey) { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }
    var toolbarOverflowOpen by remember { mutableStateOf(false) }
    var upsUploadRequested by remember(ownerKey) { mutableStateOf(false) }
    var upsAttachment by remember(ownerKey) { mutableStateOf<ImageData?>(null) }
    var upsDialogOpen by remember(ownerKey) { mutableStateOf(false) }
    var upsComment by remember(ownerKey) { mutableStateOf("") }
    var upsDeleteKey by remember(ownerKey) { mutableStateOf("") }
    var upsUploadInProgress by remember(ownerKey) { mutableStateOf(false) }
    var discardConfirm by remember { mutableStateOf(false) }
    var sendConfirm by remember { mutableStateOf(false) }
    var postDestinationWarning by remember { mutableStateOf<String?>(null) }
    var toolbarItems by remember { mutableStateOf(reconcileCompatToolbar(CompatToolbarSurface.POST, emptyList())) }
    var postingCapabilities by remember(board.canonicalUrl) {
        mutableStateOf(defaultBoardPostingCapabilities(board.canonicalUrl))
    }
    val attachmentLimitBytes = postingCapabilities.maxFileSizeBytes.toInt()
    val storedDeleteKey = preferences.compatStoredPostDeleteKey()

    fun hasDraft(): Boolean = name.isNotEmpty() || email.isNotEmpty() || subject.isNotEmpty() ||
        comment.isNotEmpty() || deleteKey.isNotEmpty() || attachment != null

    suspend fun persistCurrentDraftOrDelete() {
        if (hasDraft()) {
            val now = Clock.System.now().toEpochMilliseconds()
            if (isBuild) {
                store.saveBuildDraft(
                    CompatBuildDraft(
                        boardKey = board.key,
                        name = name,
                        email = email,
                        subject = subject,
                        comment = comment,
                        attachmentUri = attachmentLocator,
                        deleteKey = deleteKey,
                        updatedAtEpochMillis = now
                    )
                )
            } else {
                store.saveDraft(
                    CompatReplyDraft(
                        tabKey = tab.key,
                        name = name,
                        email = email,
                        subject = subject,
                        comment = comment,
                        attachmentUri = attachmentLocator,
                        deleteKey = deleteKey,
                        updatedAtEpochMillis = now
                    )
                )
            }
        } else {
            if (isBuild) store.deleteBuildDraft(board.key) else store.deleteDraft(tab.key)
        }
    }

    LaunchedEffect(ownerKey) {
        try {
            val draft = if (isBuild) {
                store.loadBuildDraft(board.key)?.let { build ->
                    CompatReplyDraft(
                        tabKey = ownerKey,
                        name = build.name,
                        email = build.email,
                        subject = build.subject,
                        comment = build.comment,
                        attachmentUri = build.attachmentUri,
                        deleteKey = build.deleteKey,
                        updatedAtEpochMillis = build.updatedAtEpochMillis
                    )
                }
            } else {
                store.loadDraft(tab.key)
            } ?: CompatReplyDraft(tabKey = ownerKey, updatedAtEpochMillis = Clock.System.now().toEpochMilliseconds())
            val restoredAttachment = draft.attachmentUri?.let { locator ->
                fileSystem?.let { loadCompatPostAttachment(it, locator).getOrNull() }
            }
            val effectiveDraft = if (draft.attachmentUri != null && restoredAttachment == null) {
                draft.copy(attachmentUri = null)
            } else {
                draft
            }
            val draftWithStoredDeleteKey = effectiveDraft.copy(
                deleteKey = effectiveDraft.deleteKey.ifBlank { storedDeleteKey }
            )
            initialDraft = draftWithStoredDeleteKey
            if (CompatPostDraftField.NAME !in editedDraftFields) name = draft.name.take(COMPAT_POST_NAME_MAX_CHARS)
            if (CompatPostDraftField.EMAIL !in editedDraftFields) email = draft.email.take(COMPAT_POST_EMAIL_MAX_CHARS)
            if (CompatPostDraftField.SUBJECT !in editedDraftFields) subject = draft.subject.take(COMPAT_POST_SUBJECT_MAX_CHARS)
            if (CompatPostDraftField.COMMENT !in editedDraftFields) {
                replaceComment(draft.comment, TextRange(draft.comment.length), restoringDraft = true)
            }
            // Quick replies are inserted before opening this form. Put the caret
            // after the generated quote, matching the legacy app's reply flow.
            if (CompatPostDraftField.DELETE_KEY !in editedDraftFields) deleteKey = draftWithStoredDeleteKey.deleteKey
            if (CompatPostDraftField.ATTACHMENT !in editedDraftFields) {
                attachment = restoredAttachment
                attachmentLocator = effectiveDraft.attachmentUri
            }
            initialAttachment = restoredAttachment
            initialAttachmentLocator = effectiveDraft.attachmentUri
            if (draft.attachmentUri != null && restoredAttachment == null) {
                message = "添付ファイルが\nリセットされました"
            }
            draftLoaded = true
            // Wait until the form is attached before requesting focus. This matches
            // the reference reply screen, which opens directly on the comment field
            // with the IME already visible.
            delay(150)
            focusRequester.requestFocus()
            // On Android the first IME request can race the navigation transition.
            // Request it once more after the focused EditText has reached a frame;
            // the reference app opens the keyboard immediately on entering this form.
            delay(200)
            keyboard?.show()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            Logger.e("CompatPost", "下書きを読み込めませんでした", failure)
            message = "下書きを読み込めませんでした"
        }
    }
    // Preferences can arrive one frame after the form's draft. Fill the field once in that
    // case, while preserving a draft or an edit the user has already made.
    LaunchedEffect(ownerKey, storedDeleteKey, draftLoaded) {
        if (!draftLoaded || storedDeleteKey.isBlank() || deleteKey.isNotBlank() ||
            CompatPostDraftField.DELETE_KEY in editedDraftFields
        ) return@LaunchedEffect
        deleteKey = storedDeleteKey
        initialDraft = initialDraft.copy(deleteKey = storedDeleteKey)
    }
    LaunchedEffect(toolbarRefreshToken) {
        runSuspendCatchingPreservingCancellation { store.loadToolbar(CompatToolbarSurface.POST) }
            .onSuccess { toolbarItems = it }
            .onFailure { failure ->
                Logger.e("CompatPost", "Toolbar load failed", failure)
                message = "ツールバー設定を読み込めませんでした"
            }
    }
    LaunchedEffect(repository, board.originalUrl) {
        postingCapabilities = runSuspendCatchingPreservingCancellation {
            repository?.getPostingCapabilities(board.originalUrl)
        }.getOrNull() ?: defaultBoardPostingCapabilities(board.canonicalUrl)
    }
    LaunchedEffect(name, email, subject, comment, deleteKey, attachment, attachmentLocator, draftLoaded) {
        if (!draftLoaded) return@LaunchedEffect
        delay(300)
        try {
            persistCurrentDraftOrDelete()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            Logger.e("CompatPost", "Draft persistence failed", failure)
            message = "下書きを保存できませんでした"
        }
    }
    LaunchedEffect(message) {
        val shownMessage = message ?: return@LaunchedEffect
        delay(2_500)
        if (message == shownMessage) message = null
    }

    fun leave() {
        keyboard?.hide()
        onBack()
    }

    fun clearAttachment(deleteContainer: Boolean = false) {
        editedDraftFields.add(CompatPostDraftField.ATTACHMENT)
        val locator = attachmentLocator
        attachmentLocator = null
        attachment = null
        if (fileSystem != null && locator != null) {
            launchScreenAction {
                deleteCompatPostAttachment(fileSystem, locator, deleteContainer).onFailure { error ->
                    message = "添付ファイルを削除できませんでした: ${error.message.orEmpty()}"
                }
            }
        }
    }

    fun persistAcceptedAttachment(selected: ImageData) {
        val localFileSystem = fileSystem
        if (localFileSystem == null) {
            editedDraftFields.add(CompatPostDraftField.ATTACHMENT)
            attachmentLocator = null
            attachment = selected
            return
        }
        val previousLocator = attachmentLocator
        launchScreenAction {
            persistCompatPostAttachment(localFileSystem, ownerKey, selected)
                .onSuccess { persistedLocator ->
                    editedDraftFields.add(CompatPostDraftField.ATTACHMENT)
                    attachmentLocator = persistedLocator
                    attachment = selected
                    if (previousLocator != null && previousLocator != persistedLocator) {
                        deleteCompatPostAttachment(localFileSystem, previousLocator)
                    }
                }
                .onFailure { error ->
                    message = "添付ファイルを一時保存できませんでした: ${error.message.orEmpty()}"
                }
        }
    }

    fun acceptAttachment(selected: ImageData) {
        if (upsUploadRequested) {
            upsUploadRequested = false
            if (!isCompatUpsUploadSizeAllowed(selected.bytes.size)) {
                message = "ファイルサイズ超過です\n3000KBまで"
            } else {
                upsAttachment = selected
                // The reference dialog starts with an empty upload comment and
                // the globally remembered deletion key, not the post body or a
                // not-yet-saved edit in the posting form.
                val initialFields = compatUpsUploadInitialFields(storedDeleteKey)
                upsComment = initialFields.comment
                upsDeleteKey = initialFields.deleteKey
                upsDialogOpen = true
            }
            return
        }
        val decision = decideCompatPostAttachment(
                attachment = selected,
                maxBytes = attachmentLimitBytes,
                supportedExtensions = postingCapabilities.supportedExtensions
            )
        when (decision) {
            CompatPostAttachmentDecision.Accept -> persistAcceptedAttachment(selected)
            CompatPostAttachmentDecision.AskImageCompression -> {
                message = compatPostAttachmentDecisionMessage(decision, selected.fileName, attachmentLimitBytes)
                pendingCompression = selected
            }
            else -> message = compatPostAttachmentDecisionMessage(decision, selected.fileName, attachmentLimitBytes)
        }
    }

    val launchAttachmentPicker = rememberAttachmentPickerLauncher(
        // 1.apk uses ACTION_GET_CONTENT + CATEGORY_OPENABLE in both posting
        // modes and reuses the component selected by a toolbar long press.
        preference = AttachmentPickerPreference.COMPAT_REFERENCE_GET_CONTENT,
        mimeType = "*/*",
        maxBytes = COMPAT_POST_PICKER_MAX_BYTES,
        onImageSelected = ::acceptAttachment,
        onSelectionError = { message = it }
    )
    val launchAttachmentChooser = rememberAttachmentPickerLauncher(
        preference = AttachmentPickerPreference.ALWAYS_ASK,
        mimeType = "*/*",
        maxBytes = COMPAT_POST_PICKER_MAX_BYTES,
        onImageSelected = ::acceptAttachment,
        onSelectionError = { message = it }
    )
    fun attachmentCommand(explicitChooser: Boolean = false) {
        upsUploadRequested = false
        if (attachment != null) {
            clearAttachment()
        } else if (explicitChooser) {
            launchAttachmentChooser()
        } else {
            launchAttachmentPicker()
        }
    }

    fun upsUploadCommand(explicitChooser: Boolean = false) {
        if (httpClient == null) {
            message = "あぷ小連携を初期化できませんでした"
        } else if (upsUploadInProgress) {
            message = "アップロード中です"
        } else {
            upsUploadRequested = true
            if (explicitChooser) launchAttachmentChooser() else launchAttachmentPicker()
        }
    }

    fun postValidationError(): String? = when {
        repository == null -> "通信機能を初期化できませんでした"
        comment.isBlank() && attachment == null -> "コメントが空白です"
        deleteKey.isBlank() -> "削除キーを入力して下さい"
        else -> null
    }

    fun sendPost() {
        val validationError = postValidationError()
        if (validationError != null) {
            message = validationError
        } else if (!sending) {
            launchScreenAction {
                sending = true
                try {
                    val currentLocator = attachmentLocator
                    if (currentLocator != null && fileSystem != null && !fileSystem.exists(currentLocator)) {
                        message = "添付ファイルが見つかりません"
                        attachment = null
                        attachmentLocator = null
                        sending = false
                        return@launchScreenAction
                    }
                    runSuspendCatchingPreservingCancellation {
                        if (isBuild) {
                            checkNotNull(repository).createThread(
                                board = board.originalUrl,
                                name = name,
                                email = email,
                                subject = subject,
                                comment = comment,
                                password = deleteKey,
                                imageFile = attachment?.bytes,
                                imageFileName = attachment?.fileName,
                                textOnly = attachment == null
                            )
                        } else {
                            checkNotNull(repository).replyToThread(
                                board = board.originalUrl,
                                threadId = tab.threadNo,
                                name = name,
                                email = email,
                                subject = subject,
                                comment = comment,
                                password = deleteKey,
                                imageFile = attachment?.bytes,
                                imageFileName = attachment?.fileName,
                                textOnly = attachment == null
                            )
                        }
                    }.onSuccess { responseId ->
                        draftLoaded = false
                        suspend fun finishLocalStep(operation: suspend () -> Unit) {
                            runSuspendCatchingPreservingCancellation { operation() }.onFailure { failure ->
                                Logger.e("CompatPost", "Post accepted, but local cleanup failed", failure)
                            }
                        }
                        finishLocalStep {
                            store.savePreference(
                                COMPAT_POST_DELETE_KEY_STORAGE_KEY,
                                compatPostDeleteKeyForStorage(deleteKey)
                            )
                        }
                        if (!isBuild) {
                            responseId?.let { raw ->
                                compatSecondaryPostNumberRegex.find(raw)?.value?.let { postNo ->
                                    finishLocalStep { store.savePreference("compat.ownpost.${tab.key}.$postNo", "1") }
                                }
                            }
                        }
                        finishLocalStep {
                            if (isBuild) store.deleteBuildDraft(board.key) else store.deleteDraft(tab.key)
                        }
                        finishLocalStep {
                            attachmentLocator?.let { locator ->
                                fileSystem?.let { deleteCompatPostAttachment(it, locator, deleteContainer = true) }
                            }
                        }
                        if (isBuild) {
                            onBuildCreated(responseId)
                        } else {
                            onPostSent()
                            leave()
                        }
                    }.onFailure { message = it.message ?: if (isBuild) "スレッドを立てられませんでした" else "投稿できませんでした" }
                } finally {
                    sending = false
                }
            }
        }
    }
    fun requestSend() {
        val destinationWarning = compatPostDestinationWarning(
                boardUrl = board.originalUrl,
                comment = comment,
                enabled = preferences.compatPreferenceValue(
                    "control", "controlPostDestinationConfirm", "板名の誤投稿確認"
                ) == "ON"
            )
        if (destinationWarning != null) {
            postDestinationWarning = destinationWarning
            return
        }
        if (
            preferences.compatPreferenceValue("control", "controlPostConfirm", "送信時の確認") == "OFF"
        ) {
            sendPost()
        } else {
            sendConfirm = true
        }
    }
    val postActions: Map<String, () -> Unit> = mapOf(
        "send" to ::requestSend,
        "attach" to { attachmentCommand() },
        "pallete" to {
            launchScreenAction {
                persistCurrentDraftOrDelete()
                keyboard?.hide()
                onOpenDrawing()
            }
        },
        "sio" to { upsUploadCommand() },
        "voice_input" to {},
        "network_info" to {
            launchScreenAction {
                val info = fetchCompatPostNetworkInfo(httpClient, "Futacha/$appVersion")
                // Read the actual state after the suspension, not this composition's String.
                replaceComment(appendCompatPostText(commentValue.text, info))
            }
        },
        "model_info" to { replaceComment(appendCompatPostText(comment, compatPostDeviceInfo(appVersion))) },
        "reset" to {
            editedDraftFields.addAll(CompatPostDraftField.entries)
            val resetFields = compatPostResetFields(isBuild, deleteKey, initialDraft)
            name = resetFields.name
            email = resetFields.email
            subject = resetFields.subject
            replaceComment(resetFields.comment)
            deleteKey = resetFields.deleteKey
            val previousLocator = attachmentLocator
            val resetAttachmentLocator = if (isBuild) null else initialAttachmentLocator
            attachmentLocator = resetAttachmentLocator
            attachment = if (isBuild) null else initialAttachment
            if (
                fileSystem != null &&
                previousLocator != null &&
                previousLocator != resetAttachmentLocator
            ) {
                launchScreenAction { deleteCompatPostAttachment(fileSystem, previousLocator) }
            }
            message = null
        },
        "discard" to { if (hasDraft()) discardConfirm = true else leave() }
    )
    val launchSpeechRecognizer = rememberCompatSpeechRecognizer(
        onResult = { recognized -> replaceComment(appendCompatPostText(comment, normalizeCompatSpeechResult(recognized))) },
        onError = { message = it }
    )
    val launchVideoPreview = rememberCompatVideoAttachmentPreviewLauncher { message = it }
    val resolvedPostActions = postActions + ("voice_input" to launchSpeechRecognizer)
    val formScrollState = rememberScrollState()
    val postTextFieldColors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        focusedIndicatorColor = palette.inputCursor,
        unfocusedIndicatorColor = palette.divider,
        // Black uses black chrome but a white colorAccent in sample/1.apk.
        // Cursor visibility therefore follows the dedicated input token.
        cursorColor = palette.inputCursor,
        focusedTextColor = palette.text,
        unfocusedTextColor = palette.text,
        focusedLabelColor = palette.uiPrimaryText,
        unfocusedLabelColor = palette.uiPrimaryText,
        focusedPlaceholderColor = palette.uiPrimaryText,
        unfocusedPlaceholderColor = palette.uiPrimaryText
    )

    CompatPostImePolicyEffect()
    // WaitingDialogFragment in both reference APKs is explicitly non-cancelable.
    // Consume the platform Back action while the network request is in flight.
    PlatformBackHandler(enabled = sending || upsUploadInProgress) {}

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = SecondaryBackground,
            topBar = {
                TopAppBar(
                    expandedHeight = 56.dp,
                    title = { Text(if (isBuild) "スレ立て" else tab.title.ifBlank { "No.${tab.threadNo}" }, maxLines = 1, modifier = Modifier.padding(start = 16.dp)) },
                    navigationIcon = {
                        IconButton(onClick = ::leave) { Icon(Icons.Filled.ArrowBack, contentDescription = "戻る") }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { overflowOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "その他")
                            }
                            DropdownMenu(
                                expanded = overflowOpen,
                                onDismissRequest = { overflowOpen = false },
                                shape = RoundedCornerShape(2.dp),
                                containerColor = compatibilityPopupSurface(LocalCompatibilityPalette.current),
                                tonalElevation = 0.dp,
                                shadowElevation = 8.dp
                            ) {
                                DropdownMenuItem(text = { Text("ツールバー編集") }, colors = compatibilityMenuItemColors(), onClick = { overflowOpen = false; onToolbarEdit() })
                                DropdownMenuItem(text = { Text("ヘルプ") }, colors = compatibilityMenuItemColors(), onClick = { overflowOpen = false; onOpenHelp() })
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = SecondaryTeal,
                        titleContentColor = SecondaryChromeContent,
                        navigationIconContentColor = SecondaryChromeContent,
                        actionIconContentColor = SecondaryChromeContent
                    )
                )
            }
        ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(padding)
                .verticalScroll(formScrollState)
                .padding(horizontal = 10.dp, vertical = 0.dp)
                .padding(bottom = 40.dp)
        ) {
            // Keep the compact APK spacing while retaining enough vertical
            // room for the label, baseline and IME text.  A fixed 45dp box
            // clips the glyphs on current Material3 fonts.
            Spacer(Modifier.height(15.dp))
            // PostResponseActivity puts the comment first, then the byte count,
            // name/mail, sage, subject and delete key. The shared form used to
            // place these in a modern create/reply order and showed a large empty
            // comment box, which was visibly different on the reference APK.
            TextField(
                value = commentValue,
                onValueChange = {
                    if (it.text != commentValue.text) editedDraftFields.add(CompatPostDraftField.COMMENT)
                    // Preserve IME composition while the user is converting
                    // Japanese text. This is intentionally not reconstructed
                    // from it.text/it.selection.
                    commentValue = if (it.text.length <= COMPAT_POST_COMMENT_MAX_CHARS) {
                        it
                    } else {
                        val limited = it.text.take(COMPAT_POST_COMMENT_MAX_CHARS)
                        TextFieldValue(limited, TextRange(limited.length))
                    }
                },
                label = { Text("コメント", modifier = Modifier.offset(x = (-12).dp)) },
                singleLine = false,
                minLines = 1,
                maxLines = 8,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .focusRequester(focusRequester)
                    .testTag("compat-post-comment-field"),
                colors = postTextFieldColors
            )
            Spacer(Modifier.height(5.dp))
            // The reference form displays an empty form as one line in both
            // reply and thread-creation modes.
            val lineCount = compatPostLineCount(comment, emptyIsOneLine = !isBuild)
            val byteCount = compatPostShiftJisByteCount(comment)
            Text(
                "${lineCount}行 ${byteCount}バイト",
                fontSize = 14.sp,
                color = if (lineCount > 15 || byteCount > 1000) Color.Red else Color.Unspecified,
                modifier = Modifier.height(18.dp).padding(horizontal = 4.dp)
            )
            Spacer(Modifier.height(25.dp))
            attachment?.let { selected ->
                CompatPostAttachmentPreview(
                    attachment = selected,
                    onImagePreview = { attachmentPreviewOpen = true },
                    onVideoPreview = { launchVideoPreview(selected) }
                )
            }
            TextField(
                name,
                { editedDraftFields.add(CompatPostDraftField.NAME); name = it.take(COMPAT_POST_NAME_MAX_CHARS) },
                label = { Text("おなまえ", modifier = Modifier.offset(x = (-12).dp)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("compat-post-name-field"),
                colors = postTextFieldColors
            )
            Spacer(Modifier.height(10.dp))
            TextField(
                email,
                { editedDraftFields.add(CompatPostDraftField.EMAIL); email = it.take(COMPAT_POST_EMAIL_MAX_CHARS) },
                label = { Text("メール", modifier = Modifier.offset(x = (-12).dp)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                colors = postTextFieldColors
            )
            Spacer(Modifier.height(5.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.weight(1f))
                compatPostMailPresets(isBuild).forEach { preset ->
                    val selected = when (preset) {
                        "sage" -> compatSecondarySageTokenRegex.containsMatchIn(email)
                        else -> email == applyCompatMailPreset("", preset, isBuild)
                    }
                    Text(
                        preset,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        color = palette.uiPrimaryText,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .width(80.dp)
                            .fillMaxHeight()
                            .clickable {
                                editedDraftFields.add(CompatPostDraftField.EMAIL)
                                email = applyCompatMailPreset(email, preset, isBuild)
                            }
                    )
                }
            }
            TextField(
                subject,
                { editedDraftFields.add(CompatPostDraftField.SUBJECT); subject = it.take(COMPAT_POST_SUBJECT_MAX_CHARS) },
                label = { Text("題名", modifier = Modifier.offset(x = (-12).dp)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                colors = postTextFieldColors
            )
            Spacer(Modifier.height(10.dp))
            TextField(
                deleteKey,
                { editedDraftFields.add(CompatPostDraftField.DELETE_KEY); deleteKey = it.take(COMPAT_POST_DELETE_KEY_MAX_LENGTH) },
                label = { Text("削除キー", modifier = Modifier.offset(x = (-12).dp)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                colors = postTextFieldColors
            )
            Spacer(Modifier.imePadding())
        }
        }
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding()
                .background(SecondaryTeal).navigationBarsPadding()
                .testTag("compat-post-bottom-bar")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().requiredHeight(40.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                toolbarItems.filter(CompatToolbarItem::active).sortedBy(CompatToolbarItem::position).forEach { item ->
                    Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        if (item.key == "attach") {
                            Box(
                                modifier = Modifier.fillMaxSize().combinedClickable(
                                    enabled = !sending && !upsUploadInProgress,
                                    onClick = { attachmentCommand() },
                                    onLongClick = { attachmentCommand(explicitChooser = true) }
                                ),
                                contentAlignment = Alignment.Center
                            ) {
                                CompatToolbarArtworkIcon(
                                    artwork = secondaryToolbarIcon(
                                        CompatToolbarSurface.POST,
                                        if (attachment == null) item.key else "attach_clear"
                                    ),
                                    contentDescription = compatPostAttachmentToolbarLabel(attachment != null),
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                        .testTag("compat-post-toolbar-icon-${item.key}")
                                )
                            }
                        } else if (item.key == "sio") {
                            Box(
                                modifier = Modifier.fillMaxSize().combinedClickable(
                                    enabled = !sending && !upsUploadInProgress,
                                    onClick = { upsUploadCommand() },
                                    onLongClick = { upsUploadCommand(explicitChooser = true) }
                                ),
                                contentAlignment = Alignment.Center
                            ) {
                                CompatToolbarArtworkIcon(
                                    artwork = secondaryToolbarIcon(CompatToolbarSurface.POST, item.key),
                                    contentDescription = compatToolbarLabel(CompatToolbarSurface.POST, item.key),
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                        .testTag("compat-post-toolbar-icon-${item.key}")
                                )
                            }
                        } else {
                            IconButton(
                                onClick = { resolvedPostActions[item.key]?.invoke() },
                                enabled = !sending && !upsUploadInProgress
                            ) {
                                if (item.key == "send" && sending) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                                } else {
                                    CompatToolbarArtworkIcon(
                                        artwork = secondaryToolbarIcon(CompatToolbarSurface.POST, item.key),
                                        contentDescription = compatToolbarLabel(CompatToolbarSurface.POST, item.key),
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                            .testTag("compat-post-toolbar-icon-${item.key}")
                                    )
                                }
                            }
                        }
                    }
                }
                if (compatToolbarShowsOverflow(CompatToolbarSurface.POST, toolbarItems)) {
                    IconButton(onClick = { toolbarOverflowOpen = true }, modifier = Modifier.weight(1f)) {
                        CompatToolbarArtworkIcon(
                            artwork = secondaryToolbarIcon(CompatToolbarSurface.POST, "other"),
                            contentDescription = "その他",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp).testTag("compat-post-toolbar-icon-other")
                        )
                    }
                }
            }
        }
        message?.let { transientMessage ->
            Text(
                text = transientMessage,
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding()
                    .navigationBarsPadding()
                    .padding(bottom = 40.dp)
                    .background(Color(0xFF646464))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
        if (sending || upsUploadInProgress) {
            // WaitingDialogFragment is a real non-cancelable dialog in both
            // reference APKs. A plain overlay leaves Android's parent host
            // BackHandler able to win during a busy frame and close the post
            // screen while the request is still active.
            Dialog(
                onDismissRequest = {},
                properties = DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false,
                    usePlatformDefaultWidth = false
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.32f))
                        .clickable(onClick = {})
                        .testTag("compat-post-waiting-dialog"),
                    contentAlignment = Alignment.Center
                ) {
                    CompatLoadingIndicator(
                        style = preferences.compatPreferenceValue(
                            "design", "designLoading", "ローディング"
                        ),
                        modifier = Modifier,
                        size = 50.dp
                    )
                }
            }
        }
    }

    pendingCompression?.let { oversizedImage ->
        CompatPostImageCompressConfirmation(
            onCompress = {
                pendingCompression = null
                launchScreenAction {
                    compressCompatPostImage(
                        oversizedImage,
                        attachmentLimitBytes
                    ).onSuccess(::persistAcceptedAttachment)
                        .onFailure { message = "画像を圧縮できませんでした: ${it.message.orEmpty()}" }
                }
            },
            onCancel = { pendingCompression = null }
        )
    }
    if (attachmentPreviewOpen) {
        val previewAttachment = attachment
        if (previewAttachment == null || compatPostAttachmentKind(previewAttachment.fileName) != CompatPostAttachmentKind.IMAGE) {
            attachmentPreviewOpen = false
        } else {
            Dialog(
                onDismissRequest = { attachmentPreviewOpen = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = previewAttachment.bytes,
                        contentDescription = "添付画像プレビューを閉じる",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth(0.8f).fillMaxHeight(0.8f)
                            .background(Color.Black)
                            .clickable { attachmentPreviewOpen = false }
                    )
                }
            }
        }
    }

    if (upsDialogOpen) {
        val selected = upsAttachment
        if (selected != null) {
            CompatUpsUploadDialog(
                fileName = selected.fileName,
                comment = upsComment,
                deleteKey = upsDeleteKey,
                onCommentChange = { upsComment = it },
                onDeleteKeyChange = { upsDeleteKey = it },
                onSubmit = {
                    val client = httpClient
                    upsDialogOpen = false
                    if (client == null) {
                        message = "あぷ小連携を初期化できませんでした"
                        upsAttachment = null
                    } else {
                        upsUploadInProgress = true
                        launchScreenAction {
                            uploadCompatUps(
                                client = client,
                                attachment = selected,
                                comment = upsComment,
                                deleteKey = upsDeleteKey,
                                appVersion = appVersion
                            ).onSuccess { fileName ->
                                replaceComment(appendCompatPostText(commentValue.text, fileName))
                                message = "${fileName}を追記しました"
                            }.onFailure { error ->
                                message = error.message ?: "あぷ小へのアップロードに失敗しました"
                            }
                            upsAttachment = null
                            upsUploadInProgress = false
                        }
                    }
                },
                onCancel = {
                    upsDialogOpen = false
                    upsAttachment = null
                }
            )
        }
    }

    if (discardConfirm) {
        AlertDialog(
            onDismissRequest = { discardConfirm = false },
            title = { Text("投稿内容の破棄") },
            text = { Text("本当によろしいですか？") },
            confirmButton = {
                TextButton(onClick = {
                    discardConfirm = false
                    editedDraftFields.addAll(CompatPostDraftField.entries)
                    val discardedAttachmentLocator = attachmentLocator
                    name = ""; email = ""; subject = ""; replaceComment(""); deleteKey = ""; attachment = null
                    attachmentLocator = null
                    launchScreenAction {
                        discardedAttachmentLocator?.let { locator ->
                            fileSystem?.let { deleteCompatPostAttachment(it, locator, deleteContainer = true) }
                        }
                        if (isBuild) store.deleteBuildDraft(board.key) else store.deleteDraft(tab.key)
                        leave()
                    }
                }) { Text("破棄する") }
            },
            dismissButton = { TextButton(onClick = { discardConfirm = false }) { Text("キャンセル") } }
        )
    }
    if (sendConfirm) {
        AlertDialog(
            onDismissRequest = { sendConfirm = false },
            title = { Text("投稿の確認") },
            text = { Text("本当によろしいですか？") },
            confirmButton = {
                TextButton(onClick = { sendConfirm = false; sendPost() }) { Text("送信する") }
            },
            dismissButton = { TextButton(onClick = { sendConfirm = false }) { Text("キャンセル") } }
        )
    }
    postDestinationWarning?.let { warning ->
        AlertDialog(
            onDismissRequest = { postDestinationWarning = null },
            title = { Text("投稿先の確認") },
            text = { Text(warning) },
            confirmButton = {
                TextButton(onClick = {
                    postDestinationWarning = null
                    if (preferences.compatPreferenceValue("control", "controlPostConfirm", "送信時の確認") == "OFF") {
                        sendPost()
                    } else {
                        sendConfirm = true
                    }
                }) { Text("確認して続行") }
            },
            dismissButton = {
                TextButton(onClick = { postDestinationWarning = null }) { Text("キャンセル") }
            }
        )
    }
    if (toolbarOverflowOpen) {
        SecondaryToolbarOverflowPopup(
            surface = CompatToolbarSurface.POST,
            items = toolbarItems,
            actions = resolvedPostActions,
            onDismiss = { toolbarOverflowOpen = false }
        )
    }
}

@Composable
fun CompatPostImageCompressConfirmation(
    onCompress: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("確認") },
        text = { Text("画像をリサイズしますか？") },
        confirmButton = {
            TextButton(onClick = onCompress) { Text("圧縮する") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("キャンセル") }
        }
    )
}

@Composable
private fun SecondaryToolbarOverflowPopup(
    surface: CompatToolbarSurface,
    items: List<CompatToolbarItem>,
    actions: Map<String, () -> Unit>,
    onDismiss: () -> Unit
) {
    val inactive = items.sortedBy(CompatToolbarItem::position).filterNot(CompatToolbarItem::active)
    CompatBottomPopup(
        alignment = Alignment.BottomEnd,
        testTag = "compat-post-toolbar-overflow-popup",
        onDismiss = onDismiss
    ) {
        if (inactive.isEmpty()) Text("ツールバー外の操作はありません")
        inactive.forEach { item ->
            TextButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                enabled = actions[item.key] != null,
                onClick = {
                    onDismiss()
                    actions[item.key]?.invoke()
                }
            ) {
                // sample/1.apk inflates a native PopupMenu here.  Its rows are
                // text-only; placing the 192px toolbar artwork in this menu
                // made every row and the whole popup several times too large.
                Text(
                    text = compatToolbarLabel(surface, item.key),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}
