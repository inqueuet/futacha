package com.valoser.futacha.shared.desktop

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.io.File
import java.util.UUID

private interface MacBridge : Library {
    fun futacha_mac_call(request: String): Pointer
    fun futacha_mac_free(result: Pointer)
}

/** The native bridge confines AppKit to its own main thread; callers use Dispatchers.IO. */
internal object MacNative {
    private val bridge by lazy {
        Native.load(desktopResource("native/libfutacha_macos.dylib").absolutePath, MacBridge::class.java,
            mapOf(Library.OPTION_STRING_ENCODING to "UTF-8"))
    }

    suspend fun call(operation: String, vararg fields: Pair<String, String>): JsonObject = withContext(Dispatchers.IO) {
        val request = buildJsonObject {
            put("op", operation)
            fields.forEach { (key, value) -> put(key, value) }
        }
        val result = bridge.futacha_mac_call(request.toString())
        try { Json.parseToJsonElement(result.getString(0, "UTF-8")).jsonObject }
        finally { bridge.futacha_mac_free(result) }
    }

    suspend fun operation(name: String, vararg fields: Pair<String, String>): JsonObject {
        val id = UUID.randomUUID().toString()
        try {
            var result = call(name, "id" to id, *fields)
            while (result.status() == "pending") { delay(100); result = call("poll", "id" to id) }
            return result.checked()
        } finally { withContext(NonCancellable) { call("cancel", "id" to id) } }
    }
}

internal fun JsonObject.status(): String = this["status"]?.jsonPrimitive?.content.orEmpty()
internal fun JsonObject.checked(): JsonObject {
    check(status() != "error") { this["message"]?.jsonPrimitive?.content ?: "Macの操作に失敗しました" }
    return this
}

object MacOsIntegration {
    suspend fun notificationPermission(): Boolean =
        MacNative.operation("notificationPermission")["granted"]?.jsonPrimitive?.booleanOrNull == true

    suspend fun notificationAllowed(): Boolean =
        withTimeout(10_000) { MacNative.operation("notificationStatus")["granted"]?.jsonPrimitive?.booleanOrNull == true }

    suspend fun notificationSettings() { MacNative.call("notificationSettings").checked() }

    suspend fun notify(identifier: String, title: String, body: String, threadUrl: String) {
        require(com.valoser.futacha.shared.compat.canonicalizeThreadUrl(threadUrl) != null)
        MacNative.operation("notify", "notificationId" to identifier, "title" to title.take(120),
            "body" to body.take(1000), "url" to threadUrl)
    }

    suspend fun share(text: String, file: String?) {
        file?.let { require(File(it).isFile) { "共有するファイルが見つかりません" } }
        MacNative.operation("share", "text" to text, "path" to file.orEmpty())
    }

    suspend fun chooseDirectory(): File? = MacNative.operation("directory").let {
        if (it.status() == "cancelled") null else it["path"]?.jsonPrimitive?.content?.let(::File)
    }

    suspend fun applyDockIcon(variant: com.valoser.futacha.shared.model.AppIconVariant) {
        MacNative.call("dockIcon", "path" to desktopResource("icons/${variant.name}.png").absolutePath).checked()
    }

    suspend fun notificationLinks(): List<String> = MacNative.call("events")["urls"]?.jsonArray.orEmpty()
        .map { it.jsonPrimitive.content }.filter { com.valoser.futacha.shared.compat.canonicalizeThreadUrl(it) != null }

    /** No permission prompts, microphone capture or external sharing during this diagnostic. */
    suspend fun checkInstallation(output: File) {
        val capabilities = MacNative.call("capabilities").checked()
        check(capabilities["bundle"]?.jsonPrimitive?.content == "com.valoser.futacha.desktop")
        val permissions = withTimeout(10_000) { MacNative.operation("notificationStatus") }
        for (icon in com.valoser.futacha.shared.model.AppIconVariant.entries) applyDockIcon(icon)
        applyDockIcon(com.valoser.futacha.shared.model.AppIconVariant.Current)
        val panels = listOf("directory", "share")
        for (operation in panels) {
            val id = UUID.randomUUID().toString()
            try {
                val started = MacNative.call(operation, "id" to id, "text" to "ふたちゃ Mac連携の確認").checked()
                check(started.status() == "pending") { "$operation did not open: $started" }
                delay(500)
            } finally { withContext(NonCancellable) { MacNative.call("cancel", "id" to id).checked() } }
            check(MacNative.call("poll", "id" to id).status() == "cancelled") { "$operation did not release its session" }
            delay(250)
        }
        File(output, "macos-integrations.json").writeText(buildJsonObject {
            put("capabilities", capabilities); put("notificationSettings", permissions)
            put("dockIcons", "all variants applied and restored")
            put("panels", "directory and sharing picker opened, cancelled and released")
        }.toString())
    }
}
