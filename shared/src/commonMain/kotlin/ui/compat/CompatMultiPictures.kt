package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.repo.aimoge.AIMOGE_EXTRA_PICTURES_MAX
import com.valoser.futacha.shared.repo.aimoge.AimogePendingExtras
import com.valoser.futacha.shared.util.ImageData
import kotlinx.coroutines.CancellationException

/** How many pictures one pick may bring on a Futaba board (1 attachment and the rest to あぷ小, one by one). */
internal const val COMPAT_MULTI_PICK_FUTABA_MAX = 10

internal const val COMPAT_MULTI_PICK_LABEL = "画像を複数選択"

internal fun compatMultiPickMax(isAimoge: Boolean): Int = if (isAimoge) AIMOGE_EXTRA_PICTURES_MAX + 1 else COMPAT_MULTI_PICK_FUTABA_MAX

/** Where each picked picture goes. */
internal data class CompatPictureSplit(
    /** Becomes the post's attachment (null: the post already has one, or the board takes none). */
    val attachment: ImageData?,
    /** Futaba: uploaded to あぷ小 in turn, their names added to the body. */
    val uploads: List<ImageData>,
    /** あいもげ: sent with the post as its 2nd and 3rd pictures. */
    val aimogeExtras: List<ImageData>,
    /** Left out over the limit. */
    val dropped: Int
)

internal fun splitCompatPictures(
    picked: List<ImageData>,
    current: ImageData?,
    attachmentAllowed: Boolean,
    isAimoge: Boolean,
    boardUrl: String = ""
): CompatPictureSplit {
    val takesFirst = current == null && attachmentAllowed
    val first = picked.firstOrNull()?.takeIf { takesFirst && !it.isHandwriting }
    val rest = if (first != null) picked.drop(1) else picked
    if (isAimoge) {
        // The 2nd and 3rd ride with a picture: none without one.
        if (first == null && current == null) return CompatPictureSplit(null, emptyList(), emptyList(), picked.size)
        val already = if (first == null) AimogePendingExtras.of(boardUrl).size else 0
        val room = (AIMOGE_EXTRA_PICTURES_MAX - already).coerceAtLeast(0)
        return CompatPictureSplit(first, emptyList(), rest.take(room), (rest.size - room).coerceAtLeast(0))
    }
    val room = COMPAT_MULTI_PICK_FUTABA_MAX - (if (first != null) 1 else 0)
    return CompatPictureSplit(first, rest.take(room), emptyList(), (rest.size - room).coerceAtLeast(0))
}

/**
 * Applies a pick: the first becomes the attachment, the rest go to あぷ小 (Futaba) one after another, or are held to
 * go with the あいもげ post. Returns the message to show. A failed upload stops nothing: the names that went up stay.
 */
internal suspend fun applyCompatPickedPictures(
    picked: List<ImageData>,
    current: ImageData?,
    attachmentAllowed: Boolean,
    isAimoge: Boolean,
    boardUrl: String,
    setAttachment: (ImageData) -> Unit,
    upload: (suspend (ImageData) -> String)?,
    appendName: (String) -> Unit
): String {
    val split = splitCompatPictures(picked, current, attachmentAllowed, isAimoge, boardUrl)
    split.attachment?.let(setAttachment)
    val parts = mutableListOf<String>()
    if (split.attachment != null) parts += "1枚目を添付しました"
    if (isAimoge) {
        val first = split.attachment ?: current
        if (first != null && split.aimogeExtras.isNotEmpty()) {
            val kept = if (split.attachment != null) emptyList() else AimogePendingExtras.of(boardUrl)
            AimogePendingExtras.set(boardUrl, kept + split.aimogeExtras.map { it.bytes to it.fileName })
            parts += "ほか${split.aimogeExtras.size}枚をあいもげへ一緒に送ります（合わせて${1 + AimogePendingExtras.of(boardUrl).size}枚）"
        } else if (split.attachment != null) AimogePendingExtras.clear()
        if (first == null) parts += "あいもげでは2枚目からは1枚目の添付と一緒に送ります"
    } else if (split.uploads.isNotEmpty()) {
        if (upload == null) {
            parts += "あぷ小へ上げられないため、2枚目からは使いません"
        } else {
            var failed = 0
            var done = 0
            for (image in split.uploads) {
                try {
                    val prepared = prepareCompatUpsUpload(image).getOrThrow()
                    appendName(upload(prepared.image))
                    done++
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    failed++
                }
            }
            if (done > 0) parts += "${done}枚をあぷ小へ上げ、本文にファイル名を足しました"
            if (failed > 0) parts += "${failed}枚はあぷ小へ上げられませんでした"
        }
    }
    if (split.dropped > 0) parts += "${split.dropped}枚は数の上限を超えたので使いません"
    return parts.joinToString("。").ifEmpty { "画像を使えませんでした" }
}
