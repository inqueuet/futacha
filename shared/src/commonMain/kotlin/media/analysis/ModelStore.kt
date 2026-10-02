package com.valoser.futacha.shared.media.analysis

import com.valoser.futacha.shared.media.*
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.*
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.time.Clock

internal fun interface ModelDownloader {
    suspend fun download(distribution: ModelDistribution, sink: BufferedSink)
    val supportsResume: Boolean get() = false
    suspend fun resumeDownload(distribution: ModelDistribution, sink: BufferedSink, offset: Long) {
        require(offset == 0L)
        download(distribution, sink)
    }
}

internal data class VerifiedModel(val spec: AnalysisModelSpec, val path: Path)
internal enum class ModelInstallStage { DOWNLOADING, IMPORTING, VERIFYING }
internal data class ModelInstallProgress(val stage: ModelInstallStage, val bytes: Long, val totalBytes: Long)

/** One host-owned store shared by both editors. Creating it performs no IO or runtime initialization.
 * Only download() may open HTTP; verified() and import() are strictly local. */
internal class ModelStore(
    private val gate: MediaFeatureGate,
    directory: () -> Path,
    downloader: () -> ModelDownloader,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    dispatcher: CoroutineDispatcher = AppDispatchers.io,
    models: List<AnalysisModelSpec> = AnalysisModels.all,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) : AutoCloseable {
    private val catalog = models.associateBy { it.id }.also { require(it.size == models.size) }
    private val directory = lazy { directory().also { fileSystem.createDirectories(it) } }
    private val downloader = lazy(downloader)
    private val life = SupervisorJob()
    private val scope = CoroutineScope(life + dispatcher)
    private val mutex = Mutex()
    private val locks = catalog.keys.associateWith { Mutex() }
    private val progress = catalog.keys.associateWith { MutableStateFlow<ModelInstallProgress?>(null) }
    private class Flight(val task: Deferred<VerifiedModel>, var readers: Int = 0)
    private val flights = mutableMapOf<AnalysisModel, Flight>()

    init {
        life.invokeOnCompletion {
            if (this.downloader.isInitialized()) (this.downloader.value as? AutoCloseable)?.close()
        }
    }

    fun progress(model: AnalysisModel): StateFlow<ModelInstallProgress?> = progress.getValue(model).asStateFlow()

    /** Call only after an explicit model-download action; enabled settings alone are not consent. */
    suspend fun download(model: AnalysisModel, permit: MediaFeaturePermit): VerifiedModel = allowed(permit) {
        val flight = mutex.withLock {
            check(life.isActive) { "モデル管理は終了しました" }
            flights.getOrPut(model) {
                Flight(scope.async(start = CoroutineStart.LAZY) {
                    locks.getValue(model).withLock {
                        val spec = catalog.getValue(model)
                        // The resumable partial is named after the distribution. A recreated store
                        // (another host) must not append to it while this process still writes it.
                        withDownloadLock(modelDirectory(), spec) {
                            verifiedOnDisk(spec) ?: install(spec, importing = false, openSource = null)
                        }
                    }
                })
            }.also { it.readers++ }
        }
        try { flight.task.start(); flight.task.await() }
        finally {
            withContext(NonCancellable) {
                val last = mutex.withLock {
                    flight.readers--
                    if (flight.readers == 0) {
                        if (flights[model] === flight) flights.remove(model)
                        flight.task.cancel()
                        true
                    } else false
                }
                // Last subscriber waits for the response/source and temporary files to close.
                if (last) flight.task.join()
            }
        }
    }

    /**
     * Whether a model file of the right size is installed, without hashing it.
     * For listings only: sessions still use [verified], so a same-size corrupt
     * file is never executed.
     */
    suspend fun present(model: AnalysisModel, permit: MediaFeaturePermit): Boolean = allowed(permit) {
        owned {
            locks.getValue(model).withLock {
                val spec = catalog.getValue(model)
                val metadata = fileSystem.metadataOrNull(modelDirectory().resolve("${spec.sha256}.onnx"))
                metadata != null && metadata.isRegularFile && metadata.symlinkTarget == null && metadata.size == spec.bytes
            }
        }
    }

    /** Rehash on every session acquisition. A same-size corrupted file is never executable. */
    suspend fun verified(model: AnalysisModel, permit: MediaFeaturePermit): VerifiedModel? = allowed(permit) {
        owned { locks.getValue(model).withLock { verifiedOnDisk(catalog.getValue(model)) } }
    }

    /** The selected ONNX source is opened once only after checking the editor's permit. */
    suspend fun import(model: AnalysisModel, permit: MediaFeaturePermit, openSource: suspend () -> Source): VerifiedModel = allowed(permit) {
        owned { locks.getValue(model).withLock { install(catalog.getValue(model), importing = true, openSource) } }
    }

    private suspend fun <T> owned(block: suspend () -> T): T {
        val task = scope.async { block() }
        try { return task.await() }
        finally { withContext(NonCancellable) { task.cancelAndJoin() } }
    }

    private suspend fun <T> allowed(permit: MediaFeaturePermit, block: suspend () -> T): T {
        require(permit.feature == MediaFeature.IMAGE_EDITOR || permit.feature == MediaFeature.VIDEO_EDITOR)
        fun checkPermit() {
            if (!life.isActive || !gate.isCurrent(permit)) throw CancellationException("編集機能は無効になりました")
        }
        checkPermit()
        return coroutineScope {
            val operation = this
            val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
                gate.permits(permit.feature).first { !gate.isCurrent(permit) }
                operation.cancel("編集機能は無効になりました")
            }
            try { block().also { currentCoroutineContext().ensureActive(); checkPermit() } }
            finally { watcher.cancel() }
        }
    }

    private suspend fun verifiedOnDisk(spec: AnalysisModelSpec): VerifiedModel? {
        val path = modelDirectory().resolve("${spec.sha256}.onnx")
        return if (matches(path, spec.bytes, spec.sha256)) VerifiedModel(spec, path) else null
    }

    private suspend fun modelDirectory(): Path = cleanupMutex.withLock {
        directory.value.also { root ->
            if (root !in preparedDirectories) {
                val modelNames = AnalysisModel.entries.joinToString("|") { it.name }
                val partial = Regex("($modelNames)-[0-9a-z]+(\\.extracted)?\\.part")
                // Resumable downloads survive only for a current distribution and for a limited time;
                // partials of replaced model versions or long-abandoned downloads are never resumed.
                val download = Regex("download-([0-9a-f]{64})\\.part")
                val current = (catalog.values + AnalysisModels.all).mapTo(mutableSetOf()) { it.distribution.sha256 }
                val expiresBefore = now() - RESUMABLE_PARTIAL_MAX_AGE_MILLIS
                fileSystem.list(root).forEach { path ->
                    val resumable = download.matchEntire(path.name)
                    if (resumable == null && !partial.matches(path.name)) return@forEach
                    val metadata = fileSystem.metadata(path)
                    if (!metadata.isRegularFile || metadata.symlinkTarget != null) return@forEach
                    val stale = resumable == null || resumable.groupValues[1] !in current ||
                        (metadata.lastModifiedAtMillis ?: Long.MAX_VALUE) < expiresBefore
                    if (stale) fileSystem.delete(path)
                }
                preparedDirectories.add(root)
            }
        }
    }

    private suspend fun matches(path: Path, bytes: Long, sha: String): Boolean {
        currentCoroutineContext().ensureActive()
        val metadata = fileSystem.metadataOrNull(path) ?: return false
        if (!metadata.isRegularFile || metadata.symlinkTarget != null || metadata.size != bytes) return false
        val digest = HashingSource.sha256(fileSystem.source(path))
        digest.use {
            val buffer = Buffer()
            var read = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = it.read(buffer, COPY_BYTES)
                if (count == -1L) break
                read += count; buffer.clear()
                if (read > bytes) return false
            }
            return read == bytes && digest.hash.hex() == sha
        }
    }

    private suspend fun install(spec: AnalysisModelSpec, importing: Boolean, openSource: (suspend () -> Source)?): VerifiedModel {
        val token = Random.nextLong().toULong().toString(36)
        val root = modelDirectory()
        val resumable = !importing && downloader.value.supportsResume
        val temporary = if (resumable) root.resolve("download-${spec.distribution.sha256}.part")
            else root.resolve("${spec.id.name}-$token.part")
        var keepPartial = resumable
        val extracted = root.resolve("${spec.id.name}-$token.extracted.part")
        val state = progress.getValue(spec.id)
        val expected = if (importing) spec.bytes else spec.distribution.bytes
        try {
            val existing = if (resumable) fileSystem.metadataOrNull(temporary) else null
            val offset = existing?.takeIf { it.isRegularFile && it.symlinkTarget == null && it.size in 0L..expected }?.size ?: 0L
            suspend fun transfer(start: Long) {
                state.value = ModelInstallProgress(if (importing) ModelInstallStage.IMPORTING else ModelInstallStage.DOWNLOADING, start, expected)
                val rawSink = if (start > 0L) fileSystem.appendingSink(temporary) else fileSystem.sink(temporary)
                val bounded = object : Sink by rawSink {
                    var count = start
                    override fun write(source: Buffer, byteCount: Long) {
                        if (byteCount > expected - count) throw IOException("モデルのサイズが一致しません")
                        rawSink.write(source, byteCount)
                        count += byteCount
                        state.value = state.value?.copy(bytes = count)
                    }
                }
                bounded.buffer().use { sink ->
                    if (importing) requireNotNull(openSource).invoke().use { copy(it, sink, expected) }
                    else if (resumable) downloader.value.resumeDownload(spec.distribution, sink, start)
                    else downloader.value.download(spec.distribution, sink)
                }
            }
            if (offset < expected) {
                try { transfer(offset) }
                catch (_: ModelResumeUnavailableException) {
                    fileSystem.delete(temporary, mustExist = false)
                    transfer(0L)
                }
            }
            // Only interrupted transfers survive. Verification/import failures never do.
            keepPartial = false
            state.value = ModelInstallProgress(ModelInstallStage.VERIFYING, expected, expected)
            val payload = if (!importing && spec.distribution.zipEntry != null) {
                check(matches(temporary, spec.distribution.bytes, spec.distribution.sha256)) { "モデル配布ファイルの検証に失敗しました" }
                // Never extract arbitrary paths. Inspect only the pinned entry of the verified archive.
                fileSystem.openZip(temporary).use { zip ->
                    zip.source(spec.distribution.zipEntry.toPath()).use { source ->
                        fileSystem.sink(extracted, mustCreate = true).buffer().use { copy(source, it, spec.bytes) }
                    }
                }
                extracted
            } else temporary
            check(matches(payload, spec.bytes, spec.sha256)) { "モデルの内容が一致しません。指定されたONNXファイルを選択してください" }
            currentCoroutineContext().ensureActive()
            val target = root.resolve("${spec.sha256}.onnx")
            fileSystem.atomicMove(payload, target)
            return VerifiedModel(spec, target)
        } finally {
            // Synchronous file deletion also runs during cancellation; verified files remain installed.
            try { if (!keepPartial) fileSystem.delete(temporary, mustExist = false) }
            finally { try { fileSystem.delete(extracted, mustExist = false) } finally { state.value = null } }
        }
    }

    private suspend fun copy(source: Source, sink: BufferedSink, limit: Long) {
        val buffer = Buffer()
        var copied = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = source.read(buffer, COPY_BYTES)
            if (count == -1L) break
            if (count <= 0 || count > limit - copied) throw IOException("モデルのサイズが一致しません")
            sink.write(buffer, count); copied += count
        }
    }

    override fun close() { life.cancel() }
    suspend fun closeAndAwait() { close(); life.join() }
    private companion object {
        const val COPY_BYTES = 64L * 1024
        const val RESUMABLE_PARTIAL_MAX_AGE_MILLIS = 7L * 24 * 60 * 60 * 1000
        val cleanupMutex = Mutex()
        val preparedDirectories = mutableSetOf<Path>()
        /** Process-wide, per installed file: per-instance [locks] do not cover other stores. */
        val downloadLocks = mutableMapOf<Path, Mutex>()

        suspend fun <T> withDownloadLock(root: Path, spec: AnalysisModelSpec, block: suspend () -> T): T {
            val lock = cleanupMutex.withLock { downloadLocks.getOrPut(root.resolve(spec.distribution.sha256)) { Mutex() } }
            return lock.withLock { block() }
        }
    }
}
