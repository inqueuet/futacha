package com.valoser.futacha.shared.desktop

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import kotlinx.coroutines.*

/** Explicitly installed by the desktop host; common/JVM tests never touch user data. */
class DesktopEnvironment(val dataDirectory: File, val cacheDirectory: File) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    internal val preferences by lazy {
        PreferenceDataStoreFactory.create(scope = scope) {
            File(dataDirectory, "settings.preferences_pb").also { it.parentFile.mkdirs() }
        }
    }
    private var channel: FileChannel? = null
    private var lock: java.nio.channels.FileLock? = null

    fun install() {
        check(current == null) { "Desktop environment is already installed" }
        // ORT's POSIX telemetry uploader must be disabled before the first OrtEnvironment.
        // setTelemetry(false) alone stops events but can leave the native uploader alive at exit.
        DesktopPlatform.setEnvironment("ORT_DISABLE_TELEMETRY", "1")
        check(dataDirectory.isDirectory || dataDirectory.mkdirs()) { "データ保存先を作成できません: $dataDirectory" }
        check(cacheDirectory.isDirectory || cacheDirectory.mkdirs()) { "キャッシュ保存先を作成できません: $cacheDirectory" }
        val file = File(dataDirectory, "application.lock")
        channel = FileChannel.open(file.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
        lock = channel!!.tryLock()
        if (lock == null) { channel?.close(); error("ふたちゃは既に起動しています") }
        current = this
    }

    override fun close() {
        scope.cancel()
        lock?.let { if (it.isValid) it.release() }; lock = null
        channel?.close(); channel = null
        if (current === this) current = null
    }

    suspend fun closeAndAwait() { close(); scope.coroutineContext[Job]?.join() }

    companion object {
        @Volatile var current: DesktopEnvironment? = null
            private set

        fun default(): DesktopEnvironment {
            val override = System.getProperty("futacha.dataDir")
            val home = if (override == null && DesktopPlatform.isMac &&
                java.lang.Boolean.getBoolean("futacha.mac.appStore")) MacNative.homeDirectory()
                else File(System.getProperty("user.home"))
            return if (override != null) DesktopEnvironment(File(override), File(override, "cache"))
            else if (DesktopPlatform.isWindows) {
                val base = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }?.let(::File)
                    ?: File(home, "AppData/Local")
                DesktopEnvironment(File(base, "Futacha/data"), File(base, "Futacha/cache"))
            } else DesktopEnvironment(File(home, "Library/Application Support/Futacha"), File(home, "Library/Caches/Futacha"))
        }
    }
}
