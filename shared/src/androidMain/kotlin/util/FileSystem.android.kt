package com.valoser.futacha.shared.util

import android.content.Context
import android.os.Build
import android.os.Environment
import com.valoser.futacha.shared.service.AUTO_SAVE_DIRECTORY
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
                dir.mkdirs()
            }
        }
    }

    override suspend fun writeBytes(path: String, bytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(resolveAbsolutePath(path))
            file.parentFile?.mkdirs()
            file.writeBytes(bytes)
        }
    }

    override suspend fun writeString(path: String, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(resolveAbsolutePath(path))
            file.parentFile?.mkdirs()
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
        return if (relativePath.startsWith("/")) {
            relativePath
        } else {
            val baseDir = if (relativePath.startsWith(AUTO_SAVE_DIRECTORY)) {
                getPrivateAppDataDirectory()
            } else {
                getAppDataDirectory()
            }
            File(baseDir, relativePath).absolutePath
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
                            mkdirs()
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
                    mkdirs()
                }
            }.absolutePath
        } catch (e: Exception) {
            Logger.e("FileSystem.android", "Unexpected error accessing storage", e)
            // Fallback to internal storage
            return File(context.filesDir, "futacha").apply {
                if (!exists()) {
                    mkdirs()
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
