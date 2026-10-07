package com.valoser.futacha.shared.ui.futaber.mht

import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.valoser.futacha.shared.compat.rememberExperienceProfileActivityResultLauncher
import com.valoser.futacha.shared.util.ImageData
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext

private const val OUT_OF_MEMORY_MESSAGE = "ファイルが大きすぎるか、メモリが足りないため読み込めませんでした"

@Composable
internal actual fun rememberFutaberMhtPickerLauncher(
    onSelected: (ImageData) -> Unit,
    onError: (String) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnSelected = rememberUpdatedState(onSelected)
    val currentOnError = rememberUpdatedState(onError)
    val launcher = rememberExperienceProfileActivityResultLauncher(ActivityResultContracts.OpenDocument()) { uri, _ ->
        uri ?: return@rememberExperienceProfileActivityResultLauncher
        scope.launch {
            try {
                val picked = withContext(Dispatchers.IO) {
                    var name: String? = null
                    var declaredSize = -1L
                    context.contentResolver.query(
                        uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) declaredSize = cursor.getLong(sizeIndex)
                        }
                    }
                    // The size the provider reports is checked before anything is read, so a file that is too big is refused at once.
                    if (declaredSize > FUTABER_MHT_MAX_READ_BYTES) error(FUTABER_MHT_TOO_LARGE_MESSAGE)
                    if (declaredSize == 0L) error("ファイルが空です")
                    val bytes = context.contentResolver.openInputStream(uri)?.use { input -> readLimited(input, declaredSize) }
                        ?: error("ファイルを読み込めませんでした")
                    ImageData(bytes, name.orEmpty().ifBlank { "thread.mht" })
                }
                currentOnSelected.value(picked)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: OutOfMemoryError) {
                currentOnError.value(OUT_OF_MEMORY_MESSAGE)
            } catch (error: Exception) {
                currentOnError.value(error.message ?: "ファイルを読み込めませんでした")
            }
        }
    }
    // A .mht is reported under several types (or none) depending on the files app, so any file is offered.
    return { launcher.launch(arrayOf("*/*")) }
}

/**
 * All of [input], never more than [FUTABER_MHT_MAX_READ_BYTES]. The buffer is made at the size the provider reported (one
 * allocation, no copy); it grows only when that size was not known or turns out wrong.
 */
private suspend fun readLimited(input: InputStream, declaredSize: Long): ByteArray {
    val limit = FUTABER_MHT_MAX_READ_BYTES.toInt()
    var buffer = ByteArray(if (declaredSize in 1..limit.toLong()) declaredSize.toInt() else 1 shl 20)
    var total = 0
    while (true) {
        if (total == buffer.size) {
            // Full: the file ends here (the size was right), or there is more (the size was not known or wrong).
            val next = runInterruptible { input.read() }
            if (next == -1) break
            if (total >= limit) error(FUTABER_MHT_TOO_LARGE_MESSAGE)
            buffer = buffer.copyOf(minOf(buffer.size.toLong() * 2L, limit.toLong()).toInt())
            buffer[total++] = next.toByte()
            continue
        }
        val count = runInterruptible { input.read(buffer, total, buffer.size - total) }
        if (count < 0) break
        total += count
    }
    if (total == 0) error("ファイルが空です")
    return if (total == buffer.size) buffer else buffer.copyOf(total)
}
