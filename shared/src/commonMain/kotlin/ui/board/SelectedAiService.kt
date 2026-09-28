package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.*
import com.valoser.futacha.shared.ai.*

@Composable
internal fun rememberSelectedAiService(context: Any?): OnDeviceAiService {
    val store = remember(context) { getAiConnectionStore(context) }
    val connection by store.state.collectAsState()
    LaunchedEffect(store) { store.load() }
    val service = remember(context, connection) {
        val local by lazy { createOnDeviceAiService(context) }
        val openAi by lazy { OpenAiService(store, connection) }
        fun resolve(provider: AiProvider): OnDeviceAiService = when (provider) {
            AiProvider.DEVICE -> local
            AiProvider.OPENAI -> openAi
        }
        RoutedAiService(local, resolve(connection.moderationProvider), "DEVICE:${connection.moderationProvider}:${connection.revision}")
    }
    DisposableEffect(service) { onDispose { service.close() } }
    return service
}
