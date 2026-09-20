package com.valoser.futacha.shared.media.video

import com.valoser.futacha.shared.media.prompt.*
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.*
import okio.Buffer
import okio.FileHandle
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.use
import kotlin.time.Clock

/** Only the editor's private staging MP4 is mutated. The original phone file is never opened writable. */
internal suspend fun preserveEditedVideoMetadata(input: String, output: String, regionCount: Int) = withContext(AppDispatchers.io) {
    val sourcePath = input.toPath(normalize = true)
    val outputPath = output.toPath(normalize = true)
    // Okio only recognizes a Windows drive root when it uses backslashes; the
    // common editor deliberately uses forward slashes when composing sibling paths.
    require(com.valoser.futacha.shared.util.isAbsoluteLocalMediaPath(sourcePath.toString()) &&
        com.valoser.futacha.shared.util.isAbsoluteLocalMediaPath(outputPath.toString()) &&
        sourcePath != outputPath && sourcePath.parent == outputPath.parent)
    val fs = FileSystem.SYSTEM
    val original = fs.openReadOnly(sourcePath).use { handle ->
        PreservedVideoMetadata.fromScan(VideoMetadataReader(VIDEO_PRESERVATION_SCAN_BUDGET).read(handle.size()) { offset, count ->
            currentCoroutineContext().ensureActive()
            handle.readExactly(offset, count)
        })
    }
    val record = original.copy(edits = original.edits + PreservedVideoMetadata.Edit(
        atUtc = Clock.System.now().toString(), regionCount = regionCount
    ))
    fs.openReadWrite(outputPath, mustExist = true).use { handle ->
        appendMp4GenerationMetadata(handle.size(), record,
            readAt = { offset, count -> handle.readExactly(offset, count) },
            writeAt = { offset, bytes -> handle.write(offset, bytes, 0, bytes.size) }, flush = { handle.flush() })
        currentCoroutineContext().ensureActive()
        val written = PreservedVideoMetadata.fromScan(VideoMetadataReader(VIDEO_PRESERVATION_SCAN_BUDGET).read(handle.size()) { offset, count ->
            currentCoroutineContext().ensureActive()
            handle.readExactly(offset, count)
        })
        check(record == written) { "書き出した動画の生成情報を照合できませんでした" }
    }
}

private fun FileHandle.readExactly(offset: Long, count: Int): ByteArray {
    val bytes = ByteArray(count)
    var position = 0
    while (position < count) {
        val read = read(offset + position, bytes, position, count - position)
        check(read > 0) { "動画のメタデータが途中で終わっています" }
        position += read
    }
    return bytes
}

/** Append a replacement moov without moving mdat or changing any sample/chunk offsets. */
internal suspend fun appendMp4GenerationMetadata(
    size: Long,
    record: PreservedVideoMetadata,
    readAt: suspend (Long, Int) -> ByteArray,
    writeAt: suspend (Long, ByteArray) -> Unit,
    flush: suspend () -> Unit
) {
    val payload = videoMetadataBox(record)
    val oldMoov = mutableListOf<Mp4Box>()
    val retire = mutableListOf<Mp4Box>()
    val terminal = mutableListOf<Mp4Box>()
    var p = 0L
    var boxes = 0
    while (p < size) {
        currentCoroutineContext().ensureActive()
        require(++boxes <= 4096) { "書き出したMP4の要素数が上限を超えています" }
        val box = readMp4Box(p, size, readAt)
        if (box.type == "moov") oldMoov += box
        if (box.type in setOf("meta", "udta") || isSourceUuid(box, readAt)) retire += box
        if (box.terminal) terminal += box
        p = box.end
    }
    require(oldMoov.size == 1) { "書き出したMP4の構造を確認できません" }
    val moov = oldMoov.single()
    require(moov.end - moov.start <= MAX_EDIT_MOOV_BYTES) { "書き出したMP4のメタデータが大きすぎます" }
    // Moov is bounded independently of video size. The encoded frames are never read here.
    val body = readAt(moov.data, (moov.end - moov.data).toInt())
    require(body.size.toLong() == moov.end - moov.data)
    var walked = 0
    suspend fun clean(start: Long, end: Long, depth: Int): ByteArray {
        require(depth <= 12)
        val out = Buffer()
        var offset = start
        val read: suspend (Long, Int) -> ByteArray = { pos, count -> body.copyOfRange(pos.toInt(), pos.toInt() + count) }
        while (offset < end) {
            currentCoroutineContext().ensureActive()
            require(++walked <= 100_000)
            val child = readMp4Box(offset, end, read)
            if (child.type !in setOf("meta", "udta") && !isSourceUuid(child, read)) {
                if (child.type in setOf("trak", "mdia")) out.write(mp4Box(child.type, clean(child.data, child.end, depth + 1)))
                else if (child.terminal) {
                    out.writeInt((child.end - child.start).toInt())
                    out.write(body, child.start.toInt() + 4, (child.end - child.start).toInt() - 4)
                } else out.write(body, child.start.toInt(), (child.end - child.start).toInt())
            }
            offset = child.end
        }
        return out.readByteArray()
    }
    val replacement = mp4Box("moov", clean(0, body.size.toLong(), 0) + mp4Box("udta", payload))
    require(replacement.size <= MAX_EDIT_MOOV_BYTES + MAX_PRESERVED_VIDEO_METADATA_BYTES * 2)
    terminal.forEach { require(it.end - it.start <= 0xffffffffL) { "MP4末尾の要素を拡張できません" } }
    // All validation precedes writes. A failed/cancelled write invalidates the staging file;
    // VideoEditSource.export deletes it and never enables confirmation/save.
    currentCoroutineContext().ensureActive()
    terminal.forEach { writeAt(it.start, mp4Uint(it.end - it.start)) }
    var written = 0
    while (written < replacement.size) {
        currentCoroutineContext().ensureActive()
        val end = minOf(replacement.size, written + 64 * 1024)
        writeAt(size + written, replacement.copyOfRange(written, end))
        written = end
    }
    flush()
    currentCoroutineContext().ensureActive()
    for (box in retire + moov) writeAt(box.start + 4, "free".encodeToByteArray())
    flush()
}

