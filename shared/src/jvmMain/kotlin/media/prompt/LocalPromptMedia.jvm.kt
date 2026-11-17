package com.valoser.futacha.shared.media.prompt

internal actual suspend fun readLocalGenerationMetadata(url: String, platformContext: Any?): GenerationMetadata {
    require(isLocalPromptMediaUrl(url) && !url.startsWith("content://", true))
    return readLocalPromptPath(url)
}
