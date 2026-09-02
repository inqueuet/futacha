package com.valoser.futacha.shared.ui

import com.valoser.futacha.shared.model.CatalogFetchSettings
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.createRemoteBoardRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.service.AUTO_SAVE_DIRECTORY
import com.valoser.futacha.shared.service.HistoryRefresher
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.version.UpdateInfo
import com.valoser.futacha.shared.version.UpdatePromptStyle
import com.valoser.futacha.shared.version.VersionChecker
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

internal data class RepositoryHolder(
    val repository: BoardRepository,
    val ownsRepository: Boolean
)

internal data class FutachaRepositoryHolderInputs(
    val existingRepository: BoardRepository? = null,
    val httpClient: HttpClient?,
    val cookieRepository: com.valoser.futacha.shared.repository.CookieRepository?,
    val catalogFetchSettingsProvider: suspend () -> CatalogFetchSettings = { CatalogFetchSettings() },
    val createSharedRepository: (HttpClient, com.valoser.futacha.shared.repository.CookieRepository?) -> BoardRepository =
        { client, cookies ->
            createRemoteBoardRepository(
                httpClient = client,
                cookieRepository = cookies,
                catalogFetchSettingsProvider = catalogFetchSettingsProvider
            )
        },
    val createOwnedRepository: () -> BoardRepository = {
        createRemoteBoardRepository(catalogFetchSettingsProvider = catalogFetchSettingsProvider)
    }
)

internal fun buildCatalogFetchSettingsProvider(
    stateStore: AppStateStore
): suspend () -> CatalogFetchSettings = {
    CatalogFetchSettings(rows = stateStore.catalogFetchRows.first()).normalized()
}

internal data class FutachaAutoSavedThreadRepositoryInputs(
    val fileSystem: FileSystem?,
    val existingRepository: SavedThreadRepository?
)

internal data class FutachaHistoryRefresherInputs(
    val existingHistoryRefresher: HistoryRefresher? = null,
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
    return if (inputs.existingRepository != null) {
        RepositoryHolder(
            repository = inputs.existingRepository,
            ownsRepository = false
        )
    } else if (inputs.httpClient != null) {
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
    inputs.existingHistoryRefresher?.let { return it }
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

internal suspend fun fetchFutachaUpdateInfoIfEnabled(
    enabled: Boolean,
    versionChecker: VersionChecker?,
    onFailure: (Throwable) -> Unit = {}
): UpdateInfo? {
    val updateInfo = fetchFutachaUpdateInfo(versionChecker, onFailure)
    return updateInfo?.takeIf { enabled || it.promptStyle == UpdatePromptStyle.IMMEDIATE }
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