private const val MAX_EDIT_MOOV_BYTES = 32 * 1024 * 1024
private val SOURCE_UUIDS = setOf("be7acfcb97a942e89c71999491e3afac", "d8fec3d61b0e483c92975828877ec481")
private data class Mp4Box(val start: Long, val data: Long, val end: Long, val type: String, val terminal: Boolean)
private suspend fun readMp4Box(start: Long, end: Long, readAt: suspend (Long, Int) -> ByteArray): Mp4Box {
    require(start >= 0 && start <= end - 8) { "MP4の要素が途中で終わっています" }
    val header = readAt(start, 8)
    require(header.size == 8)
    val short = metadataUint(header, 0, 4)
    val headerSize = if (short == 1L) 16 else 8
    val length = when (short) {
        0L -> end - start
        1L -> {
            require(start <= end - 16)
            val extended = readAt(start + 8, 8)
            require(extended.size == 8 && extended[0] >= 0)
            metadataUint(extended, 0, 8)
        }
        else -> short
    }
    require(length >= headerSize && length <= end - start)
    return Mp4Box(start, start + headerSize, start + length, metadataLatin1(header.copyOfRange(4, 8)), short == 0L)
}
private suspend fun isSourceUuid(box: Mp4Box, readAt: suspend (Long, Int) -> ByteArray): Boolean {
    if (box.type != "uuid") return false
    require(box.end - box.data >= 16)
    return readAt(box.data, 16).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') } in SOURCE_UUIDS
}
private fun videoMetadataBox(record: PreservedVideoMetadata): ByteArray {
    val encoded = record.encode()
    // Interoperable text tags plus one authoritative source record. Source signatures/XMP
    // remain inside that unsigned record; no active C2PA or XMP claim is made for edited pixels.
    val fields = record.fields.filter { it.key.lowercase() in setOf("prompt", "workflow", "comment", "parameters", "description", "software") } +
        PreservedVideoMetadata.Field(PRESERVED_VIDEO_METADATA_KEY, encoded)
    val keys = Buffer().writeInt(0).writeInt(fields.size)
    val values = Buffer()
    fields.forEachIndexed { index, field ->
        keys.write(mp4Box("mdta", field.key.encodeToByteArray()))
        val data = mp4Box("data", mp4Uint(1) + mp4Uint(0) + field.value.encodeToByteArray())
        values.writeInt(data.size + 8).writeInt(index + 1).write(data)
    }
    val handler = mp4Box("hdlr", mp4Uint(0) + mp4Uint(0) + "mdta".encodeToByteArray() + ByteArray(13))
    return mp4Box("meta", mp4Uint(0) + handler + mp4Box("keys", keys.readByteArray()) + mp4Box("ilst", values.readByteArray()))
}
private fun mp4Uint(value: Long) = ByteArray(4) { (value ushr ((3 - it) * 8)).toByte() }
private fun mp4Box(type: String, payload: ByteArray) = mp4Uint(payload.size + 8L) + type.encodeToByteArray() + payload
