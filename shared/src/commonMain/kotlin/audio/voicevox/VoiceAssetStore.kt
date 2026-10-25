package com.valoser.futacha.shared.audio.voicevox

import com.valoser.futacha.shared.download.*
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
internal class VoicePackLease(val pack: VoicePack, private val release: () -> Unit) : AutoCloseable {
    private val closed = MutableStateFlow(false)
    override fun close() { if (closed.compareAndSet(false, true)) release() }
}
internal class VoicePackUnavailableException(message: String) : IllegalStateException(message)
/** A failure whose message is written for the voice settings screen. */
internal class VoiceAssetUserException(message: String) : IllegalStateException(message)

/** The settings text for a failed install: never the image models' wording or a raw exception (L7). */
internal fun voiceAssetFailureMessage(failure: Throwable): String = when (failure) {
    is VoiceAssetUserException -> failure.message ?: VOICE_ASSET_GENERIC_FAILURE
    is AssetHttpStatusException -> "音声データを取得できません（HTTP ${failure.status}）。時間をおいて再試行してください"
    else -> VOICE_ASSET_GENERIC_FAILURE
}
private const val VOICE_ASSET_GENERIC_FAILURE = "音声データを取得・保存できませんでした。通信状態と空き容量を確認して再試行してください"
private const val VOICE_ASSET_LOCATION_FAILURE = "音声データの保存先を確認できませんでした"
private const val VOICE_ASSET_CAPACITY_FAILURE = "音声データの準備には約256MiBの空き容量が必要です"

