package com.valoser.futacha.shared.ai

import com.valoser.futacha.shared.model.Post

/** Local repetition signals only. Quoted replies are exempt; no topic/intent inference. */
internal fun detectCopyPastePosts(posts: List<Post>): List<PostModerationResult> {
    val firstByBody = mutableMapOf<String, String>()
    return buildList {
        for (post in posts) {
            val text = openAiPostText(post)
            // Preserve normal quote-and-reply conversations, including escaped HTML quotes.
            if (text.lineSequence().any { it.trimStart().startsWith('>') || it.trimStart().startsWith('＞') }) continue
            val lines = text.lineSequence().map { it.trim() }
                .filter { it.isNotEmpty() && it != "スレッドを立てた人によって削除されました" &&
                    it != "書き込みをした人によって削除されました" }.toList()
            val normalized = lines.joinToString("").filterNot { it.isWhitespace() }
            if (normalized.length < 30) continue // Short acknowledgments must remain visible.
            val first = firstByBody.getOrPut(normalized) { post.id }
            val repeatedLine = lines.map { line -> line.filterNot { it.isWhitespace() } }
                .filter { it.length >= 4 }.groupingBy { it }.eachCount()
                .any { (line, count) -> count >= 6 && line.length * count >= 32 &&
                    line.length.toLong() * count * 100 >= normalized.length.toLong() * 80 }
            val reason = when {
                first != post.id -> "コピペ候補：No.$first と同じ本文の再投稿（引用を除く）"
                repeatedLine -> "コピペ候補：本文の大部分で同じ行を反復（引用を除く）"
                else -> null
            }
            if (reason != null) add(PostModerationResult(post.id, true, reason))
        }
    }
}
