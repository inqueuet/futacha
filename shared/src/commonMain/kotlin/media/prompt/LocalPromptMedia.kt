package com.valoser.futacha.shared.media.prompt

import com.valoser.futacha.shared.media.source.readOriginalMediaPrefix
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.localMediaSavePath
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.use

internal fun isLocalPromptMediaUrl(value: String): Boolean = com.valoser.futacha.shared.util.isAbsoluteLocalMediaPath(value) ||
    value.startsWith("file:/", true) || value.startsWith("content://", true)

/** Reads the saved original in place. This operation cannot use the network or create a copy. */
internal expect suspend fun readLocalGenerationMetadata(url: String, platformContext: Any?): GenerationMetadata

internal suspend fun readLocalPromptPath(url: String): GenerationMetadata = withContext(AppDispatchers.io) {
    val path = localMediaSavePath(url).toPath()
    val fs = FileSystem.SYSTEM
    val before = fs.metadata(path)
    require(before.isRegularFile)
    fs.openReadOnly(path).use { file ->
        val size = file.size()
        val result = MediaGenerationMetadataReader().read(size) { offset, count ->
            require(offset >= 0 && count >= 0 && offset <= size - count)
            file.readOriginalMediaPrefix(offset, count)
        }
        val after = fs.metadata(path)
        check(file.size() == size && after.size == before.size && after.lastModifiedAtMillis == before.lastModifiedAtMillis) {
            "生成情報の読み取り中にファイルが変更されました"
        }
        result
    }
}
