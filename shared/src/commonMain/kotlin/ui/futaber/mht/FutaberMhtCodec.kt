package com.valoser.futacha.shared.ui.futaber.mht

import com.valoser.futacha.shared.util.FileWriteSink
import com.valoser.futacha.shared.util.MAX_FILE_SYSTEM_FILE_SIZE
import com.valoser.futacha.shared.util.TextEncoding
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * MHT (MHTML, RFC 2557): a whole page in one file. A MIME message of the type `multipart/related`:
 * a header block, then one part for the HTML and one part for each picture, each told apart by the
 * address it had on the web (`Content-Location`). Any browser or MHT viewer can open it.
 *
 * This is the format ふたばー風モード writes for "MHTで保存" and reads back, whether the file was
 * written here or by another tool. It is used by this mode only.
 */

/**
 * A file bigger than this is refused when read: it is held in memory while it is taken apart, and the file
 * system itself refuses to read a file bigger than this into memory. The writer keeps its files below it,
 * so that a file that was saved can always be opened again.
 */
internal const val FUTABER_MHT_MAX_READ_BYTES = MAX_FILE_SYSTEM_FILE_SIZE

internal const val FUTABER_MHT_TOO_LARGE_MESSAGE = "ファイルが大きすぎます（100MBまで）"

/** The header block of the message (the list reads only this much of a file), and of one part. */
private const val MAX_MESSAGE_HEADER_BYTES = 192 * 1024
private const val MAX_PART_HEADER_BYTES = 64 * 1024

/** What a file may hold: a thread has a page and its pictures, so these are far above any real file. */
private const val MAX_PARTS = 5_000
private const val MAX_DELIMITER_LINES = 10_000
private const val MAX_NESTING_DEPTH = 6

internal class FutaberMhtPart private constructor(
    val contentType: String,
    val location: String?,
    val contentId: String?,
    private val eagerBody: ByteArray?,
    private val source: ByteArray?,
    private val sourceStart: Int,
    private val sourceEnd: Int,
    private val encoding: String
) {
    constructor(contentType: String, location: String?, contentId: String?, body: ByteArray) :
        this(contentType, location, contentId, body, null, 0, 0, "")

    val isHtml: Boolean get() = contentType.startsWith("text/html", ignoreCase = true)
    val isImage: Boolean get() = contentType.startsWith("image/", ignoreCase = true)

    /**
     * The bytes of the part. A part taken out of a file is decoded each time it is asked for, so that a file with
     * many pictures never holds all of them decoded at once; callers ask once and write the bytes out.
     * Throws [FutaberMhtFormatException] when the part cannot be decoded.
     */
    val body: ByteArray get() = eagerBody ?: FutaberMhtReader.decodeBody(source!!, sourceStart, sourceEnd, encoding)

    /** [body], or null when this part is broken (one bad picture does not spoil the file). */
    fun bodyOrNull(): ByteArray? = try {
        body
    } catch (_: FutaberMhtFormatException) {
        null
    }

    internal companion object {
        /** A part whose body is decoded when it is asked for. */
        fun fromRange(
            contentType: String,
            location: String?,
            contentId: String?,
            source: ByteArray,
            start: Int,
            end: Int,
            encoding: String
        ): FutaberMhtPart = FutaberMhtPart(contentType, location, contentId, null, source, start, end, encoding)
    }
}

internal class FutaberMhtDocument(
    /** Header names in lower case. */
    val headers: Map<String, String>,
    val parts: List<FutaberMhtPart>,
    /** False when the closing delimiter is missing: the file was cut short. */
    val isComplete: Boolean = true
) {
    val subject: String? get() = headers["subject"]?.let(::decodeMimeWords)?.trim()?.takeIf { it.isNotEmpty() }
    val htmlPart: FutaberMhtPart? get() = parts.firstOrNull { it.isHtml }

    /** A header of this program (`X-Futaber-…`), decoded. */
    fun custom(name: String): String? = headers["x-futaber-${name.lowercase()}"]?.let(::decodeMimeWords)?.trim()?.takeIf { it.isNotEmpty() }
}

internal class FutaberMhtFormatException(message: String) : IllegalArgumentException(message)

