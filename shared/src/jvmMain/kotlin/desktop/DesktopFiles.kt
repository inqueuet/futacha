package com.valoser.futacha.shared.desktop

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.net.URI
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

private fun desktopDialogOwner() = java.awt.Window.getWindows().firstOrNull { it.isFocused }
    ?: java.awt.Window.getWindows().firstOrNull { it.isVisible }

internal suspend fun chooseWindowsFile(title: String, extensions: Set<String> = emptySet(), directory: Boolean = false): File? =
    withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val chooser = JFileChooser().apply {
                dialogTitle = title
                fileSelectionMode = if (directory) JFileChooser.DIRECTORIES_ONLY else JFileChooser.FILES_ONLY
                if (extensions.isNotEmpty()) fileFilter = FileNameExtensionFilter(extensions.joinToString(" / "), *extensions.toTypedArray())
            }
            continuation.invokeOnCancellation { java.awt.EventQueue.invokeLater { chooser.cancelSelection() } }
            val result = if (chooser.showOpenDialog(desktopDialogOwner()) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
            if (continuation.isActive) continuation.resume(result)
        }
    }

internal suspend fun chooseDesktopFile(title: String = "ファイルを選択", extensions: Set<String> = emptySet()): File? =
    if (DesktopPlatform.isWindows) chooseWindowsFile(title, extensions) else withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
            if (extensions.isNotEmpty()) dialog.setFilenameFilter { _, name -> name.substringAfterLast('.').lowercase() in extensions }
            continuation.invokeOnCancellation { java.awt.EventQueue.invokeLater { dialog.dispose() } }
            try {
                dialog.isVisible = true
                val result = dialog.file?.let { File(dialog.directory, it) }
                if (continuation.isActive) continuation.resume(result)
            } finally { dialog.dispose() }
        }
    }

internal suspend fun chooseDesktopDirectory(): File? = if (DesktopPlatform.isWindows)
    chooseWindowsFile("保存先のフォルダーを選択", directory = true) else MacOsIntegration.chooseDirectory()

internal suspend fun readDesktopAttachment(file: File, maxBytes: Long): com.valoser.futacha.shared.util.ImageData = withContext(Dispatchers.IO) {
    require(file.isFile && file.length() in 1..maxBytes) { "ファイルのサイズ上限を超えています" }
    val bytes = file.inputStream().use { input ->
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(65536)
        while (true) {
            currentCoroutineContext().ensureActive()
            val n = input.read(buffer); if (n < 0) break
            require(output.size().toLong() + n <= maxBytes) { "ファイルのサイズ上限を超えています" }
            output.write(buffer, 0, n)
        }
        output.toByteArray()
    }
    com.valoser.futacha.shared.util.ImageData(bytes, file.name)
}

internal fun openDesktopUrl(value: String) {
    val uri = desktopUriOrNull(value) ?: throw IllegalArgumentException("このリンクは開けません")
    require(uri.scheme?.lowercase() in setOf("http", "https", "mailto")) { "このリンクは開けません" }
    require(java.awt.Desktop.isDesktopSupported()) { "この環境ではリンクを開けません" }
    if (uri.scheme.equals("mailto", ignoreCase = true)) java.awt.Desktop.getDesktop().mail(uri)
    else java.awt.Desktop.getDesktop().browse(uri)
}

private const val DESKTOP_INLINE_HTML_DIRECTORY = "futacha-image-search"
private const val DESKTOP_INLINE_HTML_MAX_AGE_MILLIS = 60 * 60 * 1000L

/**
 * An image search that answers with a page of its own (IQDB / SauceNAO by file) has no URL to
 * open. The page is written to a temporary file with a `<base>` so its relative links and
 * thumbnails resolve, and that file is opened in the browser. A policy that blocks scripts keeps
 * the third-party page from running code under the file origin.
 */
internal fun buildDesktopInlineSearchHtml(html: String, baseUrl: String): String {
    val baseTag = """<base href="${escapeHtmlAttribute(baseUrl)}">"""
    val policyTag = """<meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src https: http: data:; style-src https: http: 'unsafe-inline'; font-src https: http: data:; form-action https: http:">"""
    val head = Regex("<head[^>]*>", RegexOption.IGNORE_CASE).find(html)
    return if (head != null) {
        html.substring(0, head.range.last + 1) + policyTag + baseTag + html.substring(head.range.last + 1)
    } else {
        "<!doctype html><html><head><meta charset=\"utf-8\">$policyTag$baseTag</head><body>$html</body></html>"
    }
}

private fun escapeHtmlAttribute(value: String): String =
    value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")

