package com.valoser.futacha.shared.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A screen of a navigation stack: [depth] 0 is the catalog, 1 a thread. Screens with the same [depth] but another [id]
 * (a tab switch, another board) change without animation, as in the originals.
 */
internal data class PushPopScreen<T>(val id: String, val depth: Int, val value: T)

/** Which way a change goes: +1 a push (deeper), -1 a pop, 0 none. */
internal fun pushPopDirection(from: PushPopScreen<*>, to: PushPopScreen<*>): Int = when {
    from.id == to.id || from.depth < 0 || to.depth < 0 -> 0
    to.depth > from.depth -> 1
    to.depth < from.depth -> -1
    else -> 0
}

/** iOS's push: about 0.35 s, the new screen from the right edge, the old one a third of the way to the left. */
internal const val PUSH_POP_DURATION_MILLIS = 350
private val PushPopEasing = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1f)

private fun <T> AnimatedContentTransitionScope<PushPopScreen<T>>.pushPopTransform(): ContentTransform {
    val spec = tween<androidx.compose.ui.unit.IntOffset>(PUSH_POP_DURATION_MILLIS, easing = PushPopEasing)
    return when (pushPopDirection(initialState, targetState)) {
        1 -> (slideInHorizontally(spec) { it } togetherWith slideOutHorizontally(spec) { -it / 3 })
            .apply { targetContentZIndex = 1f }.using(null)
        -1 -> (slideInHorizontally(spec) { -it / 3 } togetherWith slideOutHorizontally(spec) { it })
            .apply { targetContentZIndex = -1f }.using(null)
        else -> (EnterTransition.None togetherWith ExitTransition.None).using(null)
    }
}

/**
 * The screen change of ふたばー風 / ふたったー風 (App Store review 2026-11-16: they had none). [content] gets the screen it
 * draws, so a screen sliding out keeps drawing what it showed; read the screen's state from it, not from outside.
 */
@Composable
internal fun <T> PushPopTransition(
    screen: PushPopScreen<T>,
    modifier: Modifier = Modifier,
    content: @Composable (PushPopScreen<T>) -> Unit
) {
    AnimatedContent(
        targetState = screen,
        modifier = modifier,
        transitionSpec = { pushPopTransform() },
        contentKey = { it.id },
        label = "push-pop"
    ) { content(it) }
}
