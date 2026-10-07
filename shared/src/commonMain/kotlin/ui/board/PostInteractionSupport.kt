package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.ui.privacy.privacyWindowFilter
import kotlinx.coroutines.yield

internal const val POST_TAP_BEHAVIOR_KEY = "controlPostTapBehavior"
internal val LocalRelatedPostTap = compositionLocalOf { false }

@Composable
internal fun PostActionPreview(postNo: String, messageHtml: String, modifier: Modifier = Modifier) {
    val text = remember(messageHtml) { messageHtml.toCompatPlainText().trim().ifEmpty { "本文なし" } }
    Column(modifier.fillMaxWidth().privacyWindowFilter().testTag("post-action-preview").padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("No.$postNo", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(text, modifier = Modifier.heightIn(max = 144.dp).verticalScroll(rememberScrollState()),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Includes the selected post, direct sources and direct replies once, in original display order. */
internal fun relatedThreadPosts(post: Post, posts: Collection<Post>): List<Post> {
    val parents = post.quoteReferences.flatMap { it.targetPostIds }.toSet()
    return posts.filter { candidate ->
        candidate.id == post.id || candidate.id in parents ||
            candidate.quoteReferences.any { post.id in it.targetPostIds }
    }.distinctBy { it.id }
}

internal suspend fun relatedCompatPosts(postNo: String, posts: List<CompatPostSnapshot>): List<CompatPostSnapshot> {
    val selected = posts.firstOrNull { it.postNo == postNo } ?: return emptyList()
    fun targets(post: CompatPostSnapshot): Set<String> {
        if (post.isContentRedacted) return emptySet()
        if (post.quoteReferences.isNotEmpty()) return post.quoteReferences.flatMap { it.targetPostIds }.toSet()
        return post.messageHtml.toCompatPlainText().lineSequence().mapNotNull(::compatQuoteQueryForLine)
            .flatMap { resolveCompatQuotePosts(posts, post.position, it).asSequence().map { match -> match.postNo } }.toSet()
    }
    val parents = targets(selected)
    return posts.filterIndexed { index, post ->
        if (index % 64 == 0) yield()
        post.postNo == postNo || post.postNo in parents || postNo in targets(post)
    }.distinctBy { it.postNo }
}

/** Does not consume taps, links, media gestures or scrolling. */
internal fun Modifier.postPressFeedback(identity: String, color: Color): Modifier = composed {
    var pressed by remember(identity) { mutableStateOf(false) }
    this.background(if (pressed) color else Color.Transparent).pointerInput(identity) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false,
                pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
            pressed = true
            try {
                do {
                    val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    if (event.changes.any { (it.position - down.position).getDistance() > viewConfiguration.touchSlop }) pressed = false
                } while (event.changes.any { it.pressed })
            } finally { pressed = false }
        }
    }
}
