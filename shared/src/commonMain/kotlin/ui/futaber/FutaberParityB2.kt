package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import com.valoser.futacha.shared.ui.board.CookiePostingRecoveryGuidance
import com.valoser.futacha.shared.ui.board.buildCookiePostingRecoveryFallbackGuidance
import com.valoser.futacha.shared.ui.board.buildCookiePostingRecoveryGuidance
import com.valoser.futacha.shared.ui.board.rememberAttachmentPickerLauncher
import com.valoser.futacha.shared.ui.board.shouldOfferCookieManagerForPostingFailure
import com.valoser.futacha.shared.ui.compat.COMPAT_DRAWING_HELP_SECTION
import com.valoser.futacha.shared.ui.compat.CompatPostDrawingScreen
import com.valoser.futacha.shared.ui.compat.CompatUpsUploadDialog
import com.valoser.futacha.shared.ui.compat.CompatibilityPalette
import com.valoser.futacha.shared.ui.compat.LocalCompatibilityPalette
import com.valoser.futacha.shared.ui.compat.appendCompatPostText
import com.valoser.futacha.shared.ui.compat.compatPostDeviceInfo
import com.valoser.futacha.shared.ui.compat.compatPreferenceValue
import com.valoser.futacha.shared.ui.compat.fetchCompatPostNetworkInfo
import com.valoser.futacha.shared.ui.compat.isCompatUpsUploadSizeAllowed
import com.valoser.futacha.shared.ui.compat.normalizeCompatSpeechResult
import com.valoser.futacha.shared.ui.compat.parseCompatSaveLocation
import com.valoser.futacha.shared.ui.compat.launchCompatDrawingCopy
import com.valoser.futacha.shared.ui.compat.rememberCompatSpeechRecognizer
import com.valoser.futacha.shared.ui.compat.uploadCompatUps
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.ImageData
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/*
 * The B2 batch of the mode parity work (docs/mode-feature-parity-plan-2026-10-12.md §5.3): the posting aids of the other
 * modes on the write screen.
 *  - P10 手書き: the shared drawing screen, opened from the attach button. A reply on a board whose replies take no file
 *    (img's) gets it by a plain tap even with the extension off (otherwise that reply has no way to attach anything).
 *  - P11 あぷ小・音声入力・回線情報・機種情報, and the Cookie guidance after a failed post: behind the extension
 *    "書き込みツール" (off by default). L-B8: the tools are at the end of the camera's black menu (the long press is gone).
 * Nothing new is sent: the shared upload, network-information and drawing functions are called as the other modes call them.
 */

private const val PARITY_B2_TAG = "FutaberParityB2"

/** What the write screen's tools need from the mode (all optional: without them only the drawing is offered). */
internal data class FutaberPostTools(
    /** The extension "書き込みツール" (long press on the attach button, the Cookie guidance). */
    val enabled: Boolean = false,
    val httpClient: HttpClient? = null,
    val fileSystem: FileSystem? = null,
    /** "手書きファイルの保存先" of the shared settings: a copy of each drawing goes there when it is set. */
    val drawingCopyLocation: SaveLocation? = null,
    val appVersion: String = "1.0",
    val cookieRepository: CookieRepository? = null,
    /** Opens the Cookie management page; null when there is none. */
    val openCookies: (() -> Unit)? = null,
    val pickerPreference: AttachmentPickerPreference = AttachmentPickerPreference.MEDIA,
    val preferredFileManagerPackage: String? = null
)

/** The shared "手書きファイルの保存先" (as the other modes read it). */
internal fun futaberDrawingCopyLocation(preferences: Map<String, String>): SaveLocation? =
    parseCompatSaveLocation(preferences.compatPreferenceValue("storage", "dummyDrawingDir", "手書きファイルの保存先"))

// L-B8 (M37): the attach button's tap opens the camera's black menu (FutaberPostTools.kt: futaberCameraMenu). The tools
// that the long press opened before (with the extension 「書き込みツール」) are at the end of that menu, and 手書き is there
// on a board whose replies take no file (img's), as the tap offered it before.

/** The device line for a post: the same as ふたちゃ writes (this mode is part of the ふたちゃ app). */
internal fun futaberDeviceInfoText(platformText: String): String =
    platformText.replace("ふたば＠アプリ としあき(仮)", "ふたちゃ")

