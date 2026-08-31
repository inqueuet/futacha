package com.valoser.futacha.shared.compat

data class CompatPostActionCandidate(val label: String, val value: String)

/** Exact 3x3 labels from ThreadContextDialogFragment in old.apk and 1.apk. */
fun compatReferencePostContextLabels(): List<List<String>> = listOf(
    listOf("web", "抽出", "NG登録"),
    listOf("del", "削除", "そうだね"),
    listOf("クイック", "返信", "コピー")
)

fun compatPostActionCandidates(post: CompatPostSnapshot): List<CompatPostActionCandidate> = buildList {
    add(CompatPostActionCandidate("No", "No.${post.postNo}"))
    // The legacy APK exposes ID and IP as separate choices.  A snapshot may carry
    // either value in the timestamp, posterId, or both, so inspect all visible
    // header identities instead of assuming posterId is the complete header.
    compatPosterIdentities(post).forEach { identity ->
        add(CompatPostActionCandidate(identity.kind.name, identity.display))
    }
    compatPostMediaFileNames(post).forEach { fileName ->
        add(CompatPostActionCandidate("file", fileName))
    }
    post.mail?.takeIf(String::isNotBlank)?.let { add(CompatPostActionCandidate("mail", it)) }
    compatSelectablePostBodyLines(post).forEach {
        add(CompatPostActionCandidate("本文", it))
    }
}

/** Lines shown by the old APK's quote/quick-reply dialogs. */
fun compatSelectablePostBodyLines(post: CompatPostSnapshot): List<String> =
    post.messageHtml.toCompatPlainText().lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .filterNot(::isCompatSystemPostLine)
        .toList()

private fun isCompatSystemPostLine(line: String): Boolean {
    val value = line.trim()
    return value.startsWith("このスレッドは", ignoreCase = false) ||
        value.startsWith("IP:", ignoreCase = true) ||
        value.startsWith("削除依頼") ||
        value.startsWith("管理者によって削除") ||
        value.startsWith("削除されました") ||
        value.startsWith("隔離されました") ||
        value.startsWith("キタ") ||
        value.startsWith("残り時間") ||
        value.startsWith("消えます")
}

fun compatQuoteSelection(candidates: List<CompatPostActionCandidate>, selectedIndices: Set<Int>): String {
    val quoted = candidates.mapIndexedNotNull { index, candidate -> candidate.value.takeIf { index in selectedIndices } }
        .joinToString("\n") { line -> line.lineSequence().joinToString("\n") { ">$it" } }
    // Leave the caret on a fresh reply line, matching the legacy reply form.
    return quoted.takeIf(String::isNotBlank)?.plus("\n\n").orEmpty()
}

fun compatQuickQuoteText(post: CompatPostSnapshot): String {
    val lines = compatSelectablePostBodyLines(post)
    // The reference reply form leaves an empty line after the generated quote
    // so the caret starts in the user's own reply area.  A single trailing LF
    // places the caret on the same visual line as the quote on Compose/TextField
    // implementations that normalize the final newline.
    return if (lines.isEmpty()) {
        ">No.${post.postNo}\n\n"
    } else {
        lines.joinToString("\n", postfix = "\n\n") { ">$it" }
    }
}

fun compatGoogleSearchTerms(post: CompatPostSnapshot): List<String> =
    // This deliberately follows ThreadWebSearchDialogFragment's broad token
    // matcher instead of splitting every word at punctuation.
    Regex("[0-9ァ-ヶa-zA-Z一-龠\\-ー \\u3000]+")
        .findAll(post.messageHtml.toCompatPlainText())
        .map { it.value.trim() }
        .filter(String::isNotBlank)
        .distinct()
        .take(100)
        .toList()
