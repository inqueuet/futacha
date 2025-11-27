package com.valoser.futacha.shared.util

import android.content.Context
import android.os.Build
import android.os.Environment
import com.valoser.futacha.shared.service.AUTO_SAVE_DIRECTORY
import com.valoser.futacha.shared.service.MANUAL_SAVE_DIRECTORY
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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
                // FIX: mkdirs()の戻り値をチェック
                val success = dir.mkdirs()
                if (!success && !dir.exists()) {
                    throw IllegalStateException("Failed to create directory: ${dir.absolutePath}")
                }
            }
        }
    }

    override suspend fun writeBytes(path: String, bytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(resolveAbsolutePath(path))
            // FIX: mkdirs()の戻り値をチェック
            file.parentFile?.let { parent ->
                if (!parent.exists()) {
                    val success = parent.mkdirs()
                    if (!success && !parent.exists()) {
                        throw IllegalStateException("Failed to create parent directory: ${parent.absolutePath}")
                    }
                }
            }
            file.writeBytes(bytes)
        }
    }

    override suspend fun appendBytes(path: String, bytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(resolveAbsolutePath(path))
            // FIX: mkdirs()の戻り値をチェック
            file.parentFile?.let { parent ->
                if (!parent.exists()) {
                    val success = parent.mkdirs()
                    if (!success && !parent.exists()) {
                        throw IllegalStateException("Failed to create parent directory: ${parent.absolutePath}")
                    }
                }
            }
            file.appendBytes(bytes)
        }
    }

    override suspend fun writeString(path: String, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(resolveAbsolutePath(path))
            // FIX: mkdirs()の戻り値をチェック
            file.parentFile?.let { parent ->
                if (!parent.exists()) {
                    val success = parent.mkdirs()
                    if (!success && !parent.exists()) {
                        throw IllegalStateException("Failed to create parent directory: ${parent.absolutePath}")
                    }
                }
            }
            file.writeText(content, Charsets.UTF_8)
        }
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
}

actual fun createFileSystem(platformContext: Any?): FileSystem {
    require(platformContext is Context) { "Android requires Context" }
    return AndroidFileSystem(platformContext)
}