/** The network-information request names the app as ふたちゃ and としあき(仮) do. */
internal fun futaberNetworkInfoUserAgent(appVersion: String): String = "Futacha/$appVersion"

/** Whether a failed post should offer the Cookie guidance: the reason, or the error under it, says the Cookies need a look. */
internal fun futaberOffersCookieGuidance(failure: Throwable?): Boolean {
    if (failure == null) return false
    return shouldOfferCookieManagerForPostingFailure(failure) ||
        failure.cause?.let(::shouldOfferCookieManagerForPostingFailure) == true
}

/** The state of the tools of one write screen. */
@Stable
internal class FutaberPostToolsState {
    /** The camera's menu is open over this point (the camera button's top middle, in the root's coordinates); null = shut. */
    var cameraMenu by mutableStateOf<FutaberAnchor?>(null)
    /** Where the camera button is now (it moves with the keyboard's slide): the open menu follows it. */
    var cameraAnchor by mutableStateOf<FutaberAnchor?>(null)
    var drawing by mutableStateOf(false)
    var upsImage by mutableStateOf<ImageData?>(null)
    var upsComment by mutableStateOf("")
    var upsDeleteKey by mutableStateOf("")
    var busy by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    var cookieGuidanceOpen by mutableStateOf(false)
}

@Composable
internal fun rememberFutaberPostToolsState(): FutaberPostToolsState = remember { FutaberPostToolsState() }


/**
 * A chrome colour with white text at 4.5:1 or more in every theme: the shared drawing screen writes its top bar white,
 * and this mode's light bars are white too. The theme's own strong colours are tried first.
 */
internal fun futaberWhiteTextChrome(colors: FutaberColors): Color =
    listOf(colors.navBar, colors.iosBlue, colors.accent, colors.destructive)
        .firstOrNull { futaberContrastWithWhite(it) >= 4.5f } ?: Color(0xFF2B2F33)

internal fun futaberContrastWithWhite(color: Color): Float = 1.05f / (color.luminance() + 0.05f)

/** The palette of the shared drawing screen: this mode's colours, with a top bar its white text can be read on. */
internal fun futaberDrawingPalette(colors: FutaberColors): CompatibilityPalette =
    futaberMediaPalette(colors).copy(chrome = futaberWhiteTextChrome(colors), statusBarChrome = futaberWhiteTextChrome(colors))

/**
 * The sheet, the drawing screen, the あぷ小 dialog and the messages of the tools. Placed last inside the write screen's
 * full-screen box (the sheet covers it).
 */
