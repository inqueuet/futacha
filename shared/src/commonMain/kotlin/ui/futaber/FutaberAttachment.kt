package com.valoser.futacha.shared.ui.futaber

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.valoser.futacha.shared.network.BoardPostingCapabilities
import com.valoser.futacha.shared.ui.board.isVideoAttachmentName
import com.valoser.futacha.shared.ui.compat.CompatPostAttachmentDecision
import com.valoser.futacha.shared.ui.compat.compatPostAttachmentDecisionMessage
import com.valoser.futacha.shared.ui.compat.compressCompatPostImage
import com.valoser.futacha.shared.ui.compat.decideCompatPostAttachment
import com.valoser.futacha.shared.util.ImageData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.max

internal const val FUTABER_REPLY_ATTACHMENT_REFUSED_MESSAGE =
    "この板の返信は通常の添付に対応していません。お手書きはお絵描きから選択できます"

/** What to do with a picked or pasted file for this board. */
internal sealed interface FutaberAttachmentVerdict {
    data object Accept : FutaberAttachmentVerdict

    /** Over the board's size limit: an image may be compressed to fit after the user agrees. */
    data object AskCompression : FutaberAttachmentVerdict

    data class Reject(val message: String) : FutaberAttachmentVerdict
}

internal fun futaberAttachmentVerdict(
    image: ImageData,
    capabilities: BoardPostingCapabilities,
    isReply: Boolean
): FutaberAttachmentVerdict {
    if (isReply && !capabilities.replyAttachmentsAllowed && !image.isHandwriting) {
        return FutaberAttachmentVerdict.Reject(FUTABER_REPLY_ATTACHMENT_REFUSED_MESSAGE)
    }
    val limit = capabilities.maxFileSizeBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    return when (val decision = decideCompatPostAttachment(image, limit, capabilities.supportedExtensions)) {
        CompatPostAttachmentDecision.Accept -> FutaberAttachmentVerdict.Accept
        CompatPostAttachmentDecision.AskImageCompression -> FutaberAttachmentVerdict.AskCompression
        else -> FutaberAttachmentVerdict.Reject(compatPostAttachmentDecisionMessage(decision, image.fileName, limit) ?: "このファイルは添付できません")
    }
}

/**
 * The file attached to the post being written. A new pick replaces the old one; a slower
 * earlier compression never overwrites a newer pick. Location data is stripped from images
 * (as the other modes do by default).
 */
@Stable
internal class FutaberAttachment(private val scope: CoroutineScope) {
    var image by mutableStateOf<ImageData?>(null)
        private set
    var processing by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    var pendingCompression by mutableStateOf<ImageData?>(null)
        private set
    private var job: Job? = null

    fun clear() {
        job?.cancel()
        job = null
        processing = false
        image = null
        pendingCompression = null
        error = null
    }

    fun clearError() {
        error = null
    }

    fun cancelCompression() {
        pendingCompression = null
    }

    /** A file the user picked or pasted. */
    fun offer(picked: ImageData, capabilities: BoardPostingCapabilities, isReply: Boolean) {
        error = null
        when (val verdict = futaberAttachmentVerdict(picked, capabilities, isReply)) {
            FutaberAttachmentVerdict.Accept -> finish(picked)
            FutaberAttachmentVerdict.AskCompression -> pendingCompression = picked
            is FutaberAttachmentVerdict.Reject -> error = verdict.message
        }
    }

    /** The user agreed to compress [picked] to the board's limit. */
    fun compress(picked: ImageData, capabilities: BoardPostingCapabilities) {
        pendingCompression = null
        process("圧縮できませんでした") {
            compressCompatPostImage(picked, capabilities.maxFileSizeBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        }
    }

    private fun finish(picked: ImageData) {
        if (picked.isHandwriting || picked.fileName.isVideoAttachmentName()) {
            job?.cancel()
            processing = false
            image = picked
            return
        }
        process("画像の位置情報を削除できませんでした") {
            compressCompatPostImage(picked, max(picked.bytes.size, 8 * 1024 * 1024))
        }
    }

    private fun process(failureMessage: String, block: suspend () -> Result<ImageData>) {
        job?.cancel()
        processing = true
        val launched = scope.launch {
            val self = coroutineContext[Job]
            try {
                val result = block()
                if (job !== self) return@launch
                result.onSuccess { image = it }.onFailure { error = it.message ?: failureMessage }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (job === self) error = failure.message ?: failureMessage
            } finally {
                if (job === self) {
                    job = null
                    processing = false
                }
            }
        }
        job = launched
    }
}
