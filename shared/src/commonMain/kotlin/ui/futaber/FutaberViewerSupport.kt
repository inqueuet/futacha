package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.ui.board.messageHtmlToPlainText

/**
 * L-B11: the pure parts of futaber's picture list and viewer (the list's title, the viewer's caption, the control that
 * saves to a storage provider, the name a shared file keeps, and the movement that cancels a long press).
 */

/** V18: the picture list's title as futaber writes it ("2 photos"). A single picture reads "1 photo" (provisional: only "2 photos" was seen). */
internal fun futaberGalleryTitle(count: Int): String = if (count == 1) "1 photo" else "$count photos"

/** V19: the caption's third line: the post's text lines joined by a space (an empty one adds nothing), the end cut with 「…」 by the Text. */
internal fun futaberViewerBodyLine(messageHtml: String): String =
    messageHtmlToPlainText(messageHtml).lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")

/**
 * C27・F15: the control of the viewer that saves the picture to a storage provider (Dropbox, Google Drive, ... through the
 * system's document picker). [None] keeps the viewer as it was: the plain save and share. The others hand that control over
 * to the picker: the 保存 button, the 共有 (action) button, or a long press on the picture.
 */
internal enum class FutaberStorageButton(val persisted: String, val label: String) {
    None("none", "割り当てなし"),
    Save("save", "画像保存ボタン"),
    Action("action", "アクションボタン"),
    LongPress("longpress", "長押し");

    companion object {
        /** A missing or damaged stored value reads as [None]. */
        fun from(stored: String?): FutaberStorageButton = entries.firstOrNull { it.persisted == stored } ?: None
    }
}

/** What the explanation under the storage-provider choice says (futaber's, as seen on its page). */
internal const val FUTABER_STORAGE_PROVIDER_NOTE =
    "DropboxやGoogleDrive等のストレージプロバイダ対応アプリに画像を保存できます。選んだボタンを押すと、保存先を選ぶ画面が開きます。"

/** C28's and C29's rows of the page. */
internal const val FUTABER_VIEWER_PORTRAIT_LOCK_LABEL = "縦向きに固定"
internal const val FUTABER_VIEWER_AUTOPLAY_LABEL = "動画を自動再生"

/**
 * M48: the name a shared (or exported) picture keeps: the file's own name in its address ("1792153182260.jpg"), which is
 * what the share sheet's title shows. Null when the address has none (the saver then names it as before).
 */
internal fun futaberViewerFileName(mediaUrl: String): String? =
    mediaUrl.substringBefore('#').substringBefore('?').trimEnd('/').substringAfterLast('/')
        .takeIf { it.isNotBlank() && '.' in it && it.length <= 120 }

/**
 * G27: a long press is only a long press where the finger stayed. Compose's long-press detector keeps waiting while the
 * finger moves (nobody consumes a vertical move over the pager), so a slow swipe down was taken as a long press and
 * opened the share sheet.
 */
internal fun futaberLongPressAllowed(movedPx: Float, slopPx: Float): Boolean = movedPx <= slopPx

/**
 * C27・F15: opens the system's "save to" picker for a file already saved in the app: Android's CreateDocument, iOS's document
 * picker for exporting. Call it with the file's path, the name to offer and the end ([success] false for a cancel is not told:
 * only a failed write is).
 */
@androidx.compose.runtime.Composable
internal expect fun rememberFutaberFileExporter(): (path: String, fileName: String, onDone: (success: Boolean) -> Unit) -> Unit

/**
 * C28: holds the picture list and the viewer upright while this is in the composition and [portrait] is true (Android's
 * requested orientation; iOS's host is told through a notification), and gives the orientation back when it leaves.
 */
@androidx.compose.runtime.Composable
internal expect fun ApplyFutaberViewerOrientation(portrait: Boolean)
