package com.valoser.futacha.shared.audio.voicevox

import com.valoser.futacha.shared.download.*
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.*

internal enum class VoiceAssetStage { CHECKING, MISSING, DOWNLOADING, VERIFYING, EXTRACTING, READY, PAUSED, FAILED }
internal data class VoiceAssetState(
    val stage: VoiceAssetStage = VoiceAssetStage.CHECKING,
    val bytes: Long = 0,
    val message: String? = null
) {
    val ready: Boolean get() = stage == VoiceAssetStage.READY
    val busy: Boolean get() = stage in setOf(VoiceAssetStage.DOWNLOADING, VoiceAssetStage.VERIFYING, VoiceAssetStage.EXTRACTING)
}
internal data class VoicePack(val model: Path, val dictionary: Path)
internal class VoicePackUnavailableException(message: String) : IllegalStateException(message)

// Covers recreated roots as well as the settings and every speaker. A reader holds
// this through native calls so deletion cannot remove a mapped dictionary/model.
private val voiceAssetLock = Mutex()

internal class VoiceAssetStore(
    private val directory: () -> Path,
    private val availableBytes: () -> Long?,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val downloader: () -> AssetDownloader = { KtorAssetDownloader() }
) {
    private val mutableState = MutableStateFlow(VoiceAssetState())
    val state = mutableState.asStateFlow()
    private val current: Path get() = directory().resolve(VoiceAssetManifest.PACK)
    private fun pack(path: Path) = VoicePack(path.resolve("0.vvm"), path.resolve("dict"))

    /** No HTTP or native initialization. Only listing metadata is read here. */
    suspend fun refresh() = withContext(AppDispatchers.io) {
        voiceAssetLock.withLock {
            if (!mutableState.value.busy) {
                try {
                    mutableState.value = VoiceAssetState(if (hasFiles(pack(current))) VoiceAssetStage.READY else VoiceAssetStage.MISSING)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { mutableState.value = VoiceAssetState(VoiceAssetStage.FAILED, message = "音声データの保存先を確認できませんでした") }
            }
        }
    }

    suspend fun install() = withContext(AppDispatchers.io) {
        voiceAssetLock.withLock {
            var staging: Path? = null
            try {
                val root = directory()
                fileSystem.createDirectories(root)
                val stage = root.resolve("${VoiceAssetManifest.PACK}.staging").also { staging = it }
                if (verify(pack(current))) {
                    mutableState.value = VoiceAssetState(VoiceAssetStage.READY)
                    return@withLock
                }
                check(availableBytes()?.let { it >= VoiceAssetManifest.STAGING_SPACE_BYTES } != false) {
                    "音声データの準備には約256MiBの空き容量が必要です"
                }
                fileSystem.deleteRecursively(stage, mustExist = false)
                fileSystem.createDirectories(stage)
                val transport = downloader()
                try {
                    val dictionary = root.resolve("${VoiceAssetManifest.dictionary.sha256}.tar.gz")
                    val model = root.resolve("${VoiceAssetManifest.model.sha256}.vvm")
                    download(transport, VoiceAssetManifest.dictionary, dictionary, 0)
                    download(transport, VoiceAssetManifest.model, model, VoiceAssetManifest.dictionary.bytes)
                    mutableState.value = VoiceAssetState(VoiceAssetStage.EXTRACTING, VoiceAssetManifest.DOWNLOAD_BYTES)
                    extractVoiceDictionary(fileSystem, dictionary, stage.resolve("dict"))
                    check(verify(VoicePack(model, stage.resolve("dict")))) { "音声データの検証に失敗しました" }
                    // With a fixed pack version, an invalid former installation is
                    // removed only after the complete replacement has been checked.
                    currentCoroutineContext().ensureActive()
                    fileSystem.atomicMove(model, stage.resolve("0.vvm"))
                    fileSystem.deleteRecursively(current, mustExist = false)
                    fileSystem.atomicMove(stage, current)
                    fileSystem.delete(dictionary, mustExist = false)
                    mutableState.value = VoiceAssetState(VoiceAssetStage.READY, VoiceAssetManifest.DOWNLOAD_BYTES)
                } finally { (transport as? AutoCloseable)?.close() }
            } catch (cancelled: CancellationException) {
                mutableState.value = VoiceAssetState(VoiceAssetStage.PAUSED, mutableState.value.bytes, "ダウンロードを中断しました。再開できます")
                throw cancelled
            } catch (failure: Exception) {
                mutableState.value = VoiceAssetState(VoiceAssetStage.FAILED, message = failure.message ?: "音声データを準備できませんでした")
                throw failure
            } finally {
                // Downloads are retained under their pinned hashes; no staging
                // directory is ever presented to the native engine.
                staging?.let { fileSystem.deleteRecursively(it, mustExist = false) }
            }
        }
    }

    suspend fun delete() = withContext(AppDispatchers.io) {
        voiceAssetLock.withLock {
            fileSystem.deleteRecursively(directory(), mustExist = false)
            mutableState.value = VoiceAssetState(VoiceAssetStage.MISSING)
        }
    }

    suspend fun <T> withVerifiedPack(block: suspend (VoicePack) -> T): T = withContext(AppDispatchers.io) {
        voiceAssetLock.withLock {
            val value = pack(current)
            if (!verify(value)) {
                mutableState.value = VoiceAssetState(VoiceAssetStage.FAILED, message = "音声データが見つからないか壊れています。設定からダウンロードしてください")
                throw VoicePackUnavailableException(requireNotNull(mutableState.value.message))
            }
            block(value)
        }
    }

    private fun regular(path: Path, expected: Long): Boolean {
        val metadata = fileSystem.metadataOrNull(path) ?: return false
        return metadata.isRegularFile && metadata.symlinkTarget == null && metadata.size == expected
    }
    private fun hasFiles(pack: VoicePack): Boolean = regular(pack.model, VoiceAssetManifest.model.bytes) &&
        VoiceAssetManifest.files.all { regular(pack.dictionary.resolve(it.name), it.bytes) }
    private suspend fun verify(pack: VoicePack): Boolean = hasFiles(pack) &&
        matches(pack.model, VoiceAssetManifest.model.bytes, VoiceAssetManifest.model.sha256) &&
        VoiceAssetManifest.files.all { matches(pack.dictionary.resolve(it.name), it.bytes, it.sha256) }

    private suspend fun matches(path: Path, bytes: Long, sha: String): Boolean {
        if (!regular(path, bytes)) return false
        HashingSource.sha256(fileSystem.source(path)).use { source ->
            val buffer = Buffer()
            var count = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = source.read(buffer, 64 * 1024L)
                if (read < 0) break
                count += read; buffer.clear()
                if (count > bytes) return false
            }
            return count == bytes && source.hash.hex() == sha
        }
    }

    private suspend fun download(transport: AssetDownloader, spec: AssetDistribution, target: Path, completed: Long) {
        if (matches(target, spec.bytes, spec.sha256)) return
        fileSystem.delete(target, mustExist = false)
        val partial = target.parent!!.resolve("${spec.sha256}.part")
        val metadata = fileSystem.metadataOrNull(partial)
        val offset = metadata?.takeIf { it.isRegularFile && it.symlinkTarget == null && it.size in 0L..spec.bytes }?.size ?: 0L
        if (metadata != null && (metadata.symlinkTarget != null || !metadata.isRegularFile || metadata.size !in 0L..spec.bytes)) {
            fileSystem.deleteRecursively(partial, mustExist = false)
        }
        suspend fun transfer(start: Long) {
            mutableState.value = VoiceAssetState(VoiceAssetStage.DOWNLOADING, completed + start)
            val raw = if (start == 0L) fileSystem.sink(partial) else fileSystem.appendingSink(partial)
            val bounded = object : Sink by raw {
                var count = start
                override fun write(source: Buffer, byteCount: Long) {
                    check(byteCount >= 0 && byteCount <= spec.bytes - count) { "音声データのサイズが一致しません" }
                    raw.write(source, byteCount); count += byteCount
                    mutableState.value = VoiceAssetState(VoiceAssetStage.DOWNLOADING, completed + count)
                }
            }
            bounded.buffer().use { sink ->
                if (start > 0 && transport.supportsResume) transport.resumeDownload(spec, sink, start)
                else transport.download(spec, sink)
            }
        }
        if (offset < spec.bytes) {
            try { transfer(if (transport.supportsResume) offset else 0L) }
            catch (_: AssetResumeUnavailableException) {
                fileSystem.delete(partial, mustExist = false)
                transfer(0L)
            }
        }
        mutableState.value = VoiceAssetState(VoiceAssetStage.VERIFYING, completed + spec.bytes)
        if (!matches(partial, spec.bytes, spec.sha256)) {
            fileSystem.delete(partial, mustExist = false)
            error("音声データの内容が一致しません。再試行してください")
        }
        fileSystem.atomicMove(partial, target)
    }
}
