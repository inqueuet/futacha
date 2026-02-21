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
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
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
        // FIX: ファイルサイズ上限（100MB） - OOM防止
        private const val MAX_FILE_SIZE = 100 * 1024 * 1024L
        // FIX: ファイル名の最大長（Linuxの制限）
        private const val MAX_FILENAME_LENGTH = 255
        // FIX: パスの最大長（合理的な上限）
        private const val MAX_PATH_LENGTH = 4096
    }

    private inline fun <T> runFsCatching(block: () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /**
     * FIX: 入力検証 - セキュリティとデータ整合性のための検証
     *
     * @throws IllegalArgumentException パスが不正な場合
     */
    private fun validatePath(path: String, paramName: String = "path") {
        // FIX: 空文字列チェック
        if (path.isEmpty()) {
            throw IllegalArgumentException("$paramName must not be empty")
        }

        // FIX: null文字チェック（セキュリティ脆弱性防止）
        if (path.contains('\u0000')) {
            throw IllegalArgumentException("$paramName contains null character")
        }

        // FIX: パス長制限
        if (path.length > MAX_PATH_LENGTH) {
            throw IllegalArgumentException("$paramName exceeds maximum length ($MAX_PATH_LENGTH): ${path.length}")
        }

        // FIX: パストラバーサル攻撃防止
        val normalized = path.replace('\\', '/')
        if (normalized.contains("../") || normalized.contains("/..") || normalized == "..") {
            throw IllegalArgumentException("$paramName contains path traversal sequence: $path")
        }

        // FIX: ファイル名の長さチェック（最後のセグメントのみ）
        val fileName = normalized.substringAfterLast('/', normalized)
        if (fileName.length > MAX_FILENAME_LENGTH) {
            throw IllegalArgumentException("File name exceeds maximum length ($MAX_FILENAME_LENGTH): $fileName")
        }
    }

    /**
     * FIX: ファイルサイズ検証 - OOM防止
     *
     * @throws IllegalArgumentException サイズが上限を超える場合
     */
    private fun validateFileSize(size: Long, paramName: String = "file") {
        if (size > MAX_FILE_SIZE) {
            throw IllegalArgumentException("$paramName size ($size bytes) exceeds maximum allowed ($MAX_FILE_SIZE bytes)")
        }
        // FIX: 負のサイズチェック
        if (size < 0) {
            throw IllegalArgumentException("$paramName size cannot be negative: $size")
        }
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
        // Android 10以降、またはストレージ状態が正常な場合は共有Documentsフォルダを使用
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || isExternalStorageWritable()) {
            getPublicDocumentsDirectory()
        } else {
            // フォールバック: 内部ストレージ
            context.filesDir.absolutePath
        }
    }

    override fun resolveAbsolutePath(relativePath: String): String {
        if (relativePath.startsWith("/")) {
            return relativePath
        }

        val cleanedPath = relativePath.removePrefix("./")
        val lower = cleanedPath.lowercase()

        // 明示的にプライベート領域を使いたい場合のプレフィックス
        if (cleanedPath.startsWith("private/")) {
            val remainder = cleanedPath.removePrefix("private/").ifBlank { "" }
            return File(getPrivateAppDataDirectory(), remainder).absolutePath
        }

        // AUTO_SAVE_DIRECTORY はアプリ専用の非公開領域に保存
        if (cleanedPath.startsWith(AUTO_SAVE_DIRECTORY)) {
            return File(getPrivateAppDataDirectory(), cleanedPath).absolutePath
        }

        // ユーザーが「Download」や「Documents」と入力した場合は futacha/saved_threads を自動付与
        val isDownload = lower == "download" || lower == "downloads"
        val isDownloadSubPath = lower.startsWith("download/") || lower.startsWith("downloads/")
        val isDocuments = lower == "documents"
        val isDocumentsSubPath = lower.startsWith("documents/")

        return when {
            isDownload -> {
                File(getPublicDownloadsDirectory(), MANUAL_SAVE_DIRECTORY).absolutePath
            }

            isDownloadSubPath -> {
                val remainder = cleanedPath.substringAfter('/').removePrefix("futacha/").ifBlank { MANUAL_SAVE_DIRECTORY }
                File(getPublicDownloadsDirectory(), remainder).absolutePath
            }

            isDocuments -> {
                File(getPublicDocumentsDirectory(), MANUAL_SAVE_DIRECTORY).absolutePath
            }

            isDocumentsSubPath -> {
                val remainder = cleanedPath.substringAfter('/').removePrefix("futacha/").ifBlank { MANUAL_SAVE_DIRECTORY }
                File(getPublicDocumentsDirectory(), remainder).absolutePath
            }

            else -> {
                val baseDir = getAppDataDirectory()
                File(baseDir, cleanedPath).absolutePath
            }
        }
    }

    /**
     * 共有Documentsフォルダのパスを取得
     *
     * 警告: Android 10以降、getExternalStoragePublicDirectory()は非推奨で、書き込み制限があります。
     * SAF (Storage Access Framework)の使用を推奨します。
     * このメソッドはフォールバックとしてのみ使用され、失敗時は内部ストレージにフォールバックします。
     */
    private fun getPublicDocumentsDirectory(): String {
        try {
            val documentsDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10以降: 非推奨APIだが、読み取り専用として使用可能
                // 書き込みには権限が必要で、多くのケースで失敗する
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            } else {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            }

            // Null check for rare cases where external storage is not available
            if (documentsDir == null) {
                Logger.e("FileSystem.android", "External storage documents directory is null, falling back to internal storage")
                return context.filesDir.absolutePath
            }

            // アプリ専用のサブフォルダを作成
            val appDir = File(documentsDir, "futacha")
            if (!appDir.exists()) {
                val success = appDir.mkdirs()
                if (!success && !appDir.exists()) {
                    // mkdirs failed and directory still doesn't exist
                    Logger.e("FileSystem.android", "Failed to create app directory at ${appDir.absolutePath}, falling back to internal storage")
                    return File(context.filesDir, "futacha").apply {
                        if (!exists()) {
                            val fallbackSuccess = mkdirs()
                            if (!fallbackSuccess && !exists()) {
                                throw IllegalStateException("Failed to create fallback directory")
                            }
                        }
                    }.absolutePath
                }
            }

            return appDir.absolutePath
        } catch (e: SecurityException) {
            Logger.e("FileSystem.android", "SecurityException accessing external storage", e)
            // Fallback to internal storage
            return File(context.filesDir, "futacha").apply {
                if (!exists()) {
                    val fallbackSuccess = mkdirs()
                    if (!fallbackSuccess && !exists()) {
                        throw IllegalStateException("Failed to create fallback directory after SecurityException", e)
                    }
                }
            }.absolutePath
        } catch (e: Exception) {
            Logger.e("FileSystem.android", "Unexpected error accessing storage", e)
            // Fallback to internal storage
            return File(context.filesDir, "futacha").apply {
                if (!exists()) {
                    val fallbackSuccess = mkdirs()
                    if (!fallbackSuccess && !exists()) {
                        throw IllegalStateException("Failed to create fallback directory after unexpected error", e)
                    }
                }
            }.absolutePath
        }
    }

    /**
     * 共有Downloadフォルダのパスを取得
     *
     * 警告: Android 10以降、getExternalStoragePublicDirectory()は非推奨で、書き込み制限があります。
     * SAF (Storage Access Framework)の使用を推奨します。
     * このメソッドはフォールバックとしてのみ使用され、失敗時は内部ストレージにフォールバックします。
     */
    private fun getPublicDownloadsDirectory(): String {
        try {
            val downloadsDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10以降: 非推奨APIだが、読み取り専用として使用可能
                // 書き込みには権限が必要で、多くのケースで失敗する
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            } else {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            }

            if (downloadsDir == null) {
                Logger.e("FileSystem.android", "External storage downloads directory is null, falling back to internal storage")
                return context.filesDir.absolutePath
            }

            val appDir = File(downloadsDir, "futacha")
            if (!appDir.exists()) {
                val success = appDir.mkdirs()
                if (!success && !appDir.exists()) {
                    Logger.e("FileSystem.android", "Failed to create app directory at ${appDir.absolutePath}, falling back to internal storage")
                    return File(context.filesDir, "futacha").apply {
                        if (!exists()) {
                            val fallbackSuccess = mkdirs()
                            if (!fallbackSuccess && !exists()) {
                                throw IllegalStateException("Failed to create fallback directory")
                            }
                        }
                    }.absolutePath
                }
            }

            return appDir.absolutePath
        } catch (e: SecurityException) {
            Logger.e("FileSystem.android", "SecurityException accessing external storage", e)
            return File(context.filesDir, "futacha").apply {
                if (!exists()) {
                    val fallbackSuccess = mkdirs()
                    if (!fallbackSuccess && !exists()) {
                        throw IllegalStateException("Failed to create fallback directory after SecurityException", e)
                    }
                }
            }.absolutePath
        } catch (e: Exception) {
            Logger.e("FileSystem.android", "Unexpected error accessing storage", e)
            return File(context.filesDir, "futacha").apply {
                if (!exists()) {
                    val fallbackSuccess = mkdirs()
                    if (!fallbackSuccess && !exists()) {
                        throw IllegalStateException("Failed to create fallback directory after unexpected error", e)
                    }
                }
            }.absolutePath
        }
    }

    /**
     * FilesDir 配下のアプリ専用ディレクトリを取得 (ファイラーからは見えない)
     */
    private fun getPrivateAppDataDirectory(): String {
        val appDir = File(context.filesDir, "futacha")
        if (!appDir.exists()) {
            val created = appDir.mkdirs()
            if (!created && !appDir.exists()) {
                Logger.e("FileSystem.android", "Failed to create private app directory at ${appDir.absolutePath}")
                throw IllegalStateException("Failed to create private app directory at ${appDir.absolutePath}")
            }
        }
        return appDir.absolutePath
    }

    /**
     * 外部ストレージが書き込み可能か確認
     */
    private fun isExternalStorageWritable(): Boolean {
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
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
                val fullPath = if (relativePath.isEmpty()) base.path else File(base.path, relativePath).absolutePath
                createDirectory(fullPath)
            }
            is SaveLocation.TreeUri -> {
                runFsCatching {
                    val treeUri = Uri.parse(base.uri)
                    val baseDir = DocumentFile.fromTreeUri(context, treeUri)
                        ?: throw IllegalStateException("Cannot resolve tree URI: ${base.uri}. Permission may have been revoked.")

                    // Verify we still have permission
                    if (!baseDir.canWrite()) {
                        throw PermissionRevokedException("Write permission lost for tree URI: ${base.uri}. Please select the folder again.")
                    }

                    if (relativePath.isEmpty()) {
                        // Base directory already exists if we can resolve it
                        return@runFsCatching Unit
                    }

                    val segments = relativePath.split('/').filter { it.isNotBlank() }
                    var current = baseDir
                    for (segment in segments) {
                        val existing = current.findFile(segment)
                        current = if (existing != null && existing.isDirectory) {
                            existing
                        } else if (existing == null) {
                            current.createDirectory(segment)
                                ?: throw IllegalStateException("Failed to create directory: $segment in ${current.uri}")
                        } else {
                            throw IllegalStateException("Path segment exists but is not a directory: $segment")
                        }
                    }
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
                val fullPath = File(base.path, relativePath).absolutePath
                writeBytes(fullPath, bytes)
            }
            is SaveLocation.TreeUri -> {
                runFsCatching {
                    val treeUri = Uri.parse(base.uri)
                    val baseDir = DocumentFile.fromTreeUri(context, treeUri)
                        ?: throw IllegalStateException("Cannot resolve tree URI: ${base.uri}")

                    val (parentPath, fileName) = splitParentAndFileName(relativePath)
                    val parentDir = if (parentPath.isEmpty()) {
                        baseDir
                    } else {
                        createOrNavigateToDirectory(baseDir, parentPath)
                    }

                    val file = parentDir.findFile(fileName)?.takeIf { !it.isDirectory }
                        ?: parentDir.createFile("application/octet-stream", fileName)
                        ?: throw IllegalStateException("Failed to create file: $fileName")

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
                val fullPath = File(base.path, relativePath).absolutePath
                appendBytes(fullPath, bytes)
            }
            is SaveLocation.TreeUri -> {
                runFsCatching {
                    val treeUri = Uri.parse(base.uri)
                    val baseDir = DocumentFile.fromTreeUri(context, treeUri)
                        ?: throw IllegalStateException("Cannot resolve tree URI: ${base.uri}")

                    val (parentPath, fileName) = splitParentAndFileName(relativePath)
                    val parentDir = if (parentPath.isEmpty()) {
                        baseDir
                    } else {
                        createOrNavigateToDirectory(baseDir, parentPath)
                    }

                    val file = parentDir.findFile(fileName)?.takeIf { !it.isDirectory }
                        ?: parentDir.createFile("application/octet-stream", fileName)
                        ?: throw IllegalStateException("Failed to create file: $fileName")

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
                val fullPath = File(base.path, relativePath).absolutePath
                readString(fullPath)
            }
            is SaveLocation.TreeUri -> {
                runFsCatching {
                    val treeUri = Uri.parse(base.uri)
                    val baseDir = DocumentFile.fromTreeUri(context, treeUri)
                        ?: throw IllegalStateException("Cannot resolve tree URI: ${base.uri}")

                    val (parentPath, fileName) = splitParentAndFileName(relativePath)
                    val parentDir = if (parentPath.isEmpty()) {
                        baseDir
                    } else {
                        navigateToDirectory(baseDir, parentPath)
                            ?: throw IllegalStateException("Directory not found: $parentPath")
                    }

                    val file = parentDir.findFile(fileName)
                        ?: throw IllegalStateException("File not found: $fileName")

                    context.contentResolver.openInputStream(file.uri)?.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var totalRead = 0L
                        var zeroReadCount = 0
                        while (true) {
                            val read = input.read(buffer)
                            when {
                                read < 0 -> break
                                read == 0 -> {
                                    zeroReadCount += 1
                                    if (zeroReadCount >= 100) {
                                        throw IllegalStateException("Read stalled while loading file: $fileName")
                                    }
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
            }
            is SaveLocation.Bookmark -> {
                Result.failure(UnsupportedOperationException("Bookmark SaveLocation is not supported on Android"))
            }
        }
    }

    override suspend fun exists(base: SaveLocation, relativePath: String): Boolean = withContext(Dispatchers.IO) {
        when (base) {
            is SaveLocation.Path -> {
                val fullPath = if (relativePath.isEmpty()) base.path else File(base.path, relativePath).absolutePath
                exists(fullPath)
            }
            is SaveLocation.TreeUri -> {
                try {
                    val treeUri = Uri.parse(base.uri)
                    val baseDir = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext false

                    if (relativePath.isEmpty()) {
                        return@withContext baseDir.exists()
                    }

                    val (parentPath, fileName) = splitParentAndFileName(relativePath)
                    val parentDir = if (parentPath.isEmpty()) {
                        baseDir
                    } else {
                        navigateToDirectory(baseDir, parentPath) ?: return@withContext false
                    }

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
                val fullPath = if (relativePath.isEmpty()) base.path else File(base.path, relativePath).absolutePath
                delete(fullPath)
            }
            is SaveLocation.TreeUri -> {
                runFsCatching {
                    val treeUri = Uri.parse(base.uri)
                    val baseDir = DocumentFile.fromTreeUri(context, treeUri)
                        ?: throw IllegalStateException("Cannot resolve tree URI: ${base.uri}")

                    if (relativePath.isEmpty()) {
                        // Safety guard: never delete the user-selected TreeUri root from app code.
                        Logger.w(
                            "AndroidFileSystem",
                            "Refusing to delete TreeUri base directory directly: ${base.uri}"
                        )
                        return@runFsCatching Unit
                    }

                    val (parentPath, fileName) = splitParentAndFileName(relativePath)
                    val parentDir = if (parentPath.isEmpty()) {
                        baseDir
                    } else {
                        navigateToDirectory(baseDir, parentPath) ?: return@runFsCatching Unit
                    }

                    val target = parentDir.findFile(fileName) ?: return@runFsCatching Unit

                    if (!target.delete()) {
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

    private fun splitParentAndFileName(relativePath: String): Pair<String, String> {
        val normalized = relativePath.trim().trim('/')
        val lastSlash = normalized.lastIndexOf('/')
        return if (lastSlash < 0) {
            "" to normalized
        } else {
            normalized.substring(0, lastSlash) to normalized.substring(lastSlash + 1)
        }
    }

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
