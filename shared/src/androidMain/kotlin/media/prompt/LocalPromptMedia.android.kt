package com.valoser.futacha.shared.media.prompt

import android.content.Context
import android.net.Uri
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

internal actual suspend fun readLocalGenerationMetadata(url: String, platformContext: Any?): GenerationMetadata {
    require(isLocalPromptMediaUrl(url))
    if (!url.startsWith("content://", true)) return readLocalPromptPath(url)
    val resolver = requireNotNull(platformContext as? Context).contentResolver
    return withContext(AppDispatchers.io) {
        val descriptor = runInterruptible { resolver.openAssetFileDescriptor(Uri.parse(url), "r") }
            ?: error("保存済みの原本を開けません")
        descriptor.use { asset ->
            asset.createInputStream().use { input ->
                val channel = input.channel
                val initialSize = channel.size()
                val size = asset.declaredLength.takeIf { it >= 0 } ?: (initialSize - asset.startOffset)
                require(size >= 0)
                val result = MediaGenerationMetadataReader().read(size) { offset, count ->
                    require(offset >= 0 && count >= 0 && offset <= size - count)
                    currentCoroutineContext().ensureActive()
                    val bytes = ByteArray(count)
                    runInterruptible {
                        channel.position(asset.startOffset + offset)
                        val buffer = ByteBuffer.wrap(bytes)
                        while (buffer.hasRemaining()) check(channel.read(buffer) > 0) { "保存済みの原本が途中で終了しました" }
                    }
                    bytes
                }
                check(channel.size() == initialSize) { "生成情報の読み取り中にファイルが変更されました" }
                result
            }
        }
    }
}