/** Writes [html] (see [buildDesktopInlineSearchHtml]) to a temp file and opens it in the browser. */
internal fun openDesktopInlineSearchHtml(html: String, baseUrl: String) {
    val base = desktopUriOrNull(baseUrl)
    require(base != null && base.scheme?.lowercase() in setOf("http", "https") && !base.host.isNullOrEmpty()) {
        "このリンクは開けません"
    }
    require(java.awt.Desktop.isDesktopSupported()) { "この環境ではリンクを開けません" }
    val directory = File(System.getProperty("java.io.tmpdir"), DESKTOP_INLINE_HTML_DIRECTORY)
    directory.mkdirs()
    // Earlier result pages are removed when the next one is written (and at exit, below).
    val cutoff = System.currentTimeMillis() - DESKTOP_INLINE_HTML_MAX_AGE_MILLIS
    directory.listFiles { file -> file.isFile && file.name.endsWith(".html") && file.lastModified() < cutoff }
        ?.forEach { it.delete() }
    val file = File(directory, "search-${System.nanoTime()}.html")
    file.writeText(buildDesktopInlineSearchHtml(html, base.toString()), Charsets.UTF_8)
    file.deleteOnExit()
    java.awt.Desktop.getDesktop().browse(file.toURI())
}

// Characters java.net.URI accepts as they are (RFC 2396 reserved + unreserved, plus '%' handled below).
private const val DESKTOP_URI_KEEP = "-._~:/?#@!$&'()*+,;="
private const val DESKTOP_URI_REJECTED = "|[]{}^\"<>\\`"

/**
 * Parses [value] as a URI, percent-encoding what java.net.URI rejects (non-ASCII letters, spaces,
 * `| [ ] { } ^ " < > \` and the like) so a link from a post body opens instead of failing with a
 * URISyntaxException. An internationalised host name becomes punycode. Null when it still cannot
 * be parsed.
 */
internal fun desktopUriOrNull(value: String): URI? {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return null
    // java.net.URI itself accepts some non-ASCII text, but the OS launchers do not reliably take
    // it, so anything that is not plain ASCII is encoded up front.
    val needsEncoding = trimmed.any { it.code >= 128 || it.isWhitespace() || DESKTOP_URI_REJECTED.indexOf(it) >= 0 }
    if (!needsEncoding) {
        try {
            return URI(trimmed)
        } catch (_: java.net.URISyntaxException) {
        }
    }
    return try {
        URI(percentEncodeForUri(trimmed))
    } catch (_: java.net.URISyntaxException) {
        null
    }
}

private fun percentEncodeForUri(value: String): String {
    var restStart = value.indexOf(':').takeIf { it > 0 }?.plus(1) ?: 0
    val out = StringBuilder(value.substring(0, restStart))
    if (value.startsWith("//", restStart)) {
        val authorityStart = restStart + 2
        val authorityEnd = value.indexOfAny(charArrayOf('/', '?', '#'), authorityStart).let { if (it < 0) value.length else it }
        out.append("//").append(encodeUriAuthority(value.substring(authorityStart, authorityEnd)))
        restStart = authorityEnd
    }
    return out.append(percentEncodeUriChars(value, restStart)).toString()
}

private fun percentEncodeUriChars(value: String, start: Int): String {
    val out = StringBuilder()
    var index = start
    while (index < value.length) {
        val codePoint = value.codePointAt(index)
        val length = Character.charCount(codePoint)
        val char = value[index]
        val keep = length == 1 && (
            char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' || DESKTOP_URI_KEEP.indexOf(char) >= 0 ||
                (char == '%' && index + 2 < value.length && isHex(value[index + 1]) && isHex(value[index + 2]))
            )
        if (keep) {
            out.append(char)
        } else {
            for (byte in String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8)) {
                out.append('%').append("%02X".format(byte.toInt() and 0xFF))
            }
        }
        index += length
    }
    return out.toString()
}

private fun isHex(char: Char) = char in '0'..'9' || char in 'a'..'f' || char in 'A'..'F'

private fun encodeUriAuthority(authority: String): String {
    if (authority.all { it.code < 128 }) return authority
    val userInfoEnd = authority.lastIndexOf('@')
    val userInfo = if (userInfoEnd >= 0) authority.substring(0, userInfoEnd + 1) else ""
    val hostAndPort = authority.substring(userInfoEnd + 1)
    val portStart = hostAndPort.lastIndexOf(':')
        .takeIf { it >= 0 && hostAndPort.substring(it + 1).all(Char::isDigit) }
    val host = if (portStart != null) hostAndPort.substring(0, portStart) else hostAndPort
    val port = if (portStart != null) hostAndPort.substring(portStart) else ""
    val asciiHost = try {
        java.net.IDN.toASCII(host, java.net.IDN.ALLOW_UNASSIGNED)
    } catch (_: IllegalArgumentException) {
        host
    }
    // Whatever is still not ASCII (an invalid host) is percent-encoded and left to fail parsing.
    return percentEncodeUriChars(userInfo + asciiHost + port, 0)
}

internal fun desktopLocalFile(value: String): File =
    if (value.startsWith("file:/", true)) File(com.valoser.futacha.shared.util.localMediaSavePath(value)) else File(value)