@OptIn(ExperimentalTime::class)
@Composable
internal fun FutaberPostToolsHost(
    state: FutaberPostToolsState,
    tools: FutaberPostTools,
    handwritingOnly: Boolean,
    comment: String,
    onCommentChange: (String) -> Unit,
    deleteKey: String,
    onPickFile: () -> Unit,
    onAttach: (ImageData) -> Unit,
    /** The board's address, for the Cookie guidance (whether this board's posting Cookies are kept). */
    boardUrl: String = "",
    /** The camera menu's 最近の画像・ライブラリ (the OS photo picker, L-B8 M37). */
    onPickPhoto: () -> Unit = onPickFile
) {
    val colors = LocalFutaberColors.current
    val scope = rememberCoroutineScope()
    val appTasks = LocalFutaberAppTasks.current
    // Whether these tools are still on screen, for a late result (L39).
    val shown = remember { mutableStateOf(true) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { shown.value = false } }
    val latestComment by rememberUpdatedState(comment)
    val latestOnCommentChange by rememberUpdatedState(onCommentChange)
    fun perform(block: suspend () -> Unit) {
        if (state.busy) return
        state.busy = true
        scope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Throwable) {
                Logger.e(PARITY_B2_TAG, "A posting tool failed", failure)
                state.message = failure.message ?: "操作に失敗しました"
            } finally { state.busy = false }
        }
    }
    val speech = rememberCompatSpeechRecognizer(
        onResult = { latestOnCommentChange(appendCompatPostText(latestComment, normalizeCompatSpeechResult(it))) },
        onError = { state.message = it }
    )
    val pickUps = rememberAttachmentPickerLauncher(
        preference = tools.pickerPreference,
        preferredFileManagerPackage = tools.preferredFileManagerPackage,
        onSelectionError = { state.message = it },
        onImageSelected = {
            if (!isCompatUpsUploadSizeAllowed(it.bytes.size)) state.message = "あぷ小は3000KBまでです"
            else {
                state.upsComment = ""
                state.upsDeleteKey = deleteKey
                state.upsImage = it
            }
        }
    )
    fun run(id: String) {
        state.cameraMenu = null
        when (id) {
            "recent", "library" -> onPickPhoto()
            "file" -> onPickFile()
            "tool-drawing" -> state.drawing = true
            "tool-ups" -> pickUps()
            "tool-voice" -> speech()
            "tool-network-info" -> perform {
                val info = fetchCompatPostNetworkInfo(tools.httpClient, futaberNetworkInfoUserAgent(tools.appVersion))
                latestOnCommentChange(appendCompatPostText(latestComment, info))
            }
            "tool-device-info" -> latestOnCommentChange(
                appendCompatPostText(latestComment, futaberDeviceInfoText(compatPostDeviceInfo(tools.appVersion)))
            )
        }
    }
    state.cameraMenu?.let { opened ->
        val anchor = state.cameraAnchor ?: opened
        val entries = futaberCameraMenu(handwritingOnly, tools.enabled, canUpload = tools.httpClient != null)
        // The original's black menu over the camera (the keyboard stays up, as there): its items, then this app's tools.
        FutaberCalloutMenu(
            anchor = anchor,
            items = entries.map { entry ->
                FutaberCalloutItem(entry.id, entry.label, entry.enabled) {
                    // An img reply takes no file: the item says so (the picker would only be refused) and names the drawing.
                    if (entry.refused) { state.cameraMenu = null; state.message = FUTABER_REPLY_ATTACHMENT_REFUSED_MESSAGE }
                    else run(entry.id)
                }
            },
            onDismiss = { state.cameraMenu = null }
        )
    }
    if (state.drawing) {
        FutachaAppLockAwareWindow {
            Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                CompositionLocalProvider(LocalCompatibilityPalette provides remember(colors) { futaberDrawingPalette(colors) }) {
                    Surface(Modifier.fillMaxSize().testTag("futaber-post-drawing"), color = colors.background) {
                        CompatPostDrawingScreen(
                            onSaved = { image ->
                                onAttach(image)
                                state.drawing = false
                                // A copy for the person, only when "手書きファイルの保存先" is set; a failure keeps the attachment.
                                // Written afterwards on the app's scope (L39: the drawing screen waited for it).
                                tools.fileSystem?.let { fs ->
                                    launchCompatDrawingCopy(appTasks?.scope ?: scope, fs, tools.drawingCopyLocation, image,
                                        Clock.System.now().toEpochMilliseconds()) {
                                        Logger.w(PARITY_B2_TAG, "Failed to keep a copy of the drawing")
                                        val text = "手書き画像のコピーを保存できませんでした（添付はそのままです）"
                                        if (shown.value) state.message = text else appTasks?.notify?.invoke(text)
                                    }
                                }
                            },
                            onBack = { state.drawing = false },
                            // The reference help's 手書き section, not としあき(仮)'s whole help (L70).
                            helpSectionId = COMPAT_DRAWING_HELP_SECTION,
                            // A drawing lost with the app is offered back the next time (L69).
                            recoveryFileSystem = tools.fileSystem
                        )
                    }
                }
            }
        }
    }
    state.upsImage?.let { image ->
        CompositionLocalProvider(LocalCompatibilityPalette provides remember(colors) { futaberMediaPalette(colors) }) {
            CompatUpsUploadDialog(
                fileName = image.fileName,
                comment = state.upsComment,
                deleteKey = state.upsDeleteKey,
                onCommentChange = { state.upsComment = it },
                onDeleteKeyChange = { state.upsDeleteKey = it },
                onCancel = { state.upsImage = null },
                onSubmit = {
                    val client = tools.httpClient
                    if (client == null) {
                        state.upsImage = null
                        state.message = "通信機能を利用できません"
                    } else perform {
                        val uploadComment = state.upsComment
                        val uploadKey = state.upsDeleteKey
                        state.upsImage = null
                        val fileName = uploadCompatUps(client, image, uploadComment, uploadKey, tools.appVersion).getOrThrow()
                        latestOnCommentChange(appendCompatPostText(latestComment, fileName))
                        state.message = "$fileName をアップロードしました"
                    }
                }
            )
        }
    }
    if (state.busy) {
        FutachaAppLockAwareWindow {
            AlertDialog(
                onDismissRequest = {},
                containerColor = colors.bar,
                textContentColor = colors.body,
                text = { LinearProgressIndicator(Modifier.fillMaxWidth().testTag("futaber-post-tool-busy"), color = colors.accent) },
                confirmButton = {}
            )
        }
    }
    state.message?.let { text ->
        FutachaAppLockAwareWindow {
            AlertDialog(
                onDismissRequest = { state.message = null },
                containerColor = colors.bar,
                textContentColor = colors.body,
                text = { Text(text, modifier = Modifier.testTag("futaber-post-tool-message")) },
                confirmButton = { TextButton(onClick = { state.message = null }) { Text("閉じる", color = colors.link) } }
            )
        }
    }
    if (state.cookieGuidanceOpen) {
        FutaberCookieGuidanceDialog(
            repository = tools.cookieRepository,
            boardUrl = boardUrl,
            onOpenCookies = tools.openCookies,
            onDismiss = { state.cookieGuidanceOpen = false }
        )
    }
}