// ---------------------------------------------------------------------------------------------------
// Writing
// ---------------------------------------------------------------------------------------------------

private const val BASE64_LINE_CHARS = 76
/** 57 bytes make exactly one line of 76 characters. */
private const val BASE64_LINE_BYTES = 57
private const val BASE64_BATCH_LINES = 64

/** The text of [body] from [from] to [to] as base64 lines of 76 characters (the last one may be shorter), each ended by CRLF. */
@OptIn(ExperimentalEncodingApi::class)
internal fun encodeBase64Lines(body: ByteArray, from: Int, to: Int): ByteArray {
    val encoded = Base64.Default.encodeToByteArray(body, from, to)
    val lines = (encoded.size + BASE64_LINE_CHARS - 1) / BASE64_LINE_CHARS
    val out = ByteArray(encoded.size + lines * 2)
    var source = 0
    var target = 0
    while (source < encoded.size) {
        val count = minOf(BASE64_LINE_CHARS, encoded.size - source)
        encoded.copyInto(out, target, source, source + count)
        target += count
        out[target++] = '\r'.code.toByte()
        out[target++] = '\n'.code.toByte()
        source += count
    }
    return out
}

/** Pieces of the file, as bytes, in the order they are written. */
internal object FutaberMhtWriter {
    fun newBoundary(seed: Long): String = "----=_Futaber_" + seed.toULong().toString(16)

    /**
     * The header block and the opening of the first part. [customHeaders] carry this program's own facts
     * (`X-Futaber-Board`, …); values may hold any text, which is written as encoded words.
     */
    fun header(
        boundary: String,
        subject: String,
        dateRfc1123: String,
        snapshotLocation: String,
        customHeaders: List<Pair<String, String>>
    ): ByteArray = buildString {
        append("MIME-Version: 1.0\r\n")
        append("From: <Saved by Futaber mode>\r\n")
        append("Subject: ").append(encodeMimeWords(subject)).append("\r\n")
        append("Date: ").append(dateRfc1123).append("\r\n")
        append("Snapshot-Content-Location: ").append(snapshotLocation.replace("\r", "").replace("\n", "")).append("\r\n")
        customHeaders.forEach { (name, value) ->
            append("X-Futaber-").append(name).append(": ").append(encodeMimeWords(value)).append("\r\n")
        }
        append("Content-Type: multipart/related; type=\"text/html\"; boundary=\"").append(boundary).append("\"\r\n")
        append("\r\n")
    }.encodeToByteArray()

    /** The opening of a part: the delimiter, its header lines and the blank line before the body. */
    fun partHead(boundary: String, contentType: String, location: String?, charset: String? = null): ByteArray = buildString {
        append("--").append(boundary).append("\r\n")
        append("Content-Type: ").append(contentType)
        if (charset != null) append("; charset=\"").append(charset).append("\"")
        append("\r\n")
        append("Content-Transfer-Encoding: base64\r\n")
        if (location != null) append("Content-Location: ").append(location.replace("\r", "").replace("\n", "")).append("\r\n")
        append("\r\n")
    }.encodeToByteArray()

    /** How many bytes the body of [bodySize] bytes takes once written by [writeBody]: its base64 lines and the closing blank line. */
    fun bodySize(bodySize: Int): Long {
        val chars = (bodySize.toLong() + 2L) / 3L * 4L
        val lines = (chars + BASE64_LINE_CHARS - 1L) / BASE64_LINE_CHARS
        return chars + lines * 2L + 2L
    }

    /**
     * Writes the body of a part as base64 in lines of 76 characters, a few lines at a time, so the encoded
     * text of a big picture never exists as a whole. Ends with the blank line that closes the part.
     */
    suspend fun writeBody(sink: FileWriteSink, body: ByteArray) {
        val batchBytes = BASE64_LINE_BYTES * BASE64_BATCH_LINES
        var index = 0
        while (index < body.size) {
            val end = minOf(index + batchBytes, body.size)
            val lines = encodeBase64Lines(body, index, end)
            sink.write(lines, 0, lines.size)
            index = end
        }
        sink.write(CRLF, 0, CRLF.size)
    }

