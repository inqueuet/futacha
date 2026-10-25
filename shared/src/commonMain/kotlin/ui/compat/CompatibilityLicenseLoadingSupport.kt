package com.valoser.futacha.shared.ui.compat

import futacha.shared.generated.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun compatLicenseParagraphs(bytes: ByteArray): List<String> =
    bytes.decodeToString().lines().chunked(32).map { it.joinToString("\n") }

internal suspend fun loadCompatLicenseParagraphs(
    path: String,
    readBytes: suspend (String) -> ByteArray = { Res.readBytes(it) },
    decode: (ByteArray) -> List<String> = ::compatLicenseParagraphs
): List<String> {
    val bytes = readBytes(path)
    // The bundled VOICEVOX CORE notice is about 2.6MB / 51k lines (L11): decode and split it off the UI thread.
    // Default, not the parsing dispatcher, so it never occupies the thread HTML parsing slots.
    return withContext(Dispatchers.Default) { decode(bytes) }
}
