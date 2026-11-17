package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.ui.compat.CompatBrowserNavigation
import com.valoser.futacha.shared.ui.compat.CompatBrowserNavigationCommand
import com.valoser.futacha.shared.ui.compat.CompatBrowserState
import com.valoser.futacha.shared.ui.compat.CompatReverseImageSearchWebView

/*
 * L-B12 (M19・F12): 「プレイヤーを表示」. A YouTube window at the top of the thread, under its title, with a black band
 * (‹ › 拡大 ×); the thread goes on under it. It opens the first YouTube address a post of the thread holds, or YouTube's
 * own front page when there is none (the original does the same). It is a page in a web view that loads only when the
 * person asks for it; nothing of the thread is sent anywhere.
 */

internal const val FUTABER_PLAYER_HOME_URL = "https://m.youtube.com/"
private val FUTABER_PLAYER_HOSTS = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be", "www.youtu.be")
private val FUTABER_PLAYER_URL = Regex("""https?://[A-Za-z0-9.\-]+(?::\d+)?/[A-Za-z0-9._~:/?#\[\]@!$&'()*+,;=%\-]*""")

/** Whether [url] is an https address on a YouTube host (the only pages the player takes). */
internal fun futaberPlayerUrlIsAllowed(url: String): Boolean {
    if (!url.startsWith("https://", ignoreCase = true)) return false
    val host = url.substring(8).substringBefore('/').substringBefore('?').substringBefore('#').substringBefore(':').lowercase()
    return host in FUTABER_PLAYER_HOSTS
}

/** The first YouTube address in the posts' text (https only; an http one is moved to https), else YouTube's front page. */
internal fun futaberPlayerStartUrl(posts: List<Post>): String {
    for (post in posts) {
        val text = post.messageHtml.replace("&amp;", "&")
        for (match in FUTABER_PLAYER_URL.findAll(text.replace("http://", "https://"))) {
            val candidate = match.value.trimEnd('.', ',', ')', ']', '。')
            if (futaberPlayerUrlIsAllowed(candidate)) return candidate
        }
    }
    return FUTABER_PLAYER_HOME_URL
}

/** The height of the window: a band above the picture is 36dp; expanded takes more of the screen. */
internal const val FUTABER_PLAYER_BAND_DP = 36
internal const val FUTABER_PLAYER_HEIGHT_DP = 200
internal const val FUTABER_PLAYER_EXPANDED_HEIGHT_DP = 360

@Composable
internal fun FutaberPlayerWindow(startUrl: String, onClose: () -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf(CompatBrowserState()) }
    var serial by remember { mutableLongStateOf(0L) }
    var command by remember { mutableStateOf<CompatBrowserNavigationCommand?>(null) }
    fun navigate(direction: CompatBrowserNavigation) { serial += 1; command = CompatBrowserNavigationCommand(serial, direction) }
    Column(modifier.fillMaxWidth().background(Color.Black).testTag("futaber-player")) {
        Box(Modifier.fillMaxWidth().height((if (expanded) FUTABER_PLAYER_EXPANDED_HEIGHT_DP else FUTABER_PLAYER_HEIGHT_DP).dp)) {
            CompatReverseImageSearchWebView(
                initialUrl = startUrl, initialHtml = null, baseUrl = null, initialCookies = emptyList(),
                navigationCommand = command, modifier = Modifier.fillMaxWidth().fillMaxHeight().testTag("futaber-player-web"),
                onStateChanged = { state = it }, onLinkLongPressed = {}, onCookiesChanged = { _, _ -> }
            )
        }
        Row(
            Modifier.fillMaxWidth().height(FUTABER_PLAYER_BAND_DP.dp).background(Color(0xFF1C1C1E)),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            PlayerBandButton("‹", "戻る", "futaber-player-back", state.canGoBack) { navigate(CompatBrowserNavigation.BACK) }
            PlayerBandButton("›", "進む", "futaber-player-forward", state.canGoForward) { navigate(CompatBrowserNavigation.FORWARD) }
            PlayerBandButton(if (expanded) "縮小" else "拡大", if (expanded) "プレイヤーを縮小" else "プレイヤーを拡大", "futaber-player-expand", true) { expanded = !expanded }
            PlayerBandButton("×", "プレイヤーを閉じる", "futaber-player-close", true, onClose)
        }
    }
}

@Composable
private fun PlayerBandButton(label: String, description: String, tag: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.widthIn(min = 56.dp).heightIn(min = 36.dp).clickable(enabled = enabled, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description }.testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (enabled) Color.White else Color(0xFF8E8E93), fontSize = 17.sp)
    }
}
