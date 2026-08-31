package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.valoser.futacha.shared.analytics.AnalyticsTracker
import com.valoser.futacha.shared.analytics.analyticsSessionContextId
import com.valoser.futacha.shared.audio.TextSpeaker
import com.valoser.futacha.shared.audio.createTextSpeaker
import com.valoser.futacha.shared.util.rememberUrlLauncher
import kotlinx.serialization.json.Json

internal data class ThreadScreenPlatformRuntimeBindings(
    val archiveSearchJson: Json,
    val externalUrlLauncher: (String) -> Unit,
    val handleUrlClick: (String) -> Unit,
    val textSpeaker: TextSpeaker
)

@Composable
internal fun rememberThreadScreenPlatformRuntimeBindings(
    platformContext: Any?,
    onRegisteredThreadUrlClick: (String) -> Boolean
): ThreadScreenPlatformRuntimeBindings {
    val externalUrlLauncher = rememberUrlLauncher()
    val handleUrlClick = remember(onRegisteredThreadUrlClick, externalUrlLauncher) {
        { url: String ->
            handleThreadUrlClick(
                url = url,
                onRegisteredThreadUrlClick = onRegisteredThreadUrlClick,
                onLaunchExternalUrl = externalUrlLauncher
            )
        }
    }
    val archiveSearchJson = remember { Json { ignoreUnknownKeys = true } }
    val textSpeaker = remember(platformContext) { createTextSpeaker(platformContext) }
    return remember(
        archiveSearchJson,
        externalUrlLauncher,
        handleUrlClick,
        textSpeaker
    ) {
        ThreadScreenPlatformRuntimeBindings(
            archiveSearchJson = archiveSearchJson,
            externalUrlLauncher = externalUrlLauncher,
            handleUrlClick = handleUrlClick,
            textSpeaker = textSpeaker
        )
    }
}

internal fun handleThreadUrlClick(
    url: String,
    onRegisteredThreadUrlClick: (String) -> Boolean,
    onLaunchExternalUrl: (String) -> Unit
) {
    val openedInApp = onRegisteredThreadUrlClick(url)
    AnalyticsTracker.uiControl(
        control = "thread_link",
        label = if (openedInApp) "本文の登録済みスレッドURLをアプリ内で開く" else "本文の外部URLをブラウザで開く",
        params = mapOf(
            "url_context" to analyticsSessionContextId("url", url),
            "open_destination" to if (openedInApp) "in_app" else "external_browser"
        )
    )
    if (!openedInApp) {
        onLaunchExternalUrl(url)
    }
}