    /** A part: its header lines, a blank line, the body as base64 in lines of 76 characters. */
    fun part(boundary: String, contentType: String, location: String?, body: ByteArray, charset: String? = null): ByteArray {
        val head = partHead(boundary, contentType, location, charset)
        val chunks = ArrayList<ByteArray>()
        val batchBytes = BASE64_LINE_BYTES * BASE64_BATCH_LINES
        var index = 0
        while (index < body.size) {
            val end = minOf(index + batchBytes, body.size)
            chunks += encodeBase64Lines(body, index, end)
            index = end
        }
        val result = ByteArray(head.size + chunks.sumOf { it.size } + CRLF.size)
        head.copyInto(result, 0)
        var at = head.size
        chunks.forEach { chunk ->
            chunk.copyInto(result, at)
            at += chunk.size
        }
        CRLF.copyInto(result, at)
        return result
    }

    fun end(boundary: String): ByteArray = "--$boundary--\r\n".encodeToByteArray()

    private val CRLF = "\r\n".encodeToByteArray()
}

// ---------------------------------------------------------------------------------------------------
// Reading
// ---------------------------------------------------------------------------------------------------

private class MhtParseState {
    val parts = ArrayList<FutaberMhtPart>()
    var delimiterLines = 0
    var htmlSeen = false
    /** A part was left open: no delimiter followed it. */
    var cutShort = false
}

private const val BASE64_PAD = '='

private val BASE64_VALUES = IntArray(128) { -1 }.also { table ->
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".forEachIndexed { index, c -> table[c.code] = index }
}

internal object FutaberMhtReader {
    /** Takes the file apart. A single (not multipart) message is read as a document with one part. */
    fun parse(bytes: ByteArray): FutaberMhtDocument {
        if (bytes.size.toLong() > FUTABER_MHT_MAX_READ_BYTES) throw FutaberMhtFormatException(FUTABER_MHT_TOO_LARGE_MESSAGE)
        val (headers, bodyStart) = readHeaders(bytes, 0, bytes.size, MAX_MESSAGE_HEADER_BYTES)
        if (headers.isEmpty() || bodyStart < 0) throw FutaberMhtFormatException("MHTファイルとして読めません")
        val contentType = headers["content-type"].orEmpty()
        val state = MhtParseState()
        collectParts(bytes, headers, bodyStart, bytes.size, state, depth = 0)
        val parts = state.parts
        if (parts.isEmpty()) throw FutaberMhtFormatException("MHTファイルの中身が見つかりません")
        if (!contentType.startsWith("multipart/", ignoreCase = true) && parts.none { it.isHtml }) {
            throw FutaberMhtFormatException("ページ（HTML）が入っていません")
        }
        // The last part of a file that was cut short may be only partly there: leave it out (never the page itself).
        if (state.cutShort && parts.size > 1 && !parts.last().isHtml) parts.removeAt(parts.lastIndex)
        return FutaberMhtDocument(headers, parts, isComplete = !state.cutShort)
    }

    /** Only the header block of a file, from its first bytes (the list does not read whole files); null if it is not one. */
    fun readHeaderBlock(head: ByteArray): Map<String, String>? {
        val (headers, bodyStart) = try {
            readHeaders(head, 0, head.size, MAX_MESSAGE_HEADER_BYTES)
        } catch (_: FutaberMhtFormatException) {
            return null
        }
        return if (headers.isEmpty() || bodyStart < 0) null else headers
    }

