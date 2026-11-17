package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.ui.board.ReadAloudSegment
import com.valoser.futacha.shared.ui.board.containsDeletionNotice
import com.valoser.futacha.shared.ui.board.messageHtmlToLines
import com.valoser.futacha.shared.ui.board.stripUrlsForReadAloud

/*
 * L-B12 (C24): futaber reads the quoted lines of a post (the lines that begin with ＞), in a voice of their own pitch
 * (「引用部分の声の高さ」, 0.5 at first). The shared read-aloud skips them, so this mode builds its own segments: a quoted line
 * carries a private mark in front ([FUTABER_QUOTE_MARK]) that the speech engine splits on, and the mark never reaches the
 * voice. The other modes' read-aloud is not touched.
 */

/** Put in front of a quoted line of a segment's text (a private-use character, never in a post). */
internal const val FUTABER_QUOTE_MARK = ''

/** One part of a segment's text: the lines in a row that are quoted, or the lines that are not. */
internal data class FutaberSpeechRun(val text: String, val quoted: Boolean)

/** A quoted line without its leading ＞ marks (and the space after them); blank when nothing is left to say. */
internal fun futaberQuoteLineToSpeak(line: String): String = line.trimStart('>', '＞', ' ', '　').trim()

/**
 * The segments of [posts] as futaber reads them: every line of a post, the quoted ones marked. A post with nothing to say,
 * a deleted post and a deletion notice are skipped as in the shared rules.
 */
internal fun futaberReadAloudSegments(posts: List<Post>, skippedPostIds: Set<String> = emptySet()): List<ReadAloudSegment> {
    val segments = ArrayList<ReadAloudSegment>()
    posts.forEachIndexed { index, post ->
        if (post.isDeleted || post.id in skippedPostIds) return@forEachIndexed
        val raw = messageHtmlToLines(com.valoser.futacha.shared.ui.board.aimogeHtmlForSpeech(post.messageHtml)).map { stripUrlsForReadAloud(it).trim() }.filter { it.isNotBlank() }
        if (raw.isEmpty() || containsDeletionNotice(raw)) return@forEachIndexed
        val lines = raw.mapNotNull { line ->
            if (line.startsWith(">") || line.startsWith("＞")) futaberQuoteLineToSpeak(line).takeIf { it.isNotEmpty() }?.let { "$FUTABER_QUOTE_MARK$it" }
            else line
        }
        if (lines.isEmpty()) return@forEachIndexed
        segments += ReadAloudSegment(index, post.id, lines.joinToString("\n"))
    }
    return segments
}

/** [text] cut into the runs to speak in order, the quoted ones apart; the marks are gone. */
internal fun futaberSpeechRuns(text: String): List<FutaberSpeechRun> {
    val runs = ArrayList<FutaberSpeechRun>()
    var current = StringBuilder()
    var currentQuoted = false
    fun flush() {
        val body = current.toString().trim()
        if (body.isNotEmpty()) runs += FutaberSpeechRun(body, currentQuoted)
        current = StringBuilder()
    }
    for (line in text.split('\n')) {
        val quoted = line.startsWith(FUTABER_QUOTE_MARK)
        if (quoted != currentQuoted) { flush(); currentQuoted = quoted }
        if (current.isNotEmpty()) current.append('\n')
        current.append(if (quoted) line.substring(1) else line)
    }
    flush()
    return runs
}