// Shared by recreated roots, settings, and every speaker.
private val voiceAssetAccess = VoiceAssetAccess()

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
    // A pack whose sizes match but whose contents failed verification (L1). Remembered on disk so neither a
    // settings refresh nor a recreated store presents it as ready, and reading does not hash it again until
    // the user explicitly downloads (install) or deletes the data.
    private val invalidMarker: Path get() = directory().resolve("${VoiceAssetManifest.PACK}.invalid")
    private fun knownInvalid(): Boolean = fileSystem.metadataOrNull(invalidMarker) != null
    private fun markInvalid() {
        try { fileSystem.write(invalidMarker) { writeUtf8("verification failed\n") } }
        catch (_: Exception) { /* Best effort: without the marker the next read verifies again. */ }
    }
    private fun clearInvalid() { fileSystem.delete(invalidMarker, mustExist = false) }

    /** No HTTP or native initialization. Only listing metadata is read here. While the data is being
     * replaced or deleted the shown state stays as it is instead of waiting for the change (L8). */
    suspend fun refresh(): Unit = withContext(AppDispatchers.io) {
        if (mutableState.value.busy) return@withContext
        voiceAssetAccess.inspectUnlessMutating {
            if (!mutableState.value.busy) {
                try {
                    val previous = mutableState.value
                    val root = directory()
                    mutableState.value = when {
                        knownInvalid() -> VoiceAssetState(VoiceAssetStage.FAILED, message = VOICE_PACK_UNAVAILABLE_MESSAGE)
                        hasFiles(pack(current)) -> VoiceAssetState(VoiceAssetStage.READY)
                        previous.stage == VoiceAssetStage.FAILED -> previous
                        fileSystem.listOrNull(root)?.isNotEmpty() == true -> VoiceAssetState(
                            VoiceAssetStage.PAUSED,
                            retainedDownloadBytes(root),
                            "ダウンロードを中断しました。再開できます"
                        )
                        else -> VoiceAssetState(VoiceAssetStage.MISSING)
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { mutableState.value = VoiceAssetState(VoiceAssetStage.FAILED, message = "音声データの保存先を確認できませんでした") }
            }
        }
    }

    private fun retainedDownloadBytes(root: Path): Long = listOf(
        VoiceAssetManifest.dictionary to "tar.gz", VoiceAssetManifest.model to "vvm"
    ).sumOf { (spec, extension) ->
        listOf(root.resolve("${spec.sha256}.part"), root.resolve("${spec.sha256}.$extension"))
            .mapNotNull { fileSystem.metadataOrNull(it) }
            .filter { it.isRegularFile && it.symlinkTarget == null }
            .maxOfOrNull { it.size?.coerceIn(0L, spec.bytes) ?: 0L } ?: 0L
    }

    suspend fun install() = withContext(AppDispatchers.io) {
        try { directory() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            mutableState.value = VoiceAssetState(VoiceAssetStage.FAILED, message = VOICE_ASSET_LOCATION_FAILURE)
            throw failure
        }
        // A ready installation needs no mutation and must not wait for playback.
        val alreadyInstalled = try { voiceAssetAccess.inspect { verify(pack(current)) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            mutableState.value = VoiceAssetState(VoiceAssetStage.FAILED, message = voiceAssetFailureMessage(failure))
            throw failure
        }
        if (alreadyInstalled) {
            voiceAssetAccess.inspect { clearInvalid() }
            mutableState.value = VoiceAssetState(VoiceAssetStage.READY)
            return@withContext
        }
        voiceAssetAccess.mutate {
            var staging: Path? = null
            try {
                val root = directory()
                fileSystem.createDirectories(root)
                val stage = root.resolve("${VoiceAssetManifest.PACK}.staging").also { staging = it }
                if (verify(pack(current))) {
                    clearInvalid()
                    mutableState.value = VoiceAssetState(VoiceAssetStage.READY)
                    return@mutate
                }
                val dictionary = root.resolve("${VoiceAssetManifest.dictionary.sha256}.tar.gz")
                val model = root.resolve("${VoiceAssetManifest.model.sha256}.vvm")
                // Admission (L5): what is already here lowers the requirement. Verified completed downloads count
                // fully; partial ones only for a transport that can resume them. Nothing is created or downloaded
                // before a refusal, and a transport created only to ask about resuming is closed again.
                val dictionaryReady = matches(dictionary, VoiceAssetManifest.dictionary.bytes, VoiceAssetManifest.dictionary.sha256)
                val modelReady = matches(model, VoiceAssetManifest.model.bytes, VoiceAssetManifest.model.sha256)
                val completedCredit = (if (dictionaryReady) VoiceAssetManifest.dictionary.bytes else 0L) +
                    (if (modelReady) VoiceAssetManifest.model.bytes else 0L)
                val partialCredit = (if (dictionaryReady) 0L else resumablePartialBytes(root, VoiceAssetManifest.dictionary)) +
                    (if (modelReady) 0L else resumablePartialBytes(root, VoiceAssetManifest.model))
                val available = availableBytes()
                fun admits(credit: Long) = available == null || available >= voiceAssetRequiredFreeBytes(credit)
                var transport: AssetDownloader? = null
                if (!admits(completedCredit)) {
                    if (partialCredit == 0L || !admits(completedCredit + partialCredit)) throw VoiceAssetUserException(VOICE_ASSET_CAPACITY_FAILURE)
                    val resumable = downloader()
                    if (!resumable.supportsResume) {
                        (resumable as? AutoCloseable)?.close()
                        throw VoiceAssetUserException(VOICE_ASSET_CAPACITY_FAILURE)
                    }
                    transport = resumable
                }
                val activeTransport = transport ?: downloader()
                try {
                    fileSystem.deleteRecursively(stage, mustExist = false)
                    fileSystem.createDirectories(stage)
                    download(activeTransport, VoiceAssetManifest.dictionary, dictionary, 0, verified = dictionaryReady)
                    download(activeTransport, VoiceAssetManifest.model, model, VoiceAssetManifest.dictionary.bytes, verified = modelReady)
                    mutableState.value = VoiceAssetState(VoiceAssetStage.EXTRACTING, VoiceAssetManifest.DOWNLOAD_BYTES)
                    extractVoiceDictionary(fileSystem, dictionary, stage.resolve("dict"))
                    if (!verify(VoicePack(model, stage.resolve("dict")))) throw VoiceAssetUserException("音声データの検証に失敗しました")
                    // With a fixed pack version, an invalid former installation is
                    // removed only after the complete replacement has been checked.
                    promoteVerifiedVoicePack(fileSystem, model, stage, current)
                    clearInvalid()
                    fileSystem.delete(dictionary, mustExist = false)
                    mutableState.value = VoiceAssetState(VoiceAssetStage.READY, VoiceAssetManifest.DOWNLOAD_BYTES)
                } finally { (activeTransport as? AutoCloseable)?.close() }
            } catch (cancelled: CancellationException) {
                mutableState.value = VoiceAssetState(VoiceAssetStage.PAUSED, mutableState.value.bytes, "ダウンロードを中断しました。再開できます")
                throw cancelled
            } catch (failure: Exception) {
                mutableState.value = VoiceAssetState(VoiceAssetStage.FAILED, message = voiceAssetFailureMessage(failure))
                throw failure
            } finally {
                // Downloads are retained under their pinned hashes; no staging
                // directory is ever presented to the native engine.
                staging?.let { fileSystem.deleteRecursively(it, mustExist = false) }
            }
        }
    }

    suspend fun delete() = withContext(AppDispatchers.io) {
        voiceAssetAccess.mutate {
            fileSystem.deleteRecursively(directory(), mustExist = false)
            mutableState.value = VoiceAssetState(VoiceAssetStage.MISSING)
        }
    }

    suspend fun <T> withVerifiedPack(block: suspend (VoicePack) -> T): T = withContext(AppDispatchers.io) {
        val lease = acquireVerifiedPack()
        try { block(lease.pack) } finally { lease.close() }
    }

    // Called on the native/IO dispatcher. Ownership is established before returning,
    // without carrying a resource across a cancellable dispatcher boundary.
    suspend fun acquireVerifiedPack(): VoicePackLease = voiceAssetAccess.lease {
        val value = pack(current)
        val known = knownInvalid()
        if (known || !verify(value)) {
            // Missing files stay MISSING/PAUSED for refresh; only matching sizes with wrong contents are remembered.
            if (!known && hasFiles(value)) markInvalid()
            mutableState.value = VoiceAssetState(VoiceAssetStage.FAILED, message = VOICE_PACK_UNAVAILABLE_MESSAGE)
            throw VoicePackUnavailableException(VOICE_PACK_UNAVAILABLE_MESSAGE)
        }
        mutableState.value = VoiceAssetState(VoiceAssetStage.READY)
        value
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

    /** A regular partial file that a resumed transfer can continue. */
    private fun resumablePartialBytes(root: Path, spec: AssetDistribution): Long {
        val metadata = fileSystem.metadataOrNull(root.resolve("${spec.sha256}.part")) ?: return 0L
        val size = metadata.size ?: return 0L
        return if (metadata.isRegularFile && metadata.symlinkTarget == null && size in 1L..spec.bytes) size else 0L
    }

    private suspend fun download(transport: AssetDownloader, spec: AssetDistribution, target: Path, completed: Long,
        verified: Boolean = false) {
        if (verified || matches(target, spec.bytes, spec.sha256)) return
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
                    if (byteCount < 0 || byteCount > spec.bytes - count) throw VoiceAssetUserException("音声データのサイズが一致しません")
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
            throw VoiceAssetUserException("音声データの内容が一致しません。再試行してください")
        }
        fileSystem.atomicMove(partial, target)
    }
}

internal const val VOICE_PACK_UNAVAILABLE_MESSAGE = "音声データが見つからないか壊れています。設定からダウンロードしてください"

/** Admission budget seam; retained files are accounted for by the installer. */
internal fun voiceAssetRequiredFreeBytes(reusableDownloadBytes: Long = 0L): Long =
    VoiceAssetManifest.STAGING_SPACE_BYTES - reusableDownloadBytes.coerceIn(0L, VoiceAssetManifest.DOWNLOAD_BYTES)
