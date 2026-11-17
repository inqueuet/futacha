package com.valoser.futacha.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
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
    sharedRepository: BoardRepository?,
    sharedHistoryRefresher: HistoryRefresher?,
    fileSystem: FileSystem?,
    cookieRepository: CookieRepository?,
    autoSavedThreadRepository: SavedThreadRepository?,
    shouldUseLightweightMode: Boolean,
    onRepositoryCloseFailure: (Throwable) -> Unit,
    onHistoryRefresherCloseFailure: (Throwable) -> Unit = {}
): FutachaCoreRuntimeState {
    val catalogFetchSettingsProvider = remember(stateStore) {
        buildCatalogFetchSettingsProvider(stateStore)
    }
    val repositoryHolder = remember(sharedRepository, httpClient, cookieRepository, catalogFetchSettingsProvider) {
        buildFutachaRepositoryHolder(
            FutachaRepositoryHolderInputs(
                existingRepository = sharedRepository,
                httpClient = httpClient,
                cookieRepository = cookieRepository,
                catalogFetchSettingsProvider = catalogFetchSettingsProvider,
                // The Android host may render the first screen while its Ktor
                // graph is still being created on Dispatchers.IO.  Do not let
                // the null-client branch construct a second HttpClient from
                // Compose/Main; the holder will be rebuilt when the shared
                // client becomes available.  A failing fake also makes an
                // accidental network action during this short window visible
                // instead of silently serving fixture data.
                createOwnedRepository = {
                    FakeBoardRepository {
                        error("通信機能を初期化中です")
                    }
                }
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
        sharedHistoryRefresher,
        effectiveAutoSavedThreadRepository,
        httpClient,
        fileSystem,
        shouldUseLightweightMode
    ) {
        buildFutachaHistoryRefresher(
            FutachaHistoryRefresherInputs(
                existingHistoryRefresher = sharedHistoryRefresher,
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