    private fun collectParts(
        bytes: ByteArray,
        headers: Map<String, String>,
        bodyStart: Int,
        bodyEnd: Int,
        state: MhtParseState,
        depth: Int
    ) {
        val contentType = headers["content-type"].orEmpty()
        if (contentType.startsWith("multipart/", ignoreCase = true)) {
            if (depth >= MAX_NESTING_DEPTH) throw FutaberMhtFormatException("MHTファイルの入れ子が深すぎます")
            val boundary = headerParameter(contentType, "boundary")?.takeIf { it.isNotEmpty() }
                ?: throw FutaberMhtFormatException("区切りの指定がありません")
            val delimiter = ("--$boundary").encodeToByteArray()
            var cursor = indexOfDelimiterLine(bytes, delimiter, bodyStart, bodyEnd)
            while (cursor >= 0) {
                if (++state.delimiterLines > MAX_DELIMITER_LINES) throw FutaberMhtFormatException("MHTファイルの部品が多すぎます")
                val afterDelimiter = cursor + delimiter.size
                // "--boundary--" closes the message.
                if (delimiterKind(bytes, afterDelimiter, bodyEnd) == DELIMITER_CLOSING) return
                val partStart = skipLine(bytes, lineEnd(bytes, afterDelimiter, bodyEnd), bodyEnd)
                val next = indexOfDelimiterLine(bytes, delimiter, partStart, bodyEnd)
                val partEnd = if (next < 0) bodyEnd else trimLineBreakBefore(bytes, partStart, next)
                if (next < 0) state.cutShort = true
                val (partHeaders, partBody) = readHeaders(bytes, partStart, partEnd, MAX_PART_HEADER_BYTES)
                if (partBody >= 0) collectParts(bytes, partHeaders, partBody, partEnd, state, depth + 1)
                cursor = next
            }
            return
        }
        if (state.parts.size >= MAX_PARTS) throw FutaberMhtFormatException("MHTファイルの部品が多すぎます")
        val start = bodyStart.coerceAtMost(bodyEnd)
        val encoding = headers["content-transfer-encoding"].orEmpty()
        val type = contentType.substringBefore(';').trim().ifEmpty { "application/octet-stream" }
            .let { value -> if (value.startsWith("text/", ignoreCase = true)) typeWithCharset(value, contentType) else value }
        val location = headers["content-location"]?.trim()?.takeIf { it.isNotEmpty() }
        val contentId = headers["content-id"]?.trim()?.removePrefix("<")?.removeSuffix(">")?.takeIf { it.isNotEmpty() }
        val isPage = type.startsWith("text/html", ignoreCase = true) && !state.htmlSeen
        state.parts += if (isPage) {
            // The page is what a file is for: it is decoded now, and a file whose page cannot be read is refused.
            state.htmlSeen = true
            val body = try {
                decodeBody(bytes, start, bodyEnd, encoding)
            } catch (_: FutaberMhtFormatException) {
                throw FutaberMhtFormatException("ページのデータを読めません")
            }
            FutaberMhtPart(type, location, contentId, body)
        } else {
            // Pictures and the rest are decoded when they are used, one at a time; a broken one is only left out then.
            FutaberMhtPart.fromRange(type, location, contentId, bytes, start, bodyEnd, encoding)
        }
    }

    private fun typeWithCharset(type: String, fullContentType: String): String {
        val charset = headerParameter(fullContentType, "charset") ?: return type
        return "$type; charset=$charset"
    }

    /**
     * Reads header lines from [start]; returns them (names in lower case, folded lines joined) and where the body starts.
     * A header block longer than [limit] bytes is refused, so a file made of endless folded lines cannot hold the reader.
     */
    private fun readHeaders(bytes: ByteArray, start: Int, end: Int, limit: Int): Pair<Map<String, String>, Int> {
        val values = LinkedHashMap<String, StringBuilder>()
        var position = start
        var last: StringBuilder? = null
        var consumed = 0
        while (position < end) {
            val lineEnd = lineEnd(bytes, position, end)
            consumed += lineEnd - position + 2
            if (consumed > limit) throw FutaberMhtFormatException("MHTファイルのヘッダーが大きすぎます")
            val line = bytes.decodeToString(position, lineEnd)
            val next = skipLine(bytes, lineEnd, end)
            if (line.isEmpty()) return finishHeaders(values) to next
            if ((line[0] == ' ' || line[0] == '\t') && last != null) {
                last.append(' ').append(line.trim())
            } else {
                val colon = line.indexOf(':')
                if (colon <= 0) return finishHeaders(values) to (if (values.isEmpty()) -1 else position)
                val builder = StringBuilder(line.substring(colon + 1).trim())
                values[line.substring(0, colon).trim().lowercase()] = builder
                last = builder
            }
            position = next
        }
        return finishHeaders(values) to -1
    }

    private fun finishHeaders(values: Map<String, StringBuilder>): Map<String, String> {
        val result = LinkedHashMap<String, String>(values.size * 2)
        values.forEach { (name, value) -> result[name] = value.toString() }
        return result
    }

