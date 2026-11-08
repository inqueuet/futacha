package com.valoser.futacha.shared.ui.ftutter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valoser.futacha.shared.network.NetworkException

/**
 * T-B3：書き込み画面と送信を原作（KakikomiDialog）の形にするための決まり。
 *
 * - 画面は下付きの全幅シート。送信中は全体を暗くして全幅の帯にスピナー。
 * - 結果はシートを閉じて、サーバーの本文をそのままトーストで出す（本文は下書きに残る）。
 * - 今までの中央のダイアログと説明の文は「従来の動き」のスイッチで残す。
 */
internal val FTUTTER_LEGACY_POST_DIALOG_KEY: String get() = FtutterKeys.PREFIX + "legacyPostDialog"
internal const val FTUTTER_LEGACY_POST_DIALOG_LABEL = "書き込みを中央のダイアログで出す"
internal const val FTUTTER_LEGACY_POST_DIALOG_NOTE =
    "ONにすると、書き込みを前の中央のダイアログで出し、送信に失敗しても閉じずに理由の説明を出します。OFFは原作と同じ下付きのシートで、結果をサーバーの本文のまま出します"

internal fun ftutterLegacyPostDialog(preferences: Map<String, String>): Boolean =
    preferences.ftutterBool(FTUTTER_LEGACY_POST_DIALOG_KEY, false)

/** 原作が「ipアドレスからは投稿できません」の後に続けて出す案内（M37）。 */
/** 続けて出す案内までの間（原作のトーストの長さ）。 */
internal const val FTUTTER_POST_FOLLOW_UP_DELAY_MS = 2_500L
internal const val FTUTTER_IP_REFUSAL_FOLLOW_UP = "初回書き込みの場合、1時間後に書き込めるようになります"
private const val FTUTTER_IP_REFUSAL_MARK = "ipアドレスからは投稿できません"

/** 失敗のトースト：[text]を出し、[followUp]があれば続けて出す。 */
internal data class FtutterPostToast(val text: String, val followUp: String? = null)

/** 失敗の元をたどって、サーバーが返した本文（読めた時だけ）。 */
internal fun ftutterServerBody(error: Throwable): String? {
    var current: Throwable? = error
    var depth = 0
    while (current != null && depth < 8) {
        (current as? NetworkException)?.serverBody?.let { return it }
        current = current.cause
        depth++
    }
    return null
}

/**
 * 送信に失敗した時のトースト（H11・M87）。サーバーの本文がある時はそのまま（HTMLもJSONもそのまま、空なら空）。
 * 本文が無い失敗（送る前の確認で止めた、通信が届かなかった等）はこちらの理由の文。
 */
internal fun ftutterPostFailureToast(error: Throwable): FtutterPostToast {
    val body = ftutterServerBody(error)
    if (body != null) {
        val text = body.trim()
        return FtutterPostToast(text, FTUTTER_IP_REFUSAL_FOLLOW_UP.takeIf { text.contains(FTUTTER_IP_REFUSAL_MARK) })
    }
    return FtutterPostToast(error.message?.takeIf { it.isNotBlank() } ?: "書き込みに失敗しました")
}

/** 原作の添付で選べる形式（M89。Dartの`file_picker`の FileType.custom）。 */
internal val FTUTTER_ATTACHMENT_EXTENSIONS = listOf("jpg", "jpeg", "png", "gif", "webp", "mp4", "webm")
internal const val FTUTTER_ATTACHMENT_EXTENSION_REFUSED = "jpg・jpeg・png・gif・webp・mp4・webmのファイルを選んでください"

internal fun ftutterAttachmentExtensionAllowed(fileName: String): Boolean {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return extension in FTUTTER_ATTACHMENT_EXTENSIONS
}

/**
 * 端末情報の文（M47）。原作は「ふたったー ver3.1.1/Android/<機種>/<OSの版>」。[platformInfo]は共有の
 * `compatPostDeviceInfo`の文（「ふたば＠アプリ としあき(仮) <版> <メーカー>/<機種>/<OSの版>」）。
 */
