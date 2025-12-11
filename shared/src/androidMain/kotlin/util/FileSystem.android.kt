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
import java.io.File
import java.io.IOException

/**
 * Android版FileSystem実装
 */
class AndroidFileSystem(
    private val context: Context
) : FileSystem {

    override suspend fun createDirectory(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(resolveAbsolutePath(path))
            if (!dir.exists()) {
                if (!dir.mkdirs() && !dir.exists()) {
                    throw IllegalStateException("Failed to create directory: ${dir.absolutePath}")
                }
            }
        }
    }

    override suspend fun writeBytes(path: String, bytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(resolveAbsolutePath(path))
            file.parentFile?.let { parent ->
                if (!parent.exists()) {
                    if (!parent.mkdirs() && !parent.exists()) {
                        throw IllegalStateException("Failed to create parent directory: ${parent.absolutePath}")
                    }
                }
            }
            
            // Atomic write: write to temp file then rename
            val tmpFile = File.createTempFile("tmp_", ".tmp", file.parentFile)
            try {
                tmpFile.writeBytes(bytes)
                if (!tmpFile.renameTo(file)) {
                    // renameTo can fail if target exists on some file systems, try delete + rename
                    if (file.exists() && !file.delete()) {
                         throw IOException("Failed to delete existing file for atomic write: ${file.absolutePath}")
                    }
                    if (!tmpFile.renameTo(file)) {
                        throw IOException("Failed to rename temp file to target: ${file.absolutePath}")
                    }
                }
            } finally {
                if (tmpFile.exists()) {
                    tmpFile.delete()
                }
            }
        }
    }

    override suspend fun appendBytes(path: String, bytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
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
        // writeBytes uses atomic write, so we delegate to it
        writeBytes(path, content.toByteArray(Charsets.UTF_8))
    }

    override suspend fun readBytes(path: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(resolveAbsolutePath(path))
            file.readBytes()
        }
    }

    override suspend fun readString(path: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(resolveAbsolutePath(path))
            file.readText(Charsets.UTF_8)
        }
    }

    override suspend fun delete(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(resolveAbsolutePath(path))
            file.delete()
            Unit
        }
    }

    override suspend fun deleteRecursively(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(resolveAbsolutePath(path))
            file.deleteRecursively()
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
     */
    private fun getPublicDocumentsDirectory(): String {
        try {
            val documentsDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10以降: getExternalStoragePublicDirectory は非推奨だが Documents は引き続き使用可能
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
     */
    private fun getPublicDownloadsDirectory(): String {
        try {
            val downloadsDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
        when (base) {
            is SaveLocation.Path -> {
                val fullPath = if (relativePath.isEmpty()) base.path else File(base.path, relativePath).absolutePath
                createDirectory(fullPath)
            }
            is SaveLocation.TreeUri -> {
                runCatching {
                    val treeUri = Uri.parse(base.uri)
                    val baseDir = DocumentFile.fromTreeUri(context, treeUri)
                        ?: throw IllegalStateException("Cannot resolve tree URI: ${base.uri}. Permission may have been revoked.")

                    // Verify we still have permission
                    if (!baseDir.canWrite()) {
                        throw SecurityException("No write permission for tree URI: ${base.uri}")
                    }

                    if (relativePath.isEmpty()) {
                        // Base directory already exists if we can resolve it
                        return@runCatching Unit
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
        when (base) {
            is SaveLocation.Path -> {
                val fullPath = File(base.path, relativePath).absolutePath
                writeBytes(fullPath, bytes)
            }
            is SaveLocation.TreeUri -> {
                runCatching {
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
        when (base) {
            is SaveLocation.Path -> {
                val fullPath = File(base.path, relativePath).absolutePath
                appendBytes(fullPath, bytes)
            }
            is SaveLocation.TreeUri -> {
                runCatching {
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
        return writeBytes(base, relativePath, content.toByteArray(Charsets.UTF_8))
    }

    override suspend fun readString(base: SaveLocation, relativePath: String): Result<String> = withContext(Dispatchers.IO) {
        when (base) {
            is SaveLocation.Path -> {
                val fullPath = File(base.path, relativePath).absolutePath
                readString(fullPath)
            }
            is SaveLocation.TreeUri -> {
                runCatching {
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
                        input.readBytes().toString(Charsets.UTF_8)
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

    private fun navigateToDirectory(base: DocumentFile, relativePath: String): DocumentFile? {
        if (relativePath.isEmpty()) return base
        val segments = relativePath.split('/').filter { it.isNotBlank() }
        var current = base
        for (segment in segments) {
            val next = current.findFile(segment)
            if (next == null || !next.isDirectory) {
                return null
            }
            current = next
        }
        return current
    }

    private fun createOrNavigateToDirectory(base: DocumentFile, relativePath: String): DocumentFile {
        if (relativePath.isEmpty()) return base
        val segments = relativePath.split('/').filter { it.isNotBlank() }
        var current = base
        for (segment in segments) {
            val existing = current.findFile(segment)
            current = if (existing != null && existing.isDirectory) {
                existing
            } else if (existing == null) {
                current.createDirectory(segment)
                    ?: throw IllegalStateException("Failed to create directory: $segment")
            } else {
                throw IllegalStateException("Path segment exists but is not a directory: $segment")
            }
        }
        return current
    }
}

actual fun createFileSystem(platformContext: Any?): FileSystem {
    require(platformContext is Context) { "Android requires Context" }
    return AndroidFileSystem(platformContext)
}
