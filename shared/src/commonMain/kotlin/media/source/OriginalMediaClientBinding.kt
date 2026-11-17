package com.valoser.futacha.shared.media.source

import io.ktor.client.HttpClient
import io.ktor.util.AttributeKey

private val OriginalMediaSourceKey = AttributeKey<OriginalMediaSource>("FutachaOriginalMediaSource")

/** Bind once while constructing the host graph, before publishing its HTTP client.
 * Save services and background jobs resolve the same app-owned source as Coil.
 * This binding does not own/close the source and never decorates it with prompt parsing.
 */
fun HttpClient.bindOriginalMediaSource(source: OriginalMediaSource) {
    val previous = attributes.getOrNull(OriginalMediaSourceKey)
    check(previous == null || previous === source) { "Original media source is already bound" }
    attributes.put(OriginalMediaSourceKey, source)
}

internal fun HttpClient.originalMediaSourceOrNull(): OriginalMediaSource? =
    attributes.getOrNull(OriginalMediaSourceKey)
