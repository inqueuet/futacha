package com.valoser.futacha.shared.ui.futaber.mht

import com.valoser.futacha.shared.compat.canonicalizeBoardUrl
import com.valoser.futacha.shared.media.isMemoryExhaustion
import com.valoser.futacha.shared.media.source.originalMediaSourceOrNull
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.ui.futaber.FutaberThreadRef
import com.valoser.futacha.shared.ui.futaber.futaberCompatBoardKey
import com.valoser.futacha.shared.ui.futaber.futaberHistoryThreadUrl
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.FileWriteSink
import com.valoser.futacha.shared.util.withMediaSaveSource
import io.ktor.client.HttpClient
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Folder of the saved files, inside the app's data folder (the share sheet can read it). */
internal const val FUTABER_MHT_DIRECTORY = "futaber_mht"

/**
 * Pictures taken out of a file to show it; rebuilt when a file is opened. It is in the app's private area
 * (not the one the files app shows or the backup takes), and only a few files' pictures are kept.
 */
internal const val FUTABER_MHT_CACHE_DIRECTORY = "private/futaber_mht_cache"

/**
 * Pictures of a file that was opened as a tab of としあき(仮): the tab's posts point at these copies, so they stay after
 * the file is deleted or another file is opened (a newer file's pictures push out the oldest ones only past
 * [MAX_TAB_MEDIA_FOLDERS] files).
 */
internal const val FUTABER_MHT_TAB_MEDIA_DIRECTORY = "private/futaber_mht_tab_media"

/** Where the pictures were kept before: in the data folder itself. Nothing refers to it any more; it is only cleaned up per file. */
private const val LEGACY_CACHE_DIRECTORY = "futaber_mht_cache"

private const val RECENT_FILE = ".recent"
private const val MAX_VIEW_CACHE_FOLDERS = 6
private const val MAX_TAB_MEDIA_FOLDERS = 20

private const val MAX_PICTURE_BYTES = 12L * 1024L * 1024L
private const val MAX_LIST_HEADER_BYTES = 160 * 1024
private const val MAX_LIST_THUMB_BYTES = 40 * 1024
private const val WRITE_SLICE_BYTES = 256 * 1024

private const val MEMORY_MESSAGE = "メモリが不足したため処理できませんでした。ファイルが大きすぎる可能性があります"

/** One file of the "MHTファイル" list: what the header says, without reading the whole file. */
internal data class FutaberMhtEntry(
    val fileName: String,
    val relativePath: String,
    val sizeBytes: Long,
    val title: String,
    val boardName: String,
    val boardKey: String?,
    val boardUrl: String?,
    val threadId: String,
    val savedAtMillis: Long,
    val postCount: Int,
    val withFullImages: Boolean,
    val thumbnail: ByteArray?
)

/** What to save: the thread's page, and the names the file carries (the board's key, name and address). */
internal data class FutaberMhtSaveRequest(
    val boardKey: String,
    val boardName: String,
    val boardUrl: String,
    val threadId: String,
    val title: String,
    /** The thread's own address on the web (the file's snapshot location and the first picture base). */
    val threadUrl: String,
    val page: ThreadPage
)

internal data class FutaberMhtSaveResult(
    val entry: FutaberMhtEntry,
    val pictureCount: Int,
    /** Pictures the file lacks: the download failed, or one was too big, or the file reached its size limit. */
    val missingPictures: Int
)

/** A file opened for reading: the thread, with its pictures written out and the posts pointing at them. */
internal class FutaberMhtOpened(
    val thread: FutaberMhtThread,
    /** The page, with each picture's address replaced by the path of its copy on this device. */
    val page: ThreadPage
)