    private fun lineEnd(bytes: ByteArray, from: Int, end: Int): Int {
        var i = from
        while (i < end && bytes[i] != '\n'.code.toByte() && bytes[i] != '\r'.code.toByte()) i++
        return i
    }

    private fun skipLine(bytes: ByteArray, lineEnd: Int, end: Int): Int {
        var i = lineEnd
        if (i < end && bytes[i] == '\r'.code.toByte()) i++
        if (i < end && bytes[i] == '\n'.code.toByte()) i++
        return i
    }

    /** The end of the part's body: the line break that belongs to the delimiter is not part of it. */
    private fun trimLineBreakBefore(bytes: ByteArray, floor: Int, delimiterAt: Int): Int {
        var i = delimiterAt
        if (i > floor && bytes[i - 1] == '\n'.code.toByte()) i--
        if (i > floor && bytes[i - 1] == '\r'.code.toByte()) i--
        return i
    }

    private const val DELIMITER_NONE = 0
    private const val DELIMITER_PART = 1
    private const val DELIMITER_CLOSING = 2

    /**
     * What stands after `--boundary`: only the end of the line (a part follows), or `--` and the end of the line (the
     * message ends). Anything else means the text is the start of a longer boundary (an inner one that begins with
     * this one), which is not a delimiter of this level.
     */
    private fun delimiterKind(bytes: ByteArray, afterDelimiter: Int, end: Int): Int {
        var i = afterDelimiter
        var closing = false
        if (i + 1 < end && bytes[i] == '-'.code.toByte() && bytes[i + 1] == '-'.code.toByte()) {
            closing = true
            i += 2
        }
        // Spaces and tabs may pad the line.
        while (i < end && (bytes[i] == ' '.code.toByte() || bytes[i] == '\t'.code.toByte())) i++
        if (i < end && bytes[i] != '\r'.code.toByte() && bytes[i] != '\n'.code.toByte()) return DELIMITER_NONE
        return if (closing) DELIMITER_CLOSING else DELIMITER_PART
    }

    /** The next delimiter that stands at the start of a line and ends its line like one. */
    private fun indexOfDelimiterLine(bytes: ByteArray, delimiter: ByteArray, from: Int, end: Int): Int {
        var at = indexOf(bytes, delimiter, from, end)
        while (at >= 0) {
            if ((at == from || bytes[at - 1] == '\n'.code.toByte()) &&
                delimiterKind(bytes, at + delimiter.size, end) != DELIMITER_NONE
            ) return at
            at = indexOf(bytes, delimiter, at + 1, end)
        }
        return -1
    }

    private fun indexOf(bytes: ByteArray, pattern: ByteArray, from: Int, end: Int): Int {
        if (pattern.isEmpty()) return -1
        val last = end - pattern.size
        var i = from
        while (i <= last) {
            if (bytes[i] == pattern[0]) {
                var j = 1
                while (j < pattern.size && bytes[i + j] == pattern[j]) j++
                if (j == pattern.size) return i
            }
            i++
        }
        return -1
    }

    /** The decoded bytes of `bytes[start, end)` as the transfer encoding says; throws [FutaberMhtFormatException] when it is broken. */
    internal fun decodeBody(bytes: ByteArray, start: Int, end: Int, encoding: String): ByteArray =
        when (encoding.trim().lowercase()) {
            "base64" -> decodeBase64Range(bytes, start, end)
            "quoted-printable" -> decodeQuotedPrintableRange(bytes, start, end)
            else -> bytes.copyOfRange(start, end)
        }

