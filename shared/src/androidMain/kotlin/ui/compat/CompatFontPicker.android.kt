package com.valoser.futacha.shared.ui.compat

import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.valoser.futacha.shared.compat.rememberExperienceProfileActivityResultLauncher
import com.valoser.futacha.shared.util.ImageData
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext

private const val MAX_CUSTOM_FONT_BYTES = 16L * 1024L * 1024L
private const val MAX_CUSTOM_FONT_ZERO_READS = 100
private const val CUSTOM_FONT_ZERO_READ_BACKOFF_MILLIS = 10L

@Composable
internal actual fun rememberCompatFontPickerLauncher(
    onSelected: (ImageData) -> Unit,
    onError: (String) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnSelected = rememberUpdatedState(onSelected)
    val currentOnError = rememberUpdatedState(onError)
    val launcher = rememberExperienceProfileActivityResultLauncher(
        ActivityResultContracts.OpenDocument()
    ) { uri, _ ->
        uri ?: return@rememberExperienceProfileActivityResultLauncher
        scope.launch {
            runCatching {
                val bytes = withContext(Dispatchers.IO) {
                    val name = context.contentResolver.query(
                        uri,
                        arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                        null,
                        null,
                        null
                    )?.use { cursor ->
                        val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
                    }.orEmpty().ifBlank { "font.ttf" }
                    val data = context.contentResolver.openInputStream(uri)?.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0L
                        var zeroReadCount = 0
                        while (true) {
                            val count = runInterruptible { input.read(buffer) }
                            if (count < 0) break
                            if (count == 0) {
                                zeroReadCount += 1
                                check(zeroReadCount < MAX_CUSTOM_FONT_ZERO_READS) {
                                    "フォントファイルの読み込みが停止しました"
                                }
                                delay(CUSTOM_FONT_ZERO_READ_BACKOFF_MILLIS)
                                continue
                            }
                            zeroReadCount = 0
                            total += count
                            if (total > MAX_CUSTOM_FONT_BYTES) {
                                error("フォントファイルが大きすぎます（上限16MB）")
                            }
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                        ?: error("フォントを読み込めませんでした")
                    name to data
                }
                ImageData(bytes.second, bytes.first)
            }.onSuccess { currentOnSelected.value(it) }
                .onFailure { currentOnError.value(it.message ?: "フォントを読み込めませんでした") }
        }
    }
    return {
        launcher.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/octet-stream"))
    }
}