internal class FutaberMhtLibrary(
    private val fileSystem: FileSystem,
    private val httpClient: HttpClient? = null,
    /** The most a saved file may be: what [open] can read. Only tests use another value. */
    private val maxFileBytes: Long = FUTABER_MHT_MAX_READ_BYTES
) {
    /**
     * Two libraries over the same file system and client are the same one: the library keeps nothing of its own, and
     * screens that keep their state per library must not lose it when a parent builds the library again.
     */
    override fun equals(other: Any?): Boolean =
        this === other || (other is FutaberMhtLibrary && other.fileSystem === fileSystem &&
            other.httpClient === httpClient && other.maxFileBytes == maxFileBytes)

    override fun hashCode(): Int = 31 * fileSystem.hashCode() + (httpClient?.hashCode() ?: 0)

    // -----------------------------------------------------------------------------------------------
    // Saving
    // -----------------------------------------------------------------------------------------------

    /**
     * Writes the thread as one MHT file. [fullImages] also takes the full-size pictures, not only the
     * thumbnails. A picture that cannot be fetched is left out and counted; the file is still written.
     */
    suspend fun save(
        board: BoardSummary,
        ref: FutaberThreadRef,
        page: ThreadPage,
        fullImages: Boolean,
        nowMillis: Long,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<FutaberMhtSaveResult> = save(
        FutaberMhtSaveRequest(
            boardKey = futaberCompatBoardKey(board),
            boardName = board.name,
            boardUrl = board.url,
            threadId = ref.threadId,
            title = ref.title,
            threadUrl = futaberHistoryThreadUrl(board, ref.threadId),
            page = page
        ),
        fullImages, nowMillis, onProgress
    )

    /**
     * The same, for a mode that has no [BoardSummary]: it names the board and the thread itself.
     *
     * Each picture is written as soon as it is fetched, so only one is in memory at a time. The file is written under
     * a temporary name and replaces an earlier file of the same thread only when it is complete; a failure or a
     * cancellation leaves the earlier file as it was. The file never grows past what [open] can read.
     */
    suspend fun save(
        request: FutaberMhtSaveRequest,
        fullImages: Boolean,
        nowMillis: Long,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<FutaberMhtSaveResult> = runCatchingMht {
        withContext(AppDispatchers.parsing) { saveNow(request, fullImages, nowMillis, onProgress) }
    }

    private suspend fun saveNow(
        request: FutaberMhtSaveRequest,
        fullImages: Boolean,
        nowMillis: Long,
        onProgress: (done: Int, total: Int) -> Unit
    ): FutaberMhtSaveResult {
        val client = httpClient ?: error("通信を初期化できませんでした")
        fileSystem.createDirectory(FUTABER_MHT_DIRECTORY).getOrThrow()
        val page = request.page
        val posts = page.posts
        val title = request.title.ifBlank { posts.firstOrNull()?.subject.orEmpty() }.ifBlank { "(無題)" }
        val threadUrl = request.threadUrl
        val fileName = chooseFileName(request.boardUrl, request.threadId)
        val relativePath = "$FUTABER_MHT_DIRECTORY/$fileName"

        val addresses = pictureAddresses(posts, fullImages)
        val firstThumb = posts.firstNotNullOfOrNull { post -> post.thumbnailUrl?.takeIf { it.isNotBlank() } }
        val mediaSource = client.originalMediaSourceOrNull()
        onProgress(0, addresses.size)
        // The first thumbnail goes into the header (the list shows it), so it is fetched before anything is written.
        val firstThumbBytes = firstThumb?.let { fetch(client, it, mediaSource, MAX_PICTURE_BYTES) }
        val thumbForList = firstThumbBytes?.takeIf { it.size <= MAX_LIST_THUMB_BYTES }

        val boundary = FutaberMhtWriter.newBoundary(nowMillis)
        val html = FutaberMhtThreadHtml.build(
            title, threadUrl, request.boardName, posts,
            FutaberMhtPageInfo(page.expiresAtLabel, page.deletedNotice, page.isTruncated, page.truncationReason)
        )
        val custom = buildList {
            add("Version" to "1")
            add("Board" to request.boardKey)
            add("Board-Name" to request.boardName)
            add("Board-Url" to request.boardUrl)
            add("Thread" to request.threadId)
            add("Posts" to posts.size.toString())
            add("Saved" to nowMillis.toString())
            add("Full-Images" to if (fullImages) "1" else "0")
            if (thumbForList != null) {
                @OptIn(ExperimentalEncodingApi::class)
                add("Thumb-Data" to Base64.Default.encode(thumbForList))
            }
        }
        val headerBytes = FutaberMhtWriter.header(boundary, title, rfc1123(nowMillis), threadUrl, custom)
        val htmlHead = FutaberMhtWriter.partHead(boundary, "text/html", threadUrl, "utf-8")
        val htmlBytes = html.encodeToByteArray()
        val endBytes = FutaberMhtWriter.end(boundary)
        val fixedBytes = headerBytes.size + htmlHead.size + FutaberMhtWriter.bodySize(htmlBytes.size) + endBytes.size
        if (fixedBytes > maxFileBytes) throw FutaberMhtFormatException("スレッドが大きすぎて保存できません")

        var pictureCount = 0
        var missing = 0
        val writtenPath = writeReplacing(relativePath) { sink ->
            pictureCount = 0
            missing = 0
            var written = 0L
            suspend fun put(bytes: ByteArray) {
                sink.write(bytes, 0, bytes.size)
                written += bytes.size
            }
            put(headerBytes)
            put(htmlHead)
            FutaberMhtWriter.writeBody(sink, htmlBytes)
            written += FutaberMhtWriter.bodySize(htmlBytes.size)
            addresses.forEachIndexed { index, address ->
                onProgress(index, addresses.size)
                val room = maxFileBytes - endBytes.size - written
                val bytes = if (address == firstThumb) {
                    firstThumbBytes
                } else {
                    fetch(client, address, mediaSource, minOf(MAX_PICTURE_BYTES, maxRawBytesFor(room - address.length)))
                }
                val head = bytes?.let { FutaberMhtWriter.partHead(boundary, mimeOf(address, it), address) }
                if (bytes == null || head == null || head.size + FutaberMhtWriter.bodySize(bytes.size) > room) {
                    // It could not be fetched, or it does not fit the file's size limit.
                    missing++
                    return@forEachIndexed
                }
                put(head)
                FutaberMhtWriter.writeBody(sink, bytes)
                written += FutaberMhtWriter.bodySize(bytes.size)
                pictureCount++
            }
            put(endBytes)
        }
        onProgress(addresses.size, addresses.size)
        val finalName = writtenPath.substringAfterLast('/')
        clearCaches(finalName)
        val size = fileSystem.getFileSize(writtenPath)
        return FutaberMhtSaveResult(
            entry = FutaberMhtEntry(
                fileName = finalName, relativePath = writtenPath, sizeBytes = size, title = title, boardName = request.boardName,
                boardKey = request.boardKey, boardUrl = request.boardUrl, threadId = request.threadId,
                savedAtMillis = nowMillis, postCount = posts.size, withFullImages = fullImages, thumbnail = thumbForList
            ),
            pictureCount = pictureCount,
            missingPictures = missing
        )
    }

    /** Writes [relativePath] so that an earlier file of that name is replaced only by a complete one. Returns the path written. */
    private suspend fun writeReplacing(relativePath: String, block: suspend (FileWriteSink) -> Unit): String =
        fileSystem.writeByteStreamReplacing(SaveLocation.Path(fileSystem.getAppDataDirectory()), relativePath, block).getOrThrow()

    /** At most [maxBytes] of the picture, or null: it could not be fetched, or it is bigger than that. */
    private suspend fun fetch(
        client: HttpClient,
        address: String,
        mediaSource: com.valoser.futacha.shared.media.source.OriginalMediaSource?,
        maxBytes: Long
    ): ByteArray? {
        if (maxBytes <= 0L) return null
        return try {
            withMediaSaveSource(client, fileSystem, address, mediaSource) { source ->
                if (source.declaredSize > maxBytes) return@withMediaSaveSource null
                val out = GrowableBytes(source.declaredSize.takeIf { it in 1..maxBytes }?.toInt() ?: (8 * 1024))
                val buffer = ByteArray(32 * 1024)
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    out.append(buffer, count)
                    if (out.size > maxBytes) return@withMediaSaveSource null
                }
                out.toByteArray()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    // -----------------------------------------------------------------------------------------------
    // Listing, importing, deleting
    // -----------------------------------------------------------------------------------------------

    suspend fun list(): List<FutaberMhtEntry> {
        if (!fileSystem.exists(FUTABER_MHT_DIRECTORY)) return emptyList()
        return fileSystem.listFiles(FUTABER_MHT_DIRECTORY)
            .map { it.substringAfterLast('/') }
            .filter { it.endsWith(".mht", ignoreCase = true) && !it.startsWith(".") }
            .mapNotNull { name -> readEntry("$FUTABER_MHT_DIRECTORY/$name") }
            .sortedByDescending { it.savedAtMillis }
    }

    /** Reads only the beginning of the file, where the header block is. */
    private suspend fun readEntry(relativePath: String): FutaberMhtEntry? {
        val size = fileSystem.getFileSize(relativePath)
        val head = fileSystem.readByteStream(relativePath) { source ->
            val out = GrowableBytes()
            val buffer = ByteArray(16 * 1024)
            while (out.size < MAX_LIST_HEADER_BYTES) {
                val count = source.read(buffer)
                if (count < 0) break
                if (count > 0) out.append(buffer, count)
                if (hasHeaderEnd(out)) break
            }
            out.toByteArray()
        }.getOrNull() ?: return null
        val headers = FutaberMhtReader.readHeaderBlock(head) ?: return null
        val fileName = relativePath.substringAfterLast('/')
        val doc = FutaberMhtDocument(headers, emptyList())
        return FutaberMhtEntry(
            fileName = fileName,
            relativePath = relativePath,
            sizeBytes = size,
            title = doc.subject ?: fileName.removeSuffix(".mht"),
            boardName = doc.custom("board-name") ?: "",
            boardKey = doc.custom("board"),
            boardUrl = doc.custom("board-url"),
            threadId = doc.custom("thread") ?: FutaberMhtThreadReader.threadIdFromUrl(headers["snapshot-content-location"]).orEmpty(),
            savedAtMillis = doc.custom("saved")?.toLongOrNull() ?: 0L,
            postCount = doc.custom("posts")?.toIntOrNull() ?: 0,
            withFullImages = doc.custom("full-images") == "1",
            thumbnail = doc.custom("thumb-data")?.let { data ->
                @OptIn(ExperimentalEncodingApi::class)
                runCatching { Base64.Default.decode(data.filterNot { it.isWhitespace() }) }.getOrNull()
            }
        )
    }

    private fun hasHeaderEnd(bytes: GrowableBytes): Boolean = bytes.indexOfBlankLine() >= 0

    /**
     * Takes a file from outside (the files app) into the list. The file is read as a whole first, so a file
     * that is not an MHT page of a thread is refused before anything is stored.
     */
    suspend fun import(bytes: ByteArray, originalName: String, nowMillis: Long): Result<FutaberMhtEntry> =
        runCatchingMht {
            if (bytes.size.toLong() > FUTABER_MHT_MAX_READ_BYTES) throw FutaberMhtFormatException(FUTABER_MHT_TOO_LARGE_MESSAGE)
            withContext(AppDispatchers.parsing) {
                val document = FutaberMhtReader.parse(bytes)
                val thread = FutaberMhtThreadReader.read(document)
                fileSystem.createDirectory(FUTABER_MHT_DIRECTORY).getOrThrow()
                val stem = sanitize(originalName.substringBeforeLast('.').ifBlank { "import-${thread.threadId}" })
                var name = "$stem.mht"
                var counter = 2
                while (fileSystem.exists("$FUTABER_MHT_DIRECTORY/$name")) name = "$stem-${counter++}.mht"
                // Written under a temporary name first, so a failure leaves no half file in the list.
                val relativePath = writeReplacing("$FUTABER_MHT_DIRECTORY/$name") { sink ->
                    var index = 0
                    while (index < bytes.size) {
                        val count = minOf(WRITE_SLICE_BYTES, bytes.size - index)
                        sink.write(bytes, index, count)
                        index += count
                    }
                }
                readEntry(relativePath) ?: FutaberMhtEntry(
                    fileName = relativePath.substringAfterLast('/'), relativePath = relativePath, sizeBytes = bytes.size.toLong(),
                    title = thread.title, boardName = thread.boardName.orEmpty(), boardKey = thread.boardKey, boardUrl = thread.boardUrl,
                    threadId = thread.threadId, savedAtMillis = nowMillis, postCount = thread.page.posts.size,
                    withFullImages = false, thumbnail = null
                )
            }
        }

    suspend fun delete(entry: FutaberMhtEntry): Result<Unit> = runCatchingMht {
        fileSystem.delete(entry.relativePath).getOrThrow()
        clearCaches(entry.fileName)
    }

    /** The absolute path, for the share sheet. */
    fun absolutePath(entry: FutaberMhtEntry): String = fileSystem.resolveAbsolutePath(entry.relativePath)

    // -----------------------------------------------------------------------------------------------
    // Opening
    // -----------------------------------------------------------------------------------------------

    /**
     * Reads the file, writes the pictures its posts use to a folder, and points the posts' pictures at those copies. The
     * file is taken apart off the main thread, and the result keeps no picture bytes.
     *
     * The folder is a cache of the viewer that is kept for a few files only. [keepForTab] writes to the folder of the
     * pictures that a tab of としあき(仮) refers to instead, which a newer file does not push out so soon.
     *
     * Only the pictures of the file itself, and the addresses of the official board hosts, are shown: the addresses of
     * other hosts are dropped, so opening a file never reaches out to wherever it says.
     */
    suspend fun open(entry: FutaberMhtEntry, keepForTab: Boolean = false): Result<FutaberMhtOpened> = runCatchingMht {
        if (!fileSystem.exists(entry.relativePath)) throw FutaberMhtFormatException("MHTファイルが見つかりません")
        if (fileSystem.getFileSize(entry.relativePath) > FUTABER_MHT_MAX_READ_BYTES) {
            throw FutaberMhtFormatException(FUTABER_MHT_TOO_LARGE_MESSAGE)
        }
        val bytes = fileSystem.readBytes(entry.relativePath).getOrThrow()
        withContext(AppDispatchers.parsing) { openBytes(entry, bytes, keepForTab) }
    }

    private suspend fun openBytes(entry: FutaberMhtEntry, bytes: ByteArray, keepForTab: Boolean): FutaberMhtOpened {
        val thread = FutaberMhtThreadReader.read(FutaberMhtReader.parse(bytes))
        val root = if (keepForTab) FUTABER_MHT_TAB_MEDIA_DIRECTORY else FUTABER_MHT_CACHE_DIRECTORY
        val stem = entry.fileName.removeSuffix(".mht")
        val folder = "$root/$stem"
        val used = LinkedHashSet<String>()
        thread.page.posts.forEach { post ->
            post.imageUrl?.let(used::add)
            post.thumbnailUrl?.let(used::add)
        }
        val local = HashMap<String, String>()
        try {
            if (fileSystem.exists(folder)) fileSystem.deleteRecursively(folder)
            fileSystem.createDirectory(folder).getOrThrow()
            var index = 0
            for (address in used) {
                val part = thread.pictures[address] ?: continue
                // Each picture is decoded for its own write; one that is broken is only left out.
                val body = part.bodyOrNull() ?: continue
                val path = "$folder/${index++}.${extensionOf(part.contentType)}"
                fileSystem.writeBytes(path, body).getOrThrow()
                local[address] = fileSystem.resolveAbsolutePath(path)
            }
        } catch (failure: Throwable) {
            withContext(NonCancellable) { if (fileSystem.exists(folder)) fileSystem.deleteRecursively(folder) }
            throw failure
        }
        val posts = thread.page.posts.map { post ->
            post.copy(
                imageUrl = shownAddress(post.imageUrl, local),
                thumbnailUrl = shownAddress(post.thumbnailUrl, local)
            )
        }
        val page = thread.page.copy(posts = posts)
        trimRecent(root, stem, if (keepForTab) MAX_TAB_MEDIA_FOLDERS else MAX_VIEW_CACHE_FOLDERS, dropOrphans = !keepForTab)
        return FutaberMhtOpened(thread.withoutPictures(page), page)
    }

    /** A picture's address as it is shown: the copy made from the file, else the address itself if it is a Futaba host's, else none. */
    private fun shownAddress(url: String?, local: Map<String, String>): String? = when {
        url == null -> null
        local.containsKey(url) -> local[url]
        isOfficialMediaUrl(url) -> url
        else -> null
    }

    /**
     * Remembers that [current] was opened last, and removes the pictures of the files that were opened before it beyond [keep]
     * files (and, with [dropOrphans], of files that no longer exist). A failure here never fails the open.
     */
    private suspend fun trimRecent(root: String, current: String, keep: Int, dropOrphans: Boolean) {
        try {
            val existing = if (fileSystem.exists(root)) {
                fileSystem.listFiles(root).map { it.substringAfterLast('/') }.filter { !it.startsWith(".") }
            } else emptyList()
            val recent = fileSystem.readString("$root/$RECENT_FILE").getOrNull().orEmpty()
                .lines().map { it.trim() }.filter { it.isNotEmpty() }
            val keepSet = LinkedHashSet<String>()
            for (stem in listOf(current) + recent) {
                if (keepSet.size >= keep) break
                if (stem !in existing) continue
                if (dropOrphans && stem != current && !fileSystem.exists("$FUTABER_MHT_DIRECTORY/$stem.mht")) continue
                keepSet += stem
            }
            existing.filter { it !in keepSet }.forEach { stem -> fileSystem.deleteRecursively("$root/$stem") }
            fileSystem.writeString("$root/$RECENT_FILE", keepSet.joinToString("\n")).getOrThrow()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The pictures stay a little longer; the next open tries again.
        }
    }

    /** The viewer's pictures of the file (not the tab's), in the folder now used and in the one used before. */
    private suspend fun clearCaches(fileName: String) {
        val stem = fileName.removeSuffix(".mht")
        listOf("$FUTABER_MHT_CACHE_DIRECTORY/$stem", "$LEGACY_CACHE_DIRECTORY/$stem").forEach { folder ->
            if (fileSystem.exists(folder)) fileSystem.deleteRecursively(folder)
        }
    }

    // -----------------------------------------------------------------------------------------------
    // Names and helpers
    // -----------------------------------------------------------------------------------------------

    /**
     * The name a thread's file is saved under. A file saved before the host was part of the name keeps its name when it is
     * of the same board (so saving again replaces it instead of adding a second file); another board's file of that
     * name is not touched.
     */
    private suspend fun chooseFileName(boardUrl: String, threadId: String): String {
        val current = fileNameFor(boardUrl, threadId)
        val legacy = legacyFileNameFor(boardUrl, threadId)
        if (legacy == current) return current
        val legacyPath = "$FUTABER_MHT_DIRECTORY/$legacy"
        if (fileSystem.exists("$FUTABER_MHT_DIRECTORY/$current") || !fileSystem.exists(legacyPath)) return current
        val legacyEntry = readEntry(legacyPath) ?: return current
        return if (legacyEntry.boardUrl?.let { normalizeBoardUrl(it) } == normalizeBoardUrl(boardUrl)) legacy else current
    }

    companion object {
        /** `may_2chan_net-27-324989.mht`: the board's whole host and path, then the thread. Two boards never share a name. */
        fun fileNameFor(board: BoardSummary, threadId: String): String = fileNameFor(board.url, threadId)

        fun fileNameFor(boardUrl: String, threadId: String): String {
            val address = boardUrl.substringBefore('?').substringAfter("://", boardUrl).trimEnd('/')
            val host = address.substringBefore('/').substringAfterLast('@').lowercase()
            val path = address.substringAfter('/', "").substringBefore('/')
            val board = sanitize(listOf(host, path).filter { it.isNotBlank() }.joinToString("-")).take(60)
            return board + "-" + sanitize(threadId).take(24) + ".mht"
        }

        /** The name before the whole host was used (`may-27-324989.mht`): files saved so are still listed, opened and deleted. */
        fun legacyFileNameFor(boardUrl: String, threadId: String): String {
            val address = boardUrl.substringAfter("://", boardUrl).trimEnd('/')
            val host = address.substringBefore('/').substringBefore('.')
            val path = address.substringAfter('/', "").substringBefore('/')
            return sanitize(listOf(host, path, threadId).filter { it.isNotBlank() }.joinToString("-")) + ".mht"
        }

        private fun normalizeBoardUrl(url: String): String = url.trim().substringAfter("://").trimEnd('/').lowercase()

        internal fun sanitize(text: String): String =
            text.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }.joinToString("").take(80).ifEmpty { "thread" }

        /** True for an address of the Futaba hosts (a picture there may be shown); false for any other host or a local path. */
        internal fun isOfficialMediaUrl(url: String): Boolean = canonicalizeBoardUrl(url) != null

        /**
         * The most a picture may have in raw bytes so that its base64 text (with line breaks) fits in [room] bytes of the
         * file: 57 bytes become 78 bytes of text per line. Zero when nothing fits.
         */
        internal fun maxRawBytesFor(room: Long): Long = if (room <= 0L) 0L else room / 78L * 57L

        /** The addresses to fetch: every thumbnail, and every full-size picture when asked for; no address twice. */
        internal fun pictureAddresses(posts: List<Post>, fullImages: Boolean): List<String> {
            val seen = LinkedHashSet<String>()
            posts.forEach { post -> post.thumbnailUrl?.takeIf { it.isNotBlank() }?.let(seen::add) }
            if (fullImages) posts.forEach { post -> post.imageUrl?.takeIf { it.isNotBlank() }?.let(seen::add) }
            return seen.toList()
        }

        internal fun mimeOf(address: String, bytes: ByteArray): String {
            if (bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) return "image/jpeg"
            if (bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte()) return "image/png"
            if (bytes.size >= 4 && bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte()) return "image/gif"
            if (bytes.size >= 12 && bytes[0] == 'R'.code.toByte() && bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte()) return "image/webp"
            return when (address.substringBefore('?').substringAfterLast('.', "").lowercase()) {
                "png" -> "image/png"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                "mp4" -> "video/mp4"
                "webm" -> "video/webm"
                else -> "image/jpeg"
            }
        }

        internal fun extensionOf(contentType: String): String = when (contentType.substringBefore(';').trim().lowercase()) {
            "image/png" -> "png"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            "image/bmp" -> "bmp"
            else -> "jpg"
        }

        /** `Tue, 06 Oct 2026 11:04:00 +0000`. */
        internal fun rfc1123(epochMillis: Long): String {
            val time = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.UTC)
            val day = when (time.dayOfWeek) {
                DayOfWeek.MONDAY -> "Mon"; DayOfWeek.TUESDAY -> "Tue"; DayOfWeek.WEDNESDAY -> "Wed"; DayOfWeek.THURSDAY -> "Thu"
                DayOfWeek.FRIDAY -> "Fri"; DayOfWeek.SATURDAY -> "Sat"; else -> "Sun"
            }
            val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
            fun two(n: Int) = n.toString().padStart(2, '0')
            return "$day, ${two(time.dayOfMonth)} ${months[time.monthNumber - 1]} ${time.year} " +
                "${two(time.hour)}:${two(time.minute)}:${two(time.second)} +0000"
        }
    }
}

/** A buffer that grows; the common library has no byte-array output stream. */
internal class GrowableBytes(initialCapacity: Int = 8 * 1024) {
    private var data = ByteArray(initialCapacity.coerceAtLeast(16))
    var size = 0
        private set

    fun append(source: ByteArray, count: Int) {
        if (size + count > data.size) data = data.copyOf(maxOf(data.size * 2, size + count))
        source.copyInto(data, size, 0, count)
        size += count
    }

    fun toByteArray(): ByteArray = data.copyOf(size)

    /** Where the first empty line is (the end of the header block), or -1. */
    fun indexOfBlankLine(): Int {
        var i = 0
        while (i < size - 1) {
            if (data[i] == '\n'.code.toByte()) {
                if (data[i + 1] == '\n'.code.toByte()) return i + 1
                if (i + 2 < size && data[i + 1] == '\r'.code.toByte() && data[i + 2] == '\n'.code.toByte()) return i + 2
            }
            i++
        }
        return -1
    }
}

/**
 * The result of an MHT operation: failures become a message for the person, including running out of memory (an Error, which
 * `catch (Exception)` lets through and would end the app). Cancellation and every other Error pass on unchanged. This is
 * kept to the top of each operation; it is not a general net.
 */
private inline fun <T> runCatchingMht(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    } catch (error: Throwable) {
        if (error.isMemoryExhaustion()) Result.failure(FutaberMhtFormatException(MEMORY_MESSAGE)) else throw error
    }