    /**
     * Base64 over a range of bytes, without a copy of the text: line breaks and spaces are skipped, the padding
     * may be missing (some writers leave it off), anything else is an error.
     */
    internal fun decodeBase64Range(bytes: ByteArray, start: Int, end: Int): ByteArray {
        var count = 0
        var i = start
        while (i < end) {
            val b = bytes[i].toInt()
            if (b in 0..127 && BASE64_VALUES[b] >= 0) count++
            else if (b == BASE64_PAD.code) break
            else if (b != '\r'.code && b != '\n'.code && b != ' '.code && b != '\t'.code) {
                throw FutaberMhtFormatException("画像のデータを読めません")
            }
            i++
        }
        if (count % 4 == 1) throw FutaberMhtFormatException("画像のデータを読めません")
        val out = ByteArray(count / 4 * 3 + when (count % 4) { 2 -> 1; 3 -> 2; else -> 0 })
        var accumulated = 0
        var bits = 0
        var written = 0
        var seen = 0
        i = start
        while (i < end && seen < count) {
            val b = bytes[i].toInt()
            val value = if (b in 0..127) BASE64_VALUES[b] else -1
            if (value >= 0) {
                accumulated = (accumulated shl 6) or value
                bits += 6
                seen++
                if (bits >= 8) {
                    bits -= 8
                    out[written++] = (accumulated shr bits).toByte()
                    accumulated = accumulated and ((1 shl bits) - 1)
                }
            }
            i++
        }
        return out
    }

    internal fun decodeQuotedPrintable(raw: ByteArray): ByteArray = decodeQuotedPrintableRange(raw, 0, raw.size)

    /** Quoted-printable over a range of bytes, into one array that is never larger than the range. */
    internal fun decodeQuotedPrintableRange(raw: ByteArray, start: Int, end: Int): ByteArray {
        val out = ByteArray((end - start).coerceAtLeast(0))
        var written = 0
        var i = start
        while (i < end) {
            val b = raw[i]
            if (b == '='.code.toByte()) {
                // "=" at the end of a line is a soft break; "=XX" is one byte.
                if (i + 1 < end && (raw[i + 1] == '\r'.code.toByte() || raw[i + 1] == '\n'.code.toByte())) {
                    i += 2
                    if (i < end && raw[i - 1] == '\r'.code.toByte() && raw[i] == '\n'.code.toByte()) i++
                    continue
                }
                if (i + 2 < end) {
                    val hi = hexValue(raw[i + 1])
                    val lo = hexValue(raw[i + 2])
                    if (hi >= 0 && lo >= 0) {
                        out[written++] = ((hi shl 4) or lo).toByte()
                        i += 3
                        continue
                    }
                }
            }
            out[written++] = b
            i++
        }
        return if (written == out.size) out else out.copyOf(written)
    }

    private fun hexValue(b: Byte): Int = when (val c = b.toInt().toChar()) {
        in '0'..'9' -> c - '0'
        in 'A'..'F' -> c - 'A' + 10
        in 'a'..'f' -> c - 'a' + 10
        else -> -1
    }
}

/** The value of `name=` in a header such as `multipart/related; boundary="x"`, without the quotes. */
internal fun headerParameter(headerValue: String, name: String): String? {
    val lower = headerValue.lowercase()
    var from = 0
    while (true) {
        val at = lower.indexOf("$name=", from)
        if (at < 0) return null
        // The name must start a parameter ("; boundary="), not end another one ("xboundary=").
        val before = if (at == 0) ';' else lower[at - 1]
        if (before == ';' || before == ' ' || before == '\t') {
            val rest = headerValue.substring(at + name.length + 1).trimStart()
            return if (rest.startsWith("\"")) rest.drop(1).substringBefore('"')
            else rest.takeWhile { it != ';' && !it.isWhitespace() }
        }
        from = at + 1
    }
}

/** The text of an HTML part, in the charset it names (UTF-8 when it names none). */
internal fun FutaberMhtPart.text(): String = TextEncoding.decodeToString(body, contentType)

/**
 * `Tue, 06 Oct 2026 11:04:00 +0000` (also without the weekday or the seconds, and with `GMT`) as epoch milliseconds;
 * null when it is not a date. The Date header of a file is when it was saved.
 */
