package com.valoser.futacha.shared.util

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.service.AUTO_SAVE_DIRECTORY
import com.valoser.futacha.shared.service.MANUAL_SAVE_DIRECTORY
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Exception thrown when DocumentFile permissions have been revoked
 * FIX: ユーザーに適切なエラーメッセージを提供するための専用例外クラス
 */
class PermissionRevokedException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Android版FileSystem実装
 */
class AndroidFileSystem(
    private val context: Context
) : FileSystem {

    // FIX: 安全性チェックのための定数
    companion object {
        private const val ZERO_READ_BACKOFF_MILLIS = 25L
        private const val SAF_READ_IDLE_TIMEOUT_MILLIS = 15_000L
    }

    private inline fun <T> runFsCatching(block: () -> T): Result<T> = com.valoser.futacha.shared.util.runFsCatching(block)

    private fun validatePath(path: String, paramName: String = "path") = validateFileSystemPath(path, paramName)

    private fun validateFileSize(size: Long, paramName: String = "file") = validateFileSystemSize(size, paramName)

    private suspend fun backoffAfterZeroRead() {
        coroutineContext.ensureActive()
        delay(ZERO_READ_BACKOFF_MILLIS)
    }

    override suspend fun createDirectory(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        runFsCatching {
            validatePath(path, "path") // FIX: 入力検証
            val dir = File(resolveAbsolutePath(path))
            if (!dir.exists()) {
                if (!dir.mkdirs() && !dir.exists()) {
                    throw IllegalStateException("Failed to create directory: ${dir.absolutePath}")
                }
            }
        }
    }

    override suspend fun writeBytes(path: String, bytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        runFsCatching {
            validatePath(path, "path") // FIX: 入力検証
            validateFileSize(bytes.size.toLong(), "bytes") // FIX: サイズ検証
            val file = File(resolveAbsolutePath(path))
            file.parentFile?.let { parent ->
                if (!parent.exists()) {
                    if (!parent.mkdirs() && !parent.exists()) {
                        throw IllegalStateException("Failed to create parent directory: ${parent.absolutePath}")
                    }
                }
            }

            // FIX: アトミック書き込み - 一時ファイルに書き込んでからrenameすることで、
            // 書き込み中のクラッシュでファイルが破損することを防ぐ
            val tmpFile = File.createTempFile("tmp_", ".tmp", file.parentFile)
            try {
                tmpFile.writeBytes(bytes)
                if (!tmpFile.renameTo(file)) {
                    // renameTo can fail if target exists on some file systems, try delete + rename
                    Logger.d("AndroidFileSystem", "First renameTo failed, trying delete + rename for ${file.name}")
                    if (file.exists() && !file.delete()) {
                         throw IOException("Failed to delete existing file for atomic write: ${file.absolutePath}")
                    }
                    if (!tmpFile.renameTo(file)) {
                        throw IOException("Failed to rename temp file (${tmpFile.absolutePath}) to target (${file.absolutePath})")
                    }
                }
            } catch (e: Exception) {
                // FIX: エラー発生時は一時ファイルを即座にクリーンアップ
                if (tmpFile.exists()) {
                    tmpFile.delete()
                }
                throw e
            } finally {
                // FIX: 念のため再度チェック（renameが成功していれば存在しないはず）
                if (tmpFile.exists()) {
                    val deleted = tmpFile.delete()
                    if (!deleted) {
                        Logger.w("AndroidFileSystem", "Failed to delete temp file: ${tmpFile.absolutePath}. It will be cleaned up by periodic cleanup.")
                    }
                }
            }
        }
    }

    override suspend fun appendBytes(path: String, bytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        runFsCatching {
            validatePath(path, "path") // FIX: 入力検証
            validateFileSize(bytes.size.toLong(), "bytes") // FIX: サイズ検証
            val file = File(resolveAbsolutePath(path))
            file.parentFile?.let { parent ->
                if (!parent.exists()) {
                    if (!parent.mkdirs() && !parent.exists()) {
                        throw IllegalStateException("Failed to create parent directory: ${parent.absolutePath}")
                    }
                }
            }
            file.appendBytes(bytes)
        }
    }

    override suspend fun writeString(path: String, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        // FIX: 入力検証はwriteBytesで実行される
        // writeBytes uses atomic write, so we delegate to it
        writeBytes(path, content.toByteArray(Charsets.UTF_8))
    }

    override suspend fun readBytes(path: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        runFsCatching {
            validatePath(path, "path") // FIX: 入力検証
            val file = File(resolveAbsolutePath(path))
            val size = file.length()
            validateFileSize(size, "file") // FIX: 読み込み前にサイズチェック
            file.readBytes()
        }
    }

    override suspend fun readString(path: String): Result<String> = withContext(Dispatchers.IO) {
        runFsCatching {
            validatePath(path, "path") // FIX: 入力検証
            val file = File(resolveAbsolutePath(path))
            val size = file.length()
            validateFileSize(size, "file") // FIX: 読み込み前にサイズチェック
            file.readText(Charsets.UTF_8)
        }
    }

    override suspend fun delete(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        runFsCatching {
            validatePath(path, "path") // FIX: 入力検証
            val file = File(resolveAbsolutePath(path))
            if (!file.exists()) {
                return@runFsCatching Unit
            }
            if (!file.delete() && file.exists()) {
                throw IllegalStateException("Failed to delete: ${file.absolutePath}")
            }
            Unit
        }
    }

    override suspend fun deleteRecursively(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        runFsCatching {
            validatePath(path, "path") // FIX: 入力検証
            val file = File(resolveAbsolutePath(path))
            if (!file.exists()) {
                return@runFsCatching Unit
            }
            if (!file.deleteRecursively() && file.exists()) {
                throw IllegalStateException("Failed to delete recursively: ${file.absolutePath}")
            }
            Unit
        }
    }

    override suspend fun exists(path: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(resolveAbsolutePath(path))
        file.exists()
    }

    override suspend fun getFileSize(path: String): Long = withContext(Dispatchers.IO) {
        val file = File(resolveAbsolutePath(path))
        if (file.exists()) file.length() else 0L
    }

    override suspend fun listFiles(directory: String): List<String> = withContext(Dispatchers.IO) {
        val dir = File(resolveAbsolutePath(directory))
        dir.listFiles()?.map { it.name } ?: emptyList()
    }

    override fun getAppDataDirectory(): String {
        // Modern Android uses app-scoped external storage by default to avoid scoped-storage write failures.
        return if (isExternalStorageWritable()) {
            getPublicDocumentsDirectory()
        } else {
            // フォールバック: 内部ストレージ
            context.filesDir.absolutePath
        }
    }

    override fun resolveAbsolutePath(relativePath: String): String {
        return resolveAndroidAbsolutePath(
            relativePath = relativePath,
            appDataDirectory = getAppDataDirectory(),
            privateAppDataDirectory = getPrivateAppDataDirectory(),
            publicDocumentsDirectory = getPublicDocumentsDirectory(),
            publicDownloadsDirectory = getPublicDownloadsDirectory()
        )
    }

    /**
     * Documents 相当の app-scoped 外部ディレクトリを取得。
     * 共有ストレージへ直接書き込むと scoped storage で失敗しやすいため、
     * デフォルト経路は SAF 未選択でも安定して書ける app-scoped 領域を使う。
     */
    private fun getPublicDocumentsDirectory(): String {
        return getPublicAppDirectory(
            directoryType = Environment.DIRECTORY_DOCUMENTS,
            directoryLabel = "documents"
        )
    }

    /**
     * Downloads 相当の app-scoped 外部ディレクトリを取得。
     */
    private fun getPublicDownloadsDirectory(): String {
        return getPublicAppDirectory(
            directoryType = Environment.DIRECTORY_DOWNLOADS,
            directoryLabel = "downloads"
        )
    }

    private fun getPublicAppDirectory(
        directoryType: String,
        directoryLabel: String
    ): String {
        try {
            val externalDir = context.getExternalFilesDir(directoryType)
            if (externalDir == null) {
                Logger.e(
                    "FileSystem.android",
                    "App-scoped external $directoryLabel directory is null, falling back to internal storage"
                )
                return getFallbackAppDirectory()
            }
            return ensureAppDirectory(externalDir).absolutePath
        } catch (e: SecurityException) {
            Logger.e("FileSystem.android", "SecurityException accessing external storage", e)
            return getFallbackAppDirectory(e, "SecurityException")
        } catch (e: Exception) {
            Logger.e("FileSystem.android", "Unexpected error accessing storage", e)
            return getFallbackAppDirectory(e, "unexpected error")
        }
    }

    /**
     * FilesDir 配下のアプリ専用ディレクトリを取得 (ファイラーからは見えない)
     */
    private fun getPrivateAppDataDirectory(): String {
        return ensureAppDirectory(context.filesDir).absolutePath
    }

    /**
     * 外部ストレージが書き込み可能か確認
     */
    private fun isExternalStorageWritable(): Boolean {
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }

    private fun resolveSaveLocationPath(basePath: String, relativePath: String = ""): String {
        val resolvedBase = resolveAbsolutePath(basePath)
        return if (relativePath.isEmpty()) {
            resolvedBase
        } else {
            File(resolvedBase, relativePath).absolutePath
        }
    }

    private fun ensureAppDirectory(baseDirectory: File): File {
        val appDir = File(baseDirectory, "futacha")
        if (!appDir.exists()) {
            val created = appDir.mkdirs()
            if (!created && !appDir.exists()) {
                Logger.e("FileSystem.android", "Failed to create app directory at ${appDir.absolutePath}")
                throw IllegalStateException("Failed to create app directory at ${appDir.absolutePath}")
            }
        }
        return appDir
    }

    private fun getFallbackAppDirectory(
        cause: Throwable? = null,
        reason: String? = null
    ): String {
        if (reason != null) {
            Logger.w("FileSystem.android", "Using internal storage fallback after $reason")
        }
        return try {
            ensureAppDirectory(context.filesDir).absolutePath
        } catch (fallbackError: Exception) {
            throw IllegalStateException(
                "Failed to create fallback app directory",
                cause ?: fallbackError
            )
        }
    }

    private fun requireTreeBaseDirectory(
        base: SaveLocation.TreeUri,
        requireWrite: Boolean = false
    ): DocumentFile {
        val treeUri = Uri.parse(base.uri)
        val baseDir = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw PermissionRevokedException(
                "Cannot resolve tree URI: ${base.uri}. Please select the folder again."
            )
        if (requireWrite && !baseDir.canWrite()) {
            throw PermissionRevokedException(
                "Write permission lost for tree URI: ${base.uri}. Please select the folder again."
            )
        }
        return baseDir
    }

    private suspend inline fun <T> runTreeUriCatching(
        base: SaveLocation.TreeUri,
        requireWrite: Boolean = false,
        block: suspend (DocumentFile) -> T
    ): Result<T> {
        return try {
            Result.success(block(requireTreeBaseDirectory(base, requireWrite)))
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private fun resolveTreeParentDirectory(
        baseDir: DocumentFile,
        relativePath: String,
        createDirectories: Boolean
    ): Pair<DocumentFile, String> {
        val (parentPath, fileName) = splitParentAndFileName(relativePath)
        val parentDir = when {
            parentPath.isEmpty() -> baseDir
            createDirectories -> createOrNavigateToDirectory(baseDir, parentPath)
            else -> navigateToDirectory(baseDir, parentPath)
                ?: throw IllegalStateException("Directory not found: $parentPath")
        }
        return parentDir to fileName
    }

    private fun findOrCreateTreeFile(
        parentDir: DocumentFile,
        fileName: String
    ): DocumentFile {
        return parentDir.findFile(fileName)?.takeIf { !it.isDirectory }
            ?: parentDir.createFile("application/octet-stream", fileName)
            ?: throw IllegalStateException("Failed to create file: $fileName")
    }

    private suspend fun readTreeFileUtf8(file: DocumentFile, fileName: String): String {
        return context.contentResolver.openInputStream(file.uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var totalRead = 0L
            var zeroReadCount = 0
            while (true) {
                coroutineContext.ensureActive()
                val read = withTimeoutOrNull(SAF_READ_IDLE_TIMEOUT_MILLIS) {
                    runInterruptible {
                        input.read(buffer)
                    }
                } ?: throw IllegalStateException("Read timed out while loading file: $fileName")
                when {
                    read < 0 -> break
                    read == 0 -> {
                        zeroReadCount += 1
                        if (zeroReadCount >= 100) {
                            throw IllegalStateException("Read stalled while loading file: $fileName")
                        }
                        backoffAfterZeroRead()
                        continue
                    }
                    else -> {
                        zeroReadCount = 0
                    }
                }
                totalRead += read
                validateFileSize(totalRead, "file")
                output.write(buffer, 0, read)
            }
            output.toString(Charsets.UTF_8.name())
        } ?: throw IllegalStateException("Failed to open input stream for ${file.uri}")
    }

    // ========================================
    // SaveLocation-based implementations
    // ========================================

    override suspend fun createDirectory(base: SaveLocation, relativePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        // FIX: 入力検証 - 空文字列の場合はベースディレクトリなので検証不要
        if (relativePath.isNotEmpty()) {
            runFsCatching { validatePath(relativePath, "relativePath") }.getOrElse {
                return@withContext Result.failure(it)
            }
        }
        when (base) {
            is SaveLocation.Path -> {
                val fullPath = resolveSaveLocationPath(base.path, relativePath)
                createDirectory(fullPath)
            }
            is SaveLocation.TreeUri -> {
                runTreeUriCatching(base, requireWrite = true) { baseDir ->
                    if (relativePath.isEmpty()) {
                        return@runTreeUriCatching Unit
                    }
                    createOrNavigateToDirectory(baseDir, relativePath)
                    Unit
                }
            }
            is SaveLocation.Bookmark -> {
                Result.failure(UnsupportedOperationException("Bookmark SaveLocation is not supported on Android"))
            }
        }
    }

    override suspend fun writeBytes(base: SaveLocation, relativePath: String, bytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        // FIX: 入力検証
        runFsCatching {
            validatePath(relativePath, "relativePath")
            validateFileSize(bytes.size.toLong(), "bytes")
        }.getOrElse {
            return@withContext Result.failure(it)
        }
        when (base) {
            is SaveLocation.Path -> {
                val fullPath = resolveSaveLocationPath(base.path, relativePath)
                writeBytes(fullPath, bytes)
            }
            is SaveLocation.TreeUri -> {
                runTreeUriCatching(base, requireWrite = true) { baseDir ->
                    val (parentDir, fileName) = resolveTreeParentDirectory(
                        baseDir = baseDir,
                        relativePath = relativePath,
                        createDirectories = true
                    )
                    val file = findOrCreateTreeFile(parentDir, fileName)
                    context.contentResolver.openOutputStream(file.uri, "wt")?.use { output ->
                        output.write(bytes)
                    } ?: throw IllegalStateException("Failed to open output stream for ${file.uri}")
                }
            }
            is SaveLocation.Bookmark -> {
                Result.failure(UnsupportedOperationException("Bookmark SaveLocation is not supported on Android"))
            }
        }
    }

    override suspend fun appendBytes(base: SaveLocation, relativePath: String, bytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        // FIX: 入力検証
        runFsCatching {
            validatePath(relativePath, "relativePath")
            validateFileSize(bytes.size.toLong(), "bytes")
        }.getOrElse {
            return@withContext Result.failure(it)
        }
        when (base) {
            is SaveLocation.Path -> {
                val fullPath = resolveSaveLocationPath(base.path, relativePath)
                appendBytes(fullPath, bytes)
            }
            is SaveLocation.TreeUri -> {
                runTreeUriCatching(base, requireWrite = true) { baseDir ->
                    val (parentDir, fileName) = resolveTreeParentDirectory(
                        baseDir = baseDir,
                        relativePath = relativePath,
                        createDirectories = true
                    )
                    val file = findOrCreateTreeFile(parentDir, fileName)
                    context.contentResolver.openOutputStream(file.uri, "wa")?.use { output ->
                        output.write(bytes)
                    } ?: throw IllegalStateException("Failed to open output stream for ${file.uri}")
                }
            }
            is SaveLocation.Bookmark -> {
                Result.failure(UnsupportedOperationException("Bookmark SaveLocation is not supported on Android"))
            }
        }
    }

    override suspend fun writeString(base: SaveLocation, relativePath: String, content: String): Result<Unit> {
        // FIX: 入力検証はwriteBytesで実行される
        return writeBytes(base, relativePath, content.toByteArray(Charsets.UTF_8))
    }

    override suspend fun readString(base: SaveLocation, relativePath: String): Result<String> = withContext(Dispatchers.IO) {
        // FIX: 入力検証
        runFsCatching { validatePath(relativePath, "relativePath") }.getOrElse {
            return@withContext Result.failure(it)
        }
        when (base) {
            is SaveLocation.Path -> {
                val fullPath = resolveSaveLocationPath(base.path, relativePath)
                readString(fullPath)
            }
            is SaveLocation.TreeUri -> {
                runTreeUriCatching(base) { baseDir ->
                    val (parentDir, fileName) = resolveTreeParentDirectory(
                        baseDir = baseDir,
                        relativePath = relativePath,
                        createDirectories = false
                    )
                    val file = parentDir.findFile(fileName)
                        ?: throw IllegalStateException("File not found: $fileName")
                    readTreeFileUtf8(file, fileName)
                }
            }
            is SaveLocation.Bookmark -> {
                Result.failure(UnsupportedOperationException("Bookmark SaveLocation is not supported on Android"))
            }
        }
    }

    override suspend fun exists(base: SaveLocation, relativePath: String): Boolean = withContext(Dispatchers.IO) {
        when (base) {
            is SaveLocation.Path -> {
                val fullPath = resolveSaveLocationPath(base.path, relativePath)
                exists(fullPath)
            }
            is SaveLocation.TreeUri -> {
                try {
                    val baseDir = requireTreeBaseDirectory(base)

                    if (relativePath.isEmpty()) {
                        return@withContext baseDir.exists()
                    }

                    val (parentDir, fileName) = resolveTreeParentDirectory(
                        baseDir = baseDir,
                        relativePath = relativePath,
                        createDirectories = false
                    )
                    parentDir.findFile(fileName)?.exists() ?: false
                } catch (e: Exception) {
                    Logger.e("AndroidFileSystem", "Error checking existence for TreeUri: ${base.uri}, path: $relativePath", e)
                    false
                }
            }
            is SaveLocation.Bookmark -> {
                false
            }
        }
    }

    override suspend fun delete(base: SaveLocation, relativePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        // FIX: 入力検証 - 空文字列の場合はベースを削除するので検証不要
        if (relativePath.isNotEmpty()) {
            runFsCatching { validatePath(relativePath, "relativePath") }.getOrElse {
                return@withContext Result.failure(it)
            }
        }
        when (base) {
            is SaveLocation.Path -> {
                val fullPath = resolveSaveLocationPath(base.path, relativePath)
                deleteRecursively(fullPath)
            }
            is SaveLocation.TreeUri -> {
                runTreeUriCatching(base, requireWrite = true) { baseDir ->
                    if (relativePath.isEmpty()) {
                        // Safety guard: never delete the user-selected TreeUri root from app code.
                        Logger.w(
                            "AndroidFileSystem",
                            "Refusing to delete TreeUri base directory directly: ${base.uri}"
                        )
                        return@runTreeUriCatching Unit
                    }

                    val (parentDir, fileName) = resolveTreeParentDirectory(
                        baseDir = baseDir,
                        relativePath = relativePath,
                        createDirectories = false
                    )
                    val target = parentDir.findFile(fileName) ?: return@runTreeUriCatching Unit

                    if (!deleteDocumentRecursively(target)) {
                        throw IllegalStateException("Failed to delete: $relativePath")
                    }
                }
            }
            is SaveLocation.Bookmark -> {
                Result.failure(UnsupportedOperationException("Bookmark SaveLocation is not supported on Android"))
            }
        }
    }

    // ========================================
    // Helper methods for DocumentFile navigation
    // ========================================

    private fun splitParentAndFileName(relativePath: String): Pair<String, String> =
        com.valoser.futacha.shared.util.splitParentAndFileName(relativePath)

    // FIX: DocumentFileナビゲーションのエラーハンドリングを強化
    private fun navigateToDirectory(base: DocumentFile, relativePath: String): DocumentFile? {
        if (relativePath.isEmpty()) return base
        val segments = relativePath.split('/').filter { it.isNotBlank() }
        var current = base
        for ((index, segment) in segments.withIndex()) {
            val next = current.findFile(segment)
            if (next == null) {
                Logger.d("AndroidFileSystem", "Directory not found at segment[$index]: $segment (path: $relativePath)")
                return null
            }
            if (!next.isDirectory) {
                Logger.w("AndroidFileSystem", "Path segment is not a directory at segment[$index]: $segment (path: $relativePath)")
                return null
            }
            current = next
        }
        return current
    }

    // FIX: ディレクトリ作成時のエラーメッセージを詳細化
    private fun createOrNavigateToDirectory(base: DocumentFile, relativePath: String): DocumentFile {
        if (relativePath.isEmpty()) return base
        val segments = relativePath.split('/').filter { it.isNotBlank() }
        var current = base
        for ((index, segment) in segments.withIndex()) {
            val existing = current.findFile(segment)
            current = if (existing != null && existing.isDirectory) {
                existing
            } else if (existing == null) {
                current.createDirectory(segment)
                    ?: throw IllegalStateException("Failed to create directory at segment[$index]: $segment (path: $relativePath, base: ${base.uri})")
            } else {
                throw IllegalStateException("Path segment exists but is not a directory at segment[$index]: $segment (path: $relativePath)")
            }
        }
        return current
    }

    private fun deleteDocumentRecursively(target: DocumentFile): Boolean {
        if (target.isDirectory) {
            val children = runCatching { target.listFiles() }.getOrElse { emptyArray() }
            children.forEach { child ->
                if (!deleteDocumentRecursively(child)) {
                    return false
                }
            }
        }
        return target.delete() || !target.exists()
    }

    /**
     * FIX: 古い一時ファイルをクリーンアップ
     * アプリ起動時に呼び出して、前回のクラッシュなどで残った一時ファイルを削除
     *
     * 改善点:
     * - キャッシュディレクトリだけでなく、アプリデータディレクトリも検索
     * - 年齢ベースのクリーンアップ (1時間以上古いファイルのみ削除)
     * - 再帰的に検索して全ての一時ファイルを見つける
     */
    fun cleanupTempFiles(): Result<Int> = runCatching {
        var deletedCount = 0
        val now = System.currentTimeMillis()
        val maxAgeMs = 60 * 60 * 1000L // 1時間

        // FIX: 複数のディレクトリを検索対象に追加
        val searchDirs = buildList {
            add(context.cacheDir)
            add(context.filesDir)
            // 外部ストレージが利用可能な場合のみ追加
            if (isExternalStorageWritable()) {
                runCatching {
                    add(File(getPublicDocumentsDirectory()))
                    add(File(getPublicDownloadsDirectory()))
                }.onFailure { e ->
                    Logger.w("AndroidFileSystem", "Failed to access public directories for cleanup: ${e.message}")
                }
            }
        }

        // FIX: 各ディレクトリを再帰的に検索
        searchDirs.forEach { dir ->
            if (dir.exists() && dir.isDirectory) {
                deletedCount += cleanupTempFilesRecursive(
                    directory = dir,
                    now = now,
                    maxAgeMs = maxAgeMs,
                    currentDepth = 0,
                    maxDepth = 3
                )
            }
        }

        if (deletedCount > 0) {
            Logger.i("AndroidFileSystem", "Cleaned up $deletedCount temp files")
        }
        deletedCount
    }

    /**
     * FIX: ディレクトリを再帰的に検索して古い一時ファイルを削除
     */
    private fun cleanupTempFilesRecursive(
        directory: File,
        now: Long,
        maxAgeMs: Long,
        currentDepth: Int,
        maxDepth: Int
    ): Int {
        var deletedCount = 0

        runCatching {
            directory.listFiles()?.forEach { file ->
                try {
                    if (file.isFile && file.name.startsWith("tmp_") && file.name.endsWith(".tmp")) {
                        // FIX: 年齢チェック - 1時間以上古いファイルのみ削除
                        val age = now - file.lastModified()
                        if (age > maxAgeMs) {
                            if (file.delete()) {
                                deletedCount++
                                Logger.d("AndroidFileSystem", "Deleted old temp file: ${file.absolutePath} (age: ${age / 1000}s)")
                            } else {
                                Logger.w("AndroidFileSystem", "Failed to delete temp file: ${file.absolutePath}")
                            }
                        }
                    } else if (file.isDirectory) {
                        // 再帰的に検索 (最大3レベルまで、無限再帰を防ぐ)
                        if (currentDepth < maxDepth) {
                            deletedCount += cleanupTempFilesRecursive(
                                directory = file,
                                now = now,
                                maxAgeMs = maxAgeMs,
                                currentDepth = currentDepth + 1,
                                maxDepth = maxDepth
                            )
                        }
                    }
                } catch (e: Exception) {
                    Logger.w("AndroidFileSystem", "Error processing file during cleanup: ${file.absolutePath} - ${e.message}")
                }
            }
        }.onFailure { e ->
            Logger.w("AndroidFileSystem", "Error listing files in ${directory.absolutePath}: ${e.message}")
        }

        return deletedCount
    }
}

actual fun createFileSystem(platformContext: Any?): FileSystem {
    require(platformContext is Context) { "Android requires Context" }
    return AndroidFileSystem(platformContext.applicationContext)
}
