package com.valoser.futacha.shared.repo.aimoge

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * The 2nd and 3rd pictures of one あいもげ post, carried beside a [com.valoser.futacha.shared.repo.BoardRepository]
 * reply/new thread (whose signature holds one picture) so every Futaba path stays as it is. Only the あいもげ
 * repository reads it; a post screen sets it only for an あいもげ board.
 */
class AimogeExtraPictures(val pictures: List<Pair<ByteArray, String>>) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<AimogeExtraPictures>
    override fun toString(): String = "AimogeExtraPictures(${pictures.size})"
}

/**
 * The 2nd and 3rd pictures a post screen chose for an あいもげ post. The post screens of the four modes hand only one
 * picture to the repository (and may still shrink it), so the rest wait here for the board they were picked on; the
 * あいもげ repository takes them with the post's first picture and forgets them once the post went out. One slot:
 * a new pick on another board replaces it, and a screen clears it when its picture is removed or the screen closes.
 */
object AimogePendingExtras {
    private class Held(val slug: String, val pictures: List<Pair<ByteArray, String>>)
    private val held = kotlinx.coroutines.flow.MutableStateFlow<Held?>(null)

    private fun slugOf(boardUrl: String): String? =
        com.valoser.futacha.shared.compat.canonicalizeSiteBoardUrl(boardUrl)
            ?.takeIf { it.site == com.valoser.futacha.shared.compat.BoardSite.AIMOGE }?.boardPath

    fun set(boardUrl: String, pictures: List<Pair<ByteArray, String>>) {
        val slug = slugOf(boardUrl)
        held.value = if (slug == null || pictures.isEmpty()) null else Held(slug, pictures)
    }

    fun of(boardUrl: String): List<Pair<ByteArray, String>> =
        held.value?.takeIf { it.slug == slugOf(boardUrl) }?.pictures.orEmpty()

    fun clear() { held.value = null }
    fun clearAll() = clear()
}

/** The 2nd and 3rd pictures: あいもげ takes 3 in one post. */
const val AIMOGE_EXTRA_PICTURES_MAX = 2
