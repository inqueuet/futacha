package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.ui.board.messageHtmlToLines
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** What a posting screen is writing: a reply to the open thread, or a new thread on a board. */
internal sealed interface FutaberPostTarget {
    val boardId: String

    /** Key of the draft kept for this target. */
    val draftKey: String

    data class Reply(override val boardId: String, val threadId: String, val threadTitle: String) : FutaberPostTarget {
        override val draftKey: String get() = "r:$boardId:$threadId"
    }

    data class CreateThread(override val boardId: String) : FutaberPostTarget {
        override val draftKey: String get() = "c:$boardId"
    }
}

internal fun futaberPostTargetToStrings(target: FutaberPostTarget?): List<String> = when (target) {
    null -> emptyList()
    is FutaberPostTarget.Reply -> listOf("r", target.boardId, target.threadId, target.threadTitle)
    is FutaberPostTarget.CreateThread -> listOf("c", target.boardId)
}

internal fun futaberPostTargetFromStrings(parts: List<String>): FutaberPostTarget? = when {
    parts.size == 4 && parts[0] == "r" -> FutaberPostTarget.Reply(parts[1], parts[2], parts[3])
    parts.size == 2 && parts[0] == "c" -> FutaberPostTarget.CreateThread(parts[1])
    else -> null
}

/** The text of an unsent post. Attachments are not kept: bytes do not belong in settings storage. */
@Serializable
internal data class FutaberDraft(
    val key: String,
    val subject: String = "",
    val comment: String = "",
    val updatedAt: Long = 0L
) {
    val isEmpty: Boolean get() = subject.isBlank() && comment.isBlank()
}

internal const val FUTABER_DRAFT_MAX_COUNT = 6
internal const val FUTABER_DRAFT_MAX_COMMENT_CHARS = 2_500
internal const val FUTABER_DRAFT_MAX_SUBJECT_CHARS = 100
private const val FUTABER_DRAFT_STORE_MAX_CHARS = 18_000
private val draftJson = Json { ignoreUnknownKeys = true }

/**
 * The first [limit] characters, never cutting an emoji or another character outside the BMP in half (a lone surrogate
 * is not valid text and breaks the encoding on the way to the server): a cut that would fall between the two halves
 * of a pair stops one character earlier.
 */
internal fun String.futaberTakeChars(limit: Int): String {
    if (limit <= 0) return ""
    if (length <= limit) return this
    return if (this[limit - 1].isHighSurrogate() && this[limit].isLowSurrogate()) substring(0, limit - 1) else substring(0, limit)
}

internal fun encodeFutaberDrafts(drafts: List<FutaberDraft>): String =
    draftJson.encodeToString(ListSerializer(FutaberDraft.serializer()), drafts)

/** A damaged or unknown stored value reads as no drafts rather than failing. */
internal fun decodeFutaberDrafts(stored: String?): List<FutaberDraft> {
    if (stored.isNullOrBlank()) return emptyList()
    return runCatching { draftJson.decodeFromString(ListSerializer(FutaberDraft.serializer()), stored) }
        .getOrDefault(emptyList())
        .filter { it.key.isNotBlank() }
        .distinctBy { it.key }
        .take(FUTABER_DRAFT_MAX_COUNT)
}

/**
 * [drafts] with [draft] saved as the newest: an empty draft removes its entry, an older
 * entry for the same target is replaced, and the oldest entries go when the count or the
 * stored size would exceed what one setting can hold.
 */
internal fun futaberUpsertDraft(drafts: List<FutaberDraft>, draft: FutaberDraft): List<FutaberDraft> {
    val others = drafts.filterNot { it.key == draft.key }
    if (draft.isEmpty) return others
    val bounded = draft.copy(
        subject = draft.subject.futaberTakeChars(FUTABER_DRAFT_MAX_SUBJECT_CHARS),
        comment = draft.comment.futaberTakeChars(FUTABER_DRAFT_MAX_COMMENT_CHARS)
    )
    var result = (listOf(bounded) + others).take(FUTABER_DRAFT_MAX_COUNT)
    while (result.size > 1 && encodeFutaberDrafts(result).length > FUTABER_DRAFT_STORE_MAX_CHARS) {
        result = result.dropLast(1)
    }
    return result
}

internal fun futaberDraftFor(drafts: List<FutaberDraft>, key: String): FutaberDraft? =
    drafts.firstOrNull { it.key == key }

/** `>`-prefixed copy of the post's own lines (earlier quotes and blank lines are left out). */
internal fun futaberQuoteBody(post: Post): String =
    messageHtmlToLines(post.messageHtml)
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith(">") && !it.startsWith("＞") }
        .joinToString("\n") { ">$it" }

/** `>No.123`: quotes the post by its number only. */
internal fun futaberQuoteByNumber(post: Post): String = ">No.${post.id}"

/** Only the post's last line, quoted. */
internal fun futaberQuoteLastLine(post: Post): String =
    messageHtmlToLines(post.messageHtml)
        .map { it.trim() }
        .lastOrNull { it.isNotEmpty() && !it.startsWith(">") && !it.startsWith("＞") }
        ?.let { ">$it" }
        .orEmpty()

/** [quote] added on its own line(s) after what is already written, ready to type under. */
internal fun futaberAppendQuote(comment: String, quote: String): String {
    if (quote.isBlank()) return comment
    val separator = if (comment.isEmpty() || comment.endsWith("\n")) "" else "\n"
    return comment + separator + quote + "\n"
}

/** What the write screen shows under the text box: lines and bytes as they will be sent. */
internal data class FutaberPostCounts(val lines: Int, val bytes: Int)

internal fun futaberPostCounts(lines: Int, bytes: Int) = FutaberPostCounts(lines, bytes)

/** Name, mail and the send confirmation, kept for every post (not per draft). */
internal data class FutaberPostSettings(
    val name: String = "",
    val email: String = "",
    val confirmBeforeSend: Boolean = true
) {
    companion object {
        fun from(preferences: Map<String, String>) = FutaberPostSettings(
            name = preferences[FutaberPreferenceKeys.POST_NAME].orEmpty(),
            email = preferences[FutaberPreferenceKeys.POST_EMAIL].orEmpty(),
            confirmBeforeSend = preferences[FutaberPreferenceKeys.POST_CONFIRM] != "OFF"
        )
    }
}