/**
 * Under the reason of a failed post: "Cookieを確認" when the reason says the Cookies need a look (the extension
 * "書き込みツール" on, and a Cookie page to open). The guidance is the one ふたちゃ shows.
 */
@Composable
internal fun FutaberCookieGuidanceRow(state: FutaberPostToolsState, tools: FutaberPostTools, failure: Throwable?) {
    if (!tools.enabled || tools.openCookies == null || !futaberOffersCookieGuidance(failure)) return
    val colors = LocalFutaberColors.current
    TextButton(
        onClick = { state.cookieGuidanceOpen = true },
        modifier = Modifier.padding(horizontal = 4.dp).testTag("futaber-post-cookie")
    ) { Text("Cookieを確認", color = colors.link, fontSize = 14.sp) }
}

/** ふたちゃ's Cookie guidance (the same wording), in this mode's colours; nothing is counted or sent. */
@Composable
private fun FutaberCookieGuidanceDialog(
    repository: CookieRepository?,
    boardUrl: String?,
    onOpenCookies: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val colors = LocalFutaberColors.current
    var guidance by remember { mutableStateOf<CookiePostingRecoveryGuidance?>(null) }
    LaunchedEffect(repository, boardUrl) {
        guidance = if (repository == null) buildCookiePostingRecoveryFallbackGuidance() else runSuspendCatchingPreservingCancellation {
            val hasAny = repository.listCookies().isNotEmpty()
            val hasForBoard = boardUrl?.takeIf { it.isNotBlank() }?.let {
                repository.hasValidCookieFor(it, preferredNames = FUTABER_POSTING_COOKIE_NAMES)
            } ?: false
            buildCookiePostingRecoveryGuidance(hasAnyCookies = hasAny, hasPostingCookiesForBoard = hasForBoard)
        }.getOrElse { buildCookiePostingRecoveryFallbackGuidance() }
    }
    val shown = guidance ?: CookiePostingRecoveryGuidance("Cookieを確認しています", "保存済み Cookie の状態を確認しています。")
    FutachaAppLockAwareWindow {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = colors.bar,
            titleContentColor = colors.body,
            textContentColor = colors.body,
            modifier = Modifier.testTag("futaber-post-cookie-dialog"),
            title = { Text(shown.title) },
            text = { Text(shown.message) },
            confirmButton = {
                TextButton(
                    enabled = guidance != null && onOpenCookies != null,
                    onClick = { onDismiss(); onOpenCookies?.invoke() },
                    modifier = Modifier.testTag("futaber-post-cookie-open")
                ) { Text("Cookieを開く", color = colors.link) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("閉じる", color = colors.link) } }
        )
    }
}

/** The posting Cookies ふたちゃ's guidance looks for. */
private val FUTABER_POSTING_COOKIE_NAMES = setOf("posttime", "ptmt")
