@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.valoser.futacha.shared.media.prompt

import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.bookmarkedMediaDirectoryForPath
import com.valoser.futacha.shared.util.localMediaSavePath
import kotlinx.coroutines.withContext

internal actual suspend fun readLocalGenerationMetadata(url: String, platformContext: Any?): GenerationMetadata = withContext(AppDispatchers.io) {
    require(isLocalPromptMediaUrl(url) && !url.startsWith("content://", true))
    val scoped = bookmarkedMediaDirectoryForPath(localMediaSavePath(url))
    val accessed = scoped?.startAccessingSecurityScopedResource() == true
    try { readLocalPromptPath(url) }
    finally { if (accessed) scoped?.stopAccessingSecurityScopedResource() }
}
