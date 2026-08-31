package com.valoser.futacha.shared.network

import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.CatalogFetchSettings
import com.valoser.futacha.shared.media.FUTABA_IMAGE_EXTENSIONS
import com.valoser.futacha.shared.media.FUTABA_VIDEO_EXTENSIONS

data class BoardEndpoint(
    val catalog: String,
    val thread: String
)

data class BoardPostingCapabilities(
    val maxFileSizeBytes: Long,
    val supportedExtensions: Set<String>
)

private val CURRENT_FUTABA_IMAGE_EXTENSIONS = FUTABA_IMAGE_EXTENSIONS - "jpe"
private val CURRENT_FUTABA_VIDEO_EXTENSIONS = FUTABA_VIDEO_EXTENSIONS

/**
 * Conservative current-server fallback used only when the posting form cannot be fetched.
 * Live forms currently use 8,192,000 bytes for may/b and 3,072,000 bytes elsewhere. img/b
 * advertises static images only; other observed boards advertise WebM/MP4 as well. The live
 * form wins whenever it is available.
 */
fun defaultBoardPostingCapabilities(board: String): BoardPostingCapabilities {
    val normalized = BoardUrlResolver.resolveBoardBaseUrl(board).lowercase()
    val isImgBoard = normalized == "https://img.2chan.net/b" ||
        normalized.startsWith("https://img.2chan.net/b/") ||
        normalized == "http://img.2chan.net/b" ||
        normalized.startsWith("http://img.2chan.net/b/")
    val isMayB = normalized == "https://may.2chan.net/b" ||
        normalized.startsWith("https://may.2chan.net/b/") ||
        normalized == "http://may.2chan.net/b" ||
        normalized.startsWith("http://may.2chan.net/b/")
    return BoardPostingCapabilities(
        maxFileSizeBytes = if (isMayB) 8_192_000L else 3_072_000L,
        supportedExtensions = if (isImgBoard) {
            CURRENT_FUTABA_IMAGE_EXTENSIONS - "webp"
        } else {
            CURRENT_FUTABA_IMAGE_EXTENSIONS + CURRENT_FUTABA_VIDEO_EXTENSIONS
        }
    )
}

internal fun resolveBoardPostingCapabilities(
    board: String,
    serverMaxFileSizeBytes: Long?,
    serverSupportedExtensions: Set<String>
): BoardPostingCapabilities {
    val fallback = defaultBoardPostingCapabilities(board)
    val maxBytes = serverMaxFileSizeBytes
        ?.takeIf { it in 1L..32_000_000L }
        ?: fallback.maxFileSizeBytes
    val normalizedServerExtensions = serverSupportedExtensions
        .asSequence()
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }
        .flatMap { extension ->
            when (extension) {
                "jpg", "jpeg" -> sequenceOf("jpg", "jpeg")
                else -> sequenceOf(extension)
            }
        }
        .toSet()
    return BoardPostingCapabilities(
        maxFileSizeBytes = maxBytes,
        // The visible form list omits WebP even though current live posts use it. Do not
        // otherwise union fallback video types into a live image-only form such as img/b.
        supportedExtensions = if (normalizedServerExtensions.isEmpty()) {
            fallback.supportedExtensions
        } else {
            normalizedServerExtensions + fallback.supportedExtensions.filter { it == "webp" }
        }
    )
}

interface BoardApi {
    /**
     * Fetches catalog setup page to initialize cookies (posttime, ptmt, cxyl, etc.)
     * This should be called before any catalog operations to ensure proper cookie setup.
     */
    suspend fun fetchCatalogSetup(
        board: String,
        settings: CatalogFetchSettings = CatalogFetchSettings()
    )

    suspend fun fetchPostingCapabilities(board: String): BoardPostingCapabilities =
        defaultBoardPostingCapabilities(board)

    suspend fun fetchCatalog(
        board: String,
        mode: CatalogMode = CatalogMode.default
    ): String
    suspend fun fetchThreadHead(board: String, threadId: String, maxLines: Int = 65): String
    suspend fun fetchThread(board: String, threadId: String): String
    suspend fun fetchThreadByUrl(threadUrl: String): String
    suspend fun probeThreadExists(threadUrl: String): Boolean {
        fetchThreadByUrl(threadUrl)
        return true
    }
    /** Lightweight HEAD probe which is true only for an explicit 404/410. */
    suspend fun probeThreadGone(threadUrl: String): Boolean = false
    suspend fun voteSaidane(board: String, threadId: String, postId: String)
    suspend fun requestDeletion(board: String, threadId: String, postId: String, reasonCode: String)
    suspend fun deleteByUser(
        board: String,
        threadId: String,
        postId: String,
        password: String,
        imageOnly: Boolean
    )
    suspend fun replyToThread(
        board: String,
        threadId: String,
        name: String,
        email: String,
        subject: String,
        comment: String,
        password: String,
        imageFile: ByteArray?,
        imageFileName: String?,
        textOnly: Boolean
    ): String?
    suspend fun createThread(
        board: String,
        name: String,
        email: String,
        subject: String,
        comment: String,
        password: String,
        imageFile: ByteArray?,
        imageFileName: String?,
        textOnly: Boolean
    ): String?
}
