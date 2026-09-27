package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.model.ThreadPage
import io.ktor.http.Url

/** Futapo substitutes these successful PNG responses for attachments it never saved. */
private fun isFutapoMissingImage(value: String?): Boolean {
    val url = value?.let { runCatching { Url(it) }.getOrNull() } ?: return false
    return url.host == "kako.futakuro.com" && url.encodedPath in setOf("/futa/404.png", "/futa/404s.png")
}

internal fun normalizeCompatArchiveMedia(page: ThreadPage): ThreadPage = page.copy(
    posts = page.posts.map { post ->
        fun secureFutapo(value: String?): String? =
            if (value?.startsWith("http://kako.futakuro.com/") == true) "https://" + value.removePrefix("http://") else value
        val image = secureFutapo(post.imageUrl.takeUnless(::isFutapoMissingImage))
        post.copy(
            imageUrl = image,
            // Request the named original so a real 404 can trigger mirror recovery.
            thumbnailUrl = if (isFutapoMissingImage(post.thumbnailUrl)) image else secureFutapo(post.thumbnailUrl)
        )
    }
)