internal fun ftutterDeviceInfoText(appVersion: String, platformInfo: String): String {
    val platform = platformInfo.substringAfter(' ').let { rest ->
        // 「としあき(仮) <版> 」までを外す。
        val afterName = rest.substringAfter(' ', rest)
        val version = appVersion.trim()
        if (version.isNotEmpty() && afterName.startsWith("$version ")) afterName.removePrefix("$version ") else afterName
    }
    val parts = platform.split('/').map(String::trim).filter(String::isNotEmpty)
    val ios = parts.lastOrNull()?.startsWith("iOS ") == true
    val model = parts.getOrNull(1) ?: parts.firstOrNull().orEmpty()
    val release = parts.lastOrNull()?.removePrefix("iOS ").orEmpty()
    return buildString {
        append("ふたちゃ ver").append(appVersion.trim())
        append('/').append(if (ios) "iOS" else "Android")
        if (model.isNotEmpty()) append('/').append(model)
        if (release.isNotEmpty() && parts.size > 1) append('/').append(release)
    }
}

/** 削除キーの残りの文字数（M48。原作の読み上げ「残り N 文字（半角相当）」）。 */
internal fun ftutterDeleteKeyRemaining(value: String): Int = (FTUTTER_DELETE_KEY_MAX - value.length).coerceAtLeast(0)
internal const val FTUTTER_DELETE_KEY_MAX = 8
internal fun ftutterDeleteKeyRemainingLabel(value: String): String = "残り ${ftutterDeleteKeyRemaining(value)} 文字（半角相当）"

/**
 * 原作の書き込みのシート（H3）：下付きの全幅、上の段に入力、線の下の帯に︙・あぷ小・塗りの「書き込む」。開くと本文に
 * キーボードを出す。外を押すと閉じる（下書きは残す）。送信中（M17）はシートを隠し、全体を暗くして全幅の帯にスピナー。
 */
@androidx.compose.runtime.Composable
internal fun FtutterPostSheet(
    sending: Boolean,
    onDismiss: () -> Unit,
    fields: @androidx.compose.runtime.Composable androidx.compose.foundation.layout.ColumnScope.(androidx.compose.ui.focus.FocusRequester) -> Unit,
    bar: @androidx.compose.runtime.Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    val colors = LocalFtutterColors.current
    val focus = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    androidx.compose.runtime.LaunchedEffect(sending) { if (sending) keyboard?.hide() }
    com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow {
        androidx.compose.ui.window.Dialog(onDismissRequest = { if (!sending) onDismiss() }, properties = ftutterSheetDialogProperties()) {
            val noRipple = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                // 外の暗い所（押すと閉じる）は、シート・帯の後ろの別の層にする（中に入れると、帯やシートの読み上げが
                // 「閉じる」にまとめられた）。
                androidx.compose.foundation.layout.Box(
                    androidx.compose.ui.Modifier.fillMaxSize()
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = FTUTTER_POPUP_SCRIM_ALPHA))
                        .clickable(interactionSource = noRipple, indication = null) { if (!sending) onDismiss() }
                        .semantics { contentDescription = "閉じる" }
                        .testTag("ftutter-post-scrim")
                )
                if (sending) {
                    androidx.compose.foundation.layout.Box(
                        androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.Center).fillMaxWidth()
                            .background(colors.popUp, androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                            .padding(vertical = 20.dp).testTag("ftutter-post-sending"),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) { androidx.compose.material3.CircularProgressIndicator() }
                } else {
                    androidx.compose.foundation.layout.Column(
                        androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.BottomCenter).fillMaxWidth()
                            .imePadding().navigationBarsPadding()
                            .background(colors.popUp, androidx.compose.foundation.shape.RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                            // シートの中の押しは外の暗い所へ通さない。読み上げで名前の無いボタンにならないよう、
                            // clickable ではなく押しを受け止めるだけにする。
                            .pointerInput(Unit) { detectTapGestures { } }
                            .testTag("ftutter-post-dialog")
                    ) {
                        androidx.compose.foundation.layout.Column(
                            androidx.compose.ui.Modifier.weight(1f, fill = false)
                                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                                .padding(start = 10.dp, end = 10.dp, top = 10.dp),
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
                        ) { fields(focus) }
                        androidx.compose.material3.HorizontalDivider(androidx.compose.ui.Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                        androidx.compose.foundation.layout.Row(
                            androidx.compose.ui.Modifier.fillMaxWidth().padding(start = 4.dp, end = 10.dp, bottom = 6.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) { bar() }
                    }
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        // 原作は開くとすぐ本文にキーボードが出る。
                        runCatching { focus.requestFocus() }
                    }
                }
            }
        }
    }
}

/** ︙の2行の項目（原作：「削除キー」の下に値、「端末情報」の下に文）。 */
@androidx.compose.runtime.Composable
internal fun FtutterMenuTwoLines(title: String, value: String) {
    androidx.compose.foundation.layout.Column {
        androidx.compose.material3.Text(title)
        androidx.compose.material3.Text(value, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}
