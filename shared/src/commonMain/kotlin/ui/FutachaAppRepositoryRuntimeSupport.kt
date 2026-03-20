package com.valoser.futacha.shared.ui

import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.createRemoteBoardRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.service.AUTO_SAVE_DIRECTORY
import com.valoser.futacha.shared.service.HistoryRefresher
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.version.UpdateInfo
import com.valoser.futacha.shared.version.VersionChecker
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException

internal data class RepositoryHolder(
    val repository: BoardRepository,
    val ownsRepository: Boolean
)

internal data class FutachaRepositoryHolderInputs(
    val httpClient: HttpClient?,
    val cookieRepository: com.valoser.futacha.shared.repository.CookieRepository?,
    val createSharedRepository: (HttpClient, com.valoser.futacha.shared.repository.CookieRepository?) -> BoardRepository =
        { client, cookies -> createRemoteBoardRepository(client, cookieRepository = cookies) },
    val createOwnedRepository: () -> BoardRepository = { createRemoteBoardRepository() }
)

internal data class FutachaAutoSavedThreadRepositoryInputs(
    val fileSystem: FileSystem?,
    val existingRepository: SavedThreadRepository?
)

internal data class FutachaHistoryRefresherInputs(
    val stateStore: AppStateStore,
    val repository: BoardRepository,
    val autoSavedThreadRepository: SavedThreadRepository?,
    val httpClient: HttpClient?,
    val fileSystem: FileSystem?,
    val shouldUseLightweightMode: Boolean
)

internal fun buildFutachaRepositoryHolder(
    inputs: FutachaRepositoryHolderInputs
): RepositoryHolder {
    return if (inputs.httpClient != null) {
        RepositoryHolder(
            repository = inputs.createSharedRepository(inputs.httpClient, inputs.cookieRepository),
            ownsRepository = false
        )
    } else {
        RepositoryHolder(
            repository = inputs.createOwnedRepository(),
            ownsRepository = true
        )
    }
}

internal fun buildFutachaAutoSavedThreadRepository(
    inputs: FutachaAutoSavedThreadRepositoryInputs
): SavedThreadRepository? {
    return inputs.existingRepository ?: inputs.fileSystem?.let {
        SavedThreadRepository(it, baseDirectory = AUTO_SAVE_DIRECTORY)
    }
}

internal fun buildFutachaHistoryRefresher(
    inputs: FutachaHistoryRefresherInputs
): HistoryRefresher {
    return HistoryRefresher(
        stateStore = inputs.stateStore,
        repository = inputs.repository,
        dispatcher = AppDispatchers.io,
        autoSavedThreadRepository = inputs.autoSavedThreadRepository,
        httpClient = inputs.httpClient,
        fileSystem = inputs.fileSystem,
        maxConcurrency = if (inputs.shouldUseLightweightMode) 2 else 4
    )
}

internal suspend fun fetchFutachaUpdateInfo(
    versionChecker: VersionChecker?,
    onFailure: (Throwable) -> Unit = {}
): UpdateInfo? {
    if (versionChecker == null) {
        return null
    }
    return try {
        versionChecker.checkForUpdate()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onFailure(e)
        null
    }
}

internal fun closeOwnedFutachaRepository(
    repositoryHolder: RepositoryHolder,
    onCloseFailure: (Throwable) -> Unit
) {
    if (!repositoryHolder.ownsRepository) {
        return
    }
    runCatching {
        repositoryHolder.repository.closeAsync().invokeOnCompletion { error ->
            if (error != null && error !is CancellationException) {
                onCloseFailure(error)
            }
        }
    }.onFailure(onCloseFailure)
}
