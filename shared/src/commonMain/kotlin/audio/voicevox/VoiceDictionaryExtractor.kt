package com.valoser.futacha.shared.audio.voicevox

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okio.*

/** Only the pinned archive's nine regular files and root directory are accepted. */
internal suspend fun extractVoiceDictionary(fileSystem: FileSystem, archive: Path, target: Path) {
    fileSystem.createDirectories(target)
    val wanted = VoiceAssetManifest.files.associateBy { it.name }
    val seen = mutableSetOf<String>()
    var expanded = 0L
    GzipSource(fileSystem.source(archive)).buffer().use { source ->
        while (true) {
            currentCoroutineContext().ensureActive()
            val header = source.readByteArray(512)
            if (header.all { it == 0.toByte() }) {
                check(source.readByteArray(512).all { it == 0.toByte() }) { "辞書アーカイブの終端が不正です" }
                // Consume only bounded zero padding, also checking the gzip footer/CRC.
                var padding = 0
                while (!source.exhausted()) {
                    check(padding++ < 10240 && source.readByte() == 0.toByte()) { "辞書アーカイブの末尾が不正です" }
                }
                break
            }
            fun field(at: Int, length: Int): String = header.copyOfRange(at, at + length)
                .takeWhile { it != 0.toByte() }.toByteArray().decodeToString()
            fun octal(at: Int, length: Int): Long = field(at, length).trim().toLongOrNull(8)
                ?: error("辞書アーカイブの数値が不正です")
            val checksum = header.indices.sumOf { if (it in 148..155) 32L else (header[it].toInt() and 255).toLong() }
            check(octal(148, 8) == checksum && field(345, 155).isEmpty()) { "辞書アーカイブのヘッダーが不正です" }
            val name = field(0, 100)
            val size = octal(124, 12)
            val type = header[156].toInt().toChar()
            if (name.trimEnd('/') == VoiceAssetManifest.DICTIONARY_ROOT && type == '5' && size == 0L) continue
            val prefix = VoiceAssetManifest.DICTIONARY_ROOT + "/"
            check(name.startsWith(prefix) && (type == '0' || type == '\u0000')) { "辞書に許可されないファイルがあります" }
            val leaf = name.removePrefix(prefix)
            val spec = wanted[leaf] ?: error("辞書に未知のファイルがあります")
            check(seen.add(leaf) && size == spec.bytes) { "辞書のファイル構成が一致しません" }
            expanded += size
            check(expanded <= VoiceAssetManifest.DICTIONARY_BYTES)
            val output = target.resolve(leaf)
            val hash = HashingSink.sha256(fileSystem.sink(output))
            hash.buffer().use { sink ->
                var remaining = size
                while (remaining > 0) {
                    currentCoroutineContext().ensureActive()
                    val count = minOf(64 * 1024L, remaining)
                    sink.write(source.readByteArray(count))
                    remaining -= count
                }
            }
            check(hash.hash.hex() == spec.sha256) { "辞書の内容が一致しません" }
            source.skip((512 - size % 512) % 512)
        }
    }
    check(seen == wanted.keys && expanded == VoiceAssetManifest.DICTIONARY_BYTES) { "辞書のファイルが不足しています" }
}
