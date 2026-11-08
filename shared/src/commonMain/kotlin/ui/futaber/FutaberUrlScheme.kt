package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/*
 * L-B12 (C05・M13): futaber's 「URLスキーム」. Two templates the person writes: the browser's (「[url]」 is replaced by the
 * address to open; empty = the default browser) and the action's (「[text]」 is replaced by the text; empty = the action
 * only closes, as the original does while none is registered).
 */

internal const val FUTABER_URL_SCHEME_URL_PLACEHOLDER = "[url]"
internal const val FUTABER_URL_SCHEME_TEXT_PLACEHOLDER = "[text]"

internal const val FUTABER_URL_SCHEME_BROWSER_NOTE =
    "ブラウザで開く時に使うURLスキームです。「[url]」が開くアドレスに置き換わります。空のままにすると既定のブラウザで開きます。\n" +
        "例：googlechromes://[url]"
internal const val FUTABER_URL_SCHEME_ACTION_NOTE =
    "「URLスキームアクション」で使うURLスキームです。「[text]」が渡すアドレスに置き換わります。空のままにすると何も起きません。\n" +
        "例：ghttp://[text]"

/** The reason shown when a template cannot be used (a person typed something that is not a scheme). */
internal const val FUTABER_URL_SCHEME_INVALID = "URLスキームの形が正しくありません。設定のURLスキームを確かめてください。"

/** Percent-encodes [value] as UTF-8, leaving the characters that are safe in a query/path as they are. */
internal fun futaberUrlSchemeEncode(value: String): String {
    val keep = "-._~"
    val out = StringBuilder()
    for (byte in value.encodeToByteArray()) {
        val code = byte.toInt() and 0xFF
        val ch = code.toChar()
        if (code < 0x80 && (ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch in keep)) out.append(ch)
        else {
            out.append('%')
            out.append("0123456789ABCDEF"[code shr 4])
            out.append("0123456789ABCDEF"[code and 0x0F])
        }
    }
    return out.toString()
}

/** Whether [template] begins with a scheme (letters, digits, + - .) and a colon. */
internal fun futaberUrlSchemeIsValid(template: String): Boolean {
    val head = template.trim().substringBefore(':', "")
    return head.isNotEmpty() && head.length <= 32 && head[0].lowercaseChar() in 'a'..'z' &&
        head.all { it.lowercaseChar() in 'a'..'z' || it in '0'..'9' || it == '+' || it == '-' || it == '.' } &&
        template.trim().contains(':')
}

/**
 * [template] with [placeholder] replaced by [value] (percent-encoded). Null when the template is empty (nothing registered)
 * or is not a scheme. A template without the placeholder is used as it is.
 */
internal fun futaberUrlSchemeExpand(template: String, placeholder: String, value: String): String? {
    val trimmed = template.trim()
    if (trimmed.isEmpty() || !futaberUrlSchemeIsValid(trimmed)) return null
    return trimmed.replace(placeholder, futaberUrlSchemeEncode(value))
}

/** What the person registered, read from the preferences. */
internal data class FutaberUrlSchemes(val browser: String = "", val action: String = "") {
    /** The address the browser button opens: the template filled in, or [url] itself while no template is registered. */
    fun browserTarget(url: String): String = futaberUrlSchemeExpand(browser, FUTABER_URL_SCHEME_URL_PLACEHOLDER, url) ?: url
    /** The address the action opens, or null (the action only closes). */
    fun actionTarget(text: String): String? = futaberUrlSchemeExpand(action, FUTABER_URL_SCHEME_TEXT_PLACEHOLDER, text)
    /** The browser template is set but is not a scheme (so the default browser is used, and the person is told). */
    val browserInvalid: Boolean get() = browser.isNotBlank() && !futaberUrlSchemeIsValid(browser)
    val actionInvalid: Boolean get() = action.isNotBlank() && !futaberUrlSchemeIsValid(action)

    companion object {
        fun from(preferences: Map<String, String>) = FutaberUrlSchemes(
            browser = preferences[FutaberSettingKeys.URL_SCHEME_BROWSER].orEmpty(),
            action = preferences[FutaberSettingKeys.URL_SCHEME_ACTION].orEmpty()
        )
    }
}

/** futaber's 「URLスキーム」 page. */
@Composable
internal fun FutaberUrlSchemePage(
    schemes: FutaberUrlSchemes,
    onSettingChange: (key: String, value: String) -> Unit
) {
    // The typed text is kept here and written through, so a keystroke is not lost while the stored value comes back.
    var browser by remember { mutableStateOf(schemes.browser) }
    var action by remember { mutableStateOf(schemes.action) }
    FutaberTableSection("ブラウザのURLスキーム", tag = "futaber-url-scheme-browser-title") {
        FutaberTableFieldRow(
            "URLスキーム", "futaber-url-scheme-browser", browser,
            onValueChange = { browser = it; onSettingChange(FutaberSettingKeys.URL_SCHEME_BROWSER, it) }, last = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
        )
    }
    FutaberTableNote(FUTABER_URL_SCHEME_BROWSER_NOTE, tag = "futaber-url-scheme-browser-note")
    FutaberTableSection("URLスキームアクション", tag = "futaber-url-scheme-action-title") {
        FutaberTableFieldRow(
            "URLスキーム", "futaber-url-scheme-action", action,
            onValueChange = { action = it; onSettingChange(FutaberSettingKeys.URL_SCHEME_ACTION, it) }, last = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
        )
    }
    FutaberTableNote(FUTABER_URL_SCHEME_ACTION_NOTE, tag = "futaber-url-scheme-action-note")
    Spacer(Modifier.height(32.dp))
}

/** Opens what the two templates make of an address: [browser] for 「ブラウザで開く」, [action] for 「URLスキームアクション」. */
internal class FutaberUrlSchemeOpener(
    private val schemes: FutaberUrlSchemes,
    private val plain: (String) -> Unit,
    private val any: (String) -> Unit
) {
    /** The address in the person's browser scheme, or in the default browser while none is registered. */
    fun browser(url: String) {
        val target = schemes.browserTarget(url)
        if (target == url) plain(url) else any(target)
    }

    /**
     * The URL scheme action: nothing registered closes without opening anything (the original does the same); a template
     * that is not a scheme goes to the launcher, which says it cannot open it.
     */
    fun action(text: String) {
        if (schemes.action.isBlank()) return
        any(schemes.actionTarget(text) ?: schemes.action.trim())
    }
}

@Composable
internal fun rememberFutaberUrlSchemeOpener(schemes: FutaberUrlSchemes): FutaberUrlSchemeOpener {
    val plain = com.valoser.futacha.shared.util.rememberUrlLauncher()
    val any = com.valoser.futacha.shared.util.rememberUrlLauncher(allowAnyScheme = true)
    return remember(schemes, plain, any) { FutaberUrlSchemeOpener(schemes, plain, any) }
}
