package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.*
import com.valoser.futacha.shared.ai.*

/** An injected service is owned and closed by its provider. */
val LocalFutachaAiService = staticCompositionLocalOf<OnDeviceAiService?> { null }

@Composable
internal fun rememberSelectedAiService(context: Any?): OnDeviceAiService {
    LocalFutachaAiService.current?.let { return it }
    val store = remember(context) { getAiConnectionStore(context) }
    val connection by store.state.collectAsState()
    LaunchedEffect(store) { store.load() }
    // The revision changes only with the effective connection; key renames/reordering are read
    // live by the store and must not recreate (and cancel) the services.
    val service = remember(context, connection.revision) {
        val local by lazy { createOnDeviceAiService(context) }
        val openAi by lazy { OpenAiService(store, connection) }
        fun resolve(provider: AiProvider): OnDeviceAiService = when (provider) {
            AiProvider.DEVICE -> local
            AiProvider.OPENAI -> openAi
            AiProvider.BOTH -> HybridModerationService(local, openAi)
        }
        RoutedAiService(local, resolve(connection.moderationProvider), "DEVICE:${connection.moderationProvider}:${connection.revision}")
    }
    DisposableEffect(service) { onDispose { service.close() } }
    return service
}