internal fun parseRfc1123Millis(text: String?): Long? {
    val match = RFC1123_REGEX.find(text.orEmpty()) ?: return null
    val day = match.groupValues[1].toIntOrNull() ?: return null
    val month = RFC1123_MONTHS.indexOf(match.groupValues[2].lowercase()).takeIf { it >= 0 }?.plus(1) ?: return null
    val year = match.groupValues[3].toIntOrNull() ?: return null
    val hour = match.groupValues[4].toIntOrNull() ?: return null
    val minute = match.groupValues[5].toIntOrNull() ?: return null
    val second = match.groupValues[6].ifEmpty { "0" }.toIntOrNull() ?: return null
    if (day !in 1..31 || hour !in 0..23 || minute !in 0..59 || second !in 0..60 || year !in 1970..9999) return null
    val zone = match.groupValues[7]
    val offsetMinutes = if (zone.length == 5 && (zone[0] == '+' || zone[0] == '-')) {
        val minutes = (zone.substring(1, 3).toIntOrNull() ?: return null) * 60 + (zone.substring(3, 5).toIntOrNull() ?: return null)
        if (zone[0] == '-') -minutes else minutes
    } else 0
    // Days since 1970-01-01 of the civil date (proleptic Gregorian).
    val y = if (month <= 2) year - 1 else year
    val era = y / 400
    val yearOfEra = y - era * 400
    val dayOfYear = (153 * (month + (if (month > 2) -3 else 9)) + 2) / 5 + day - 1
    val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
    val days = era * 146097L + dayOfEra - 719468L
    return ((days * 24L + hour) * 60L + minute - offsetMinutes) * 60_000L + second * 1000L
}

private val RFC1123_REGEX = Regex("""(?:[A-Za-z]{3},\s*)?(\d{1,2})\s+([A-Za-z]{3})\s+(\d{4})\s+(\d{1,2}):(\d{2})(?::(\d{2}))?\s*([+-]\d{4}|GMT|UT|Z)?""")
private val RFC1123_MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

// ---------------------------------------------------------------------------------------------------
// Header words (RFC 2047)
// ---------------------------------------------------------------------------------------------------

/** Plain ASCII passes as it is; anything else becomes `=?utf-8?B?…?=` words (a few characters each, never cut inside one). */
@OptIn(ExperimentalEncodingApi::class)
internal fun encodeMimeWords(text: String): String {
    val clean = text.replace("\r", " ").replace("\n", " ")
    if (clean.all { it.code in 0x20..0x7E }) return clean
    val words = mutableListOf<String>()
    val chunk = StringBuilder()
    var count = 0
    var index = 0
    while (index < clean.length) {
        val codePointEnd = if (clean[index].isHighSurrogate() && index + 1 < clean.length) index + 2 else index + 1
        chunk.append(clean, index, codePointEnd)
        count++
        index = codePointEnd
        if (count >= 12 || index >= clean.length) {
            words += "=?utf-8?B?" + Base64.Default.encode(chunk.toString().encodeToByteArray()) + "?="
            chunk.clear()
            count = 0
        }
    }
    return words.joinToString(" ")
}

/** Decodes `=?charset?B|Q?…?=` words; text outside them stays. Adjacent words join without the space between them. */
@OptIn(ExperimentalEncodingApi::class)
internal fun decodeMimeWords(value: String): String {
    if (!value.contains("=?")) return value
    val result = StringBuilder()
    var i = 0
    var previousWasWord = false
    while (i < value.length) {
        val start = value.indexOf("=?", i)
        if (start < 0) {
            result.append(value, i, value.length)
            break
        }
        val charsetEnd = value.indexOf('?', start + 2)
        val encodingEnd = if (charsetEnd < 0) -1 else value.indexOf('?', charsetEnd + 1)
        val wordEnd = if (encodingEnd < 0) -1 else value.indexOf("?=", encodingEnd + 1)
        if (wordEnd < 0) {
            result.append(value, i, value.length)
            break
        }
        val between = value.substring(i, start)
        if (!(previousWasWord && between.isBlank())) result.append(between)
        val charset = value.substring(start + 2, charsetEnd)
        val encoding = value.substring(charsetEnd + 1, encodingEnd).uppercase()
        val payload = value.substring(encodingEnd + 1, wordEnd)
        val bytes = when (encoding) {
            "B" -> runCatching { Base64.Default.decode(payload) }.getOrNull()
            "Q" -> FutaberMhtReader.decodeQuotedPrintable(payload.replace('_', ' ').encodeToByteArray())
            else -> null
        }
        if (bytes == null) result.append(value, start, wordEnd + 2)
        else result.append(TextEncoding.decodeToString(bytes, "text/plain; charset=$charset"))
        previousWasWord = true
        i = wordEnd + 2
    }
    return result.toString()
}
