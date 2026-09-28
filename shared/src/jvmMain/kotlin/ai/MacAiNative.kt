package com.valoser.futacha.shared.ai

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.valoser.futacha.shared.desktop.desktopResource
import kotlinx.serialization.json.*

private interface MacAiBridge : Library {
    fun futacha_ai_call(request: String): Pointer
    fun futacha_ai_free(result: Pointer)
}

internal object MacAiNative {
    private val bridge by lazy {
        Native.load(desktopResource("native/libfutacha_ai.dylib").absolutePath, MacAiBridge::class.java,
            mapOf(Library.OPTION_STRING_ENCODING to "UTF-8"))
    }

    // Calls never dispatch synchronously to AppKit. Credentials and model inputs are not logged.
    fun call(operation: String, vararg fields: Pair<String, String>): JsonObject {
        val request = buildJsonObject { put("op", operation); fields.forEach { (k, v) -> put(k, v) } }
        val pointer = try { bridge.futacha_ai_call(request.toString()) }
        catch (_: LinkageError) { throw IllegalStateException("MacのAI接続を読み込めませんでした。アプリを再インストールしてください。") }
        try {
            val result = Json.parseToJsonElement(pointer.getString(0, "UTF-8")).jsonObject
            check(result["status"]?.jsonPrimitive?.content != "error") {
                result["message"]?.jsonPrimitive?.content ?: "MacのAI処理に失敗しました。"
            }
            return result
        } finally { bridge.futacha_ai_free(pointer) }
    }
}
