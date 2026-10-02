package com.valoser.futacha.shared.ai

import com.valoser.futacha.shared.desktop.DesktopEnvironment
import com.valoser.futacha.shared.desktop.DesktopPlatform
import com.sun.jna.platform.win32.Crypt32Util
import java.io.File
import com.valoser.futacha.shared.util.moveReplacingWithRetry
import java.nio.file.CopyOption
import java.nio.file.Files
import java.nio.file.Path
import java.util.WeakHashMap
import kotlinx.serialization.json.jsonPrimitive

private val stores = WeakHashMap<DesktopEnvironment, AiConnectionStore>()
private val unavailable = AiConnectionStore(object : AiConnectionStorage {
    override val supported = false
    override fun read(): String? = null
    override fun write(value: String): Unit = error("Secure credential storage is unavailable")
})
@Synchronized
actual fun getAiConnectionStore(platformContext: Any?): AiConnectionStore {
    val environment = DesktopEnvironment.current ?: return unavailable
    return stores.getOrPut(environment) {
        val credentials = when {
            DesktopPlatform.isWindows -> WindowsAiCredentials(File(environment.dataDirectory, "private/openai.dpapi"))
            DesktopPlatform.isMac -> MacAiCredentials(aiDigest(environment.dataDirectory.canonicalPath))
            else -> return unavailable
        }
        AiConnectionStore(DesktopAiConnectionStorage(credentials, File(environment.cacheDirectory, "openai-analysis-v1.json"), File(environment.dataDirectory, "openai-usage-v1.json")))
    }
}

internal interface DesktopAiCredentials {
    fun read(): String?
    fun write(value: String)
}

internal class DesktopAiConnectionStorage(
    private val credentials: DesktopAiCredentials,
    private val cacheFile: File,
    private val usageFile: File = File(cacheFile.parentFile, "openai-usage-v1.json")
) : AiConnectionStorage {
    override fun read() = credentials.read()
    override fun write(value: String) = credentials.write(value)
    override fun readUsage(): String? = if (usageFile.isFile && usageFile.length() <= 100_000) usageFile.readText() else null
    override fun writeUsage(value: String) = writeDesktopAiFile(usageFile, value.encodeToByteArray())
    override fun readCache(): String? = if (cacheFile.isFile && cacheFile.length() <= 4_000_000) cacheFile.readText() else null
    override fun writeCache(value: String) = writeDesktopAiFile(cacheFile, value.encodeToByteArray())
}

/** DPAPI uses the current Windows account. Never use CRYPTPROTECT_LOCAL_MACHINE. */
internal class WindowsAiCredentials(private val file: File) : DesktopAiCredentials {
    override fun read(): String? {
        if (!file.exists()) return null
        check(file.length() in 1..131_072) { "Invalid credential data" }
        val plain = Crypt32Util.cryptUnprotectData(file.readBytes(), 0x1 /* CRYPTPROTECT_UI_FORBIDDEN */)
        try { check(plain.size <= MAX_AI_CREDENTIAL_BYTES); return plain.decodeToString(throwOnInvalidSequence = true) }
        finally { plain.fill(0) }
    }
    override fun write(value: String) {
        val plain = value.encodeToByteArray()
        try {
            require(plain.size in 1..MAX_AI_CREDENTIAL_BYTES)
            writeDesktopAiFile(file, Crypt32Util.cryptProtectData(plain, 0x1 /* CRYPTPROTECT_UI_FORBIDDEN */))
        } finally { plain.fill(0) }
    }
}

internal class MacAiCredentials(private val account: String) : DesktopAiCredentials {
    override fun read(): String? = MacAiNative.call("credentialRead", "account" to account)["value"]?.jsonPrimitive?.content
    override fun write(value: String) { MacAiNative.call("credentialWrite", "account" to account, "value" to value) }
    internal fun delete() { MacAiNative.call("credentialDelete", "account" to account) }
}

/** Windows scanners briefly holding the target must not drop usage/cache/credential writes (N4-3). */
internal fun writeDesktopAiFile(
    file: File,
    bytes: ByteArray,
    move: (Path, Path, Array<out CopyOption>) -> Unit = { source, target, options -> Files.move(source, target, *options) }
) {
    Files.createDirectories(file.parentFile.toPath())
    val temporary = Files.createTempFile(file.parentFile.toPath(), ".ai-", ".tmp")
    try {
        Files.write(temporary, bytes)
        moveReplacingWithRetry(temporary, file.toPath(), move)
    } finally { Files.deleteIfExists(temporary) }
}
