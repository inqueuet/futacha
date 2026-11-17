package com.valoser.futacha.shared.audio.voicevox

import com.valoser.futacha.shared.audio.*
import com.valoser.futacha.shared.download.AssetDownloader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import okio.*
import kotlin.random.Random

/** Small corrupt payloads with the pinned metadata sizes; never allocates/downloads a real voice pack. */
internal class VoiceAssetFailureFixture(downloader: () -> AssetDownloader = { error("No network in the voice fixture") }) {
    val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve("voice-failure-${Random.nextLong()}")
    private val installed = root.resolve(VoiceAssetManifest.PACK)
    val sourceOpens = MutableStateFlow(0)
    private val sizes = buildMap {
        put(installed.resolve("0.vvm"), VoiceAssetManifest.model.bytes)
        VoiceAssetManifest.files.forEach { put(installed.resolve("dict").resolve(it.name), it.bytes) }
    }
    val fileSystem = object : ForwardingFileSystem(FileSystem.SYSTEM) {
        override fun metadataOrNull(path: Path): FileMetadata? {
            val actual = super.metadataOrNull(path) ?: return null
            val expected = sizes[path] ?: return actual
            return FileMetadata(isRegularFile = actual.isRegularFile, isDirectory = actual.isDirectory,
                symlinkTarget = actual.symlinkTarget, size = expected)
        }
        override fun source(file: Path): Source {
            sourceOpens.update { it + 1 }
            return super.source(file)
        }
    }
    val assets = VoiceAssetStore({ root }, { Long.MAX_VALUE }, fileSystem, downloader)
    var beforeVoiceAcquire: suspend () -> Unit = {}
    val systemSpoken = MutableStateFlow<List<String>>(emptyList())
    val system = object : SpeechBackend {
        override val supportsPitch = true
        override suspend fun availableVoices() = emptyList<SpeechVoice>()
        override fun configure(settings: SpeechSettings) {}
        override suspend fun prepare() {}
        override suspend fun speak(text: String) { systemSpoken.update { it + text } }
        override fun stop() {}
        override fun close() {}
    }
    val platform = object : VoicePlatform {
        override val available = true
        override val unavailableReason = ""
        override fun directory() = root
        override fun availableBytes() = Long.MAX_VALUE
        override fun createNative(pack: VoicePack): VoiceNativeSession = error("Corrupt bytes must never enter native code")
        override fun createPlayer() = object : VoicePcmPlayer {
            override suspend fun prepare(onStop: () -> Unit) { beforeVoiceAcquire() }
            override suspend fun play(wav: ByteArray) = error("No real PCM playback in this fixture")
            override fun stop() {}
            override fun close() {}
        }
    }
    val runtime = SpeechRuntime(null, platform, assets)
    val speaker = TextSpeaker(null, runtime, systemEngine = { system })
    init {
        sizes.keys.forEach { path ->
            FileSystem.SYSTEM.createDirectories(requireNotNull(path.parent))
            FileSystem.SYSTEM.write(path) { writeByte(0) }
        }
        speaker.configure(SpeechSettings(engine = SpeechEngineKind.VOICEVOX_ZUNDAMON))
    }
    fun close() { speaker.close(); runtime.close(); FileSystem.SYSTEM.deleteRecursively(root, false) }
}
