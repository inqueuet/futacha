package com.valoser.futacha.shared.audio.voicevox

import kotlinx.coroutines.runBlocking
import okio.*
import kotlin.random.Random
import kotlin.test.*

class VoiceDictionaryExtractorTest {
    @Test fun traversalLinksUnknownFilesAndOversizeEntriesAreRejectedBeforeWriting() = runBlocking {
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve("voice-tar-${Random.nextLong()}")
        val fs = FileSystem.SYSTEM
        fs.createDirectories(root)
        try {
            val prefix = VoiceAssetManifest.DICTIONARY_ROOT + "/"
            for ((name, type, size) in listOf(
                Triple(prefix + "../outside", '0', 0L), Triple(prefix + "COPYING", '2', 5865L),
                Triple(prefix + "unknown", '0', 0L), Triple(prefix + "COPYING", '0', 1_000_000_000L))) {
                val archive = root.resolve("dict.tar.gz")
                GzipSink(fs.sink(archive)).buffer().use { it.write(header(name, type, size)); it.write(ByteArray(1024)) }
                val target = root.resolve("output")
                assertFails { extractVoiceDictionary(fs, archive, target) }
                assertTrue(fs.list(target).isEmpty())
                assertFalse(fs.exists(root.resolve("outside")))
            }
        } finally { fs.deleteRecursively(root, false) }
    }
    private fun header(name: String, type: Char, size: Long): ByteArray = ByteArray(512).also { value ->
        fun field(offset: Int, text: String) { text.encodeToByteArray().copyInto(value, offset) }
        field(0, name); field(124, size.toString(8).padStart(11, '0'))
        value[156] = type.code.toByte()
        for (i in 148..155) value[i] = 32
        val sum = value.sumOf { it.toInt() and 255 }
        field(148, sum.toString(8).padStart(6, '0') + "\u0000 ")
    }
}
