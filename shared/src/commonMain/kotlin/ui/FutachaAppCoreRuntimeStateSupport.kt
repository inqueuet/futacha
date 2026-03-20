package com.valoser.futacha.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.service.HistoryRefresher
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.util.FileSystem
import io.ktor.client.HttpClient

internal data class FutachaCoreRuntimeState(
    val repositoryHolder: RepositoryHolder,
    val effectiveAutoSavedThreadRepository: SavedThreadRepository?,
    val historyRefresher: HistoryRefresher
)

@Composable
internal fun rememberFutachaCoreRuntimeState(
    stateStore: AppStateStore,
    httpClient: HttpClient?,
    fileSystem: FileSystem?,
    cookieRepository: CookieRepository?,
    autoSavedThreadRepository: SavedThreadRepository?,
    shouldUseLightweightMode: Boolean,
    onRepositoryCloseFailure: (Throwable) -> Unit,
    onHistoryRefresherCloseFailure: (Throwable) -> Unit = {}
): FutachaCoreRuntimeState {
    val repositoryHolder = remember(httpClient, cookieRepository) {
        buildFutachaRepositoryHolder(
            FutachaRepositoryHolderInputs(
                httpClient = httpClient,
                cookieRepository = cookieRepository
            )
        )
    }
    val effectiveAutoSavedThreadRepository = remember(fileSystem, autoSavedThreadRepository) {
        buildFutachaAutoSavedThreadRepository(
            FutachaAutoSavedThreadRepositoryInputs(
                fileSystem = fileSystem,
                existingRepository = autoSavedThreadRepository
            )
        )
    }
    val historyRefresher = remember(
        repositoryHolder.repository,
        effectiveAutoSavedThreadRepository,
        httpClient,
        fileSystem,
        shouldUseLightweightMode
    ) {
        buildFutachaHistoryRefresher(
            FutachaHistoryRefresherInputs(
                stateStore = stateStore,
                repository = repositoryHolder.repository,
                autoSavedThreadRepository = effectiveAutoSavedThreadRepository,
                httpClient = httpClient,
                fileSystem = fileSystem,
                shouldUseLightweightMode = shouldUseLightweightMode
            )
        )
    }

    DisposableEffect(repositoryHolder) {
        onDispose {
            closeOwnedFutachaRepository(repositoryHolder, onRepositoryCloseFailure)
        }
    }

    DisposableEffect(historyRefresher) {
        onDispose {
            runCatching { historyRefresher.close() }.onFailure(onHistoryRefresherCloseFailure)
        }
    }

    return remember(
        repositoryHolder,
        effectiveAutoSavedThreadRepository,
        historyRefresher
    ) {
        FutachaCoreRuntimeState(
            repositoryHolder = repositoryHolder,
            effectiveAutoSavedThreadRepository = effectiveAutoSavedThreadRepository,
            historyRefresher = historyRefresher
        )
    }
}
