package com.valoser.futacha.shared.ui

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.createRemoteBoardRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.service.AUTO_SAVE_DIRECTORY
import com.valoser.futacha.shared.service.DEFAULT_MANUAL_SAVE_ROOT
import com.valoser.futacha.shared.service.HistoryRefresher
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.version.UpdateInfo
import com.valoser.futacha.shared.version.VersionChecker
import io.ktor.client.HttpClient
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.encodedPath
import kotlinx.coroutines.CancellationException

private const val FUTACHA_APP_RUNTIME_LOG_TAG = "FutachaApp"

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

internal data class FutachaSavedThreadsRepositories(
    val currentRepository: SavedThreadRepository?,
    val legacyRepository: SavedThreadRepository?
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

internal data class FutachaSavedThreadsRepositoryInputs(
    val fileSystem: FileSystem?,
    val manualSaveDirectory: String,
    val manualSaveLocation: SaveLocation
)

internal data class FutachaActiveSavedThreadsRepositoryInputs(
    val currentRepository: SavedThreadRepository?,
    val legacyRepository: SavedThreadRepository?
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

internal fun resolveFutachaBoardRepository(
    board: BoardSummary,
    sharedRepository: BoardRepository
): BoardRepository? {
    return board.takeUnless { it.isMockBoard() }?.let { sharedRepository }
}

internal fun buildFutachaSavedThreadsRepositories(
    inputs: FutachaSavedThreadsRepositoryInputs
): FutachaSavedThreadsRepositories {
    val currentRepository = inputs.fileSystem?.let { fs ->
        SavedThreadRepository(
            fs,
            baseDirectory = inputs.manualSaveDirectory,
            baseSaveLocation = inputs.manualSaveLocation
        )
    }
    val shouldUseLegacyFallback = inputs.fileSystem != null &&
        !(inputs.manualSaveLocation is SaveLocation.Path &&
            isDefaultManualSaveRoot(inputs.manualSaveDirectory))
    val legacyRepository = if (!shouldUseLegacyFallback) {
        null
    } else {
        SavedThreadRepository(
            inputs.fileSystem,
            baseDirectory = DEFAULT_MANUAL_SAVE_ROOT,
            baseSaveLocation = SaveLocation.Path(DEFAULT_MANUAL_SAVE_ROOT)
        )
    }
    return FutachaSavedThreadsRepositories(
        currentRepository = currentRepository,
        legacyRepository = legacyRepository
    )
}

internal suspend fun shouldResetInaccessibleManualSaveBookmark(
    fileSystem: FileSystem?,
    manualSaveLocation: SaveLocation
): Boolean {
    if (manualSaveLocation !is SaveLocation.Bookmark || fileSystem == null) {
        return false
    }
    return !runCatching { fileSystem.exists(manualSaveLocation, "") }.getOrDefault(false)
}

internal fun resolveFutachaManualSaveDirectoryDisplay(
    fileSystem: FileSystem?,
    manualSaveDirectory: String,
    manualSaveLocation: SaveLocation
): String? {
    return when (manualSaveLocation) {
        is SaveLocation.TreeUri -> "SAF: ${manualSaveLocation.uri}"
        is SaveLocation.Bookmark -> "Bookmark: 保存先が選択済みです"
        is SaveLocation.Path -> {
            runCatching { fileSystem?.resolveAbsolutePath(manualSaveDirectory) }.getOrNull()
        }
    }
}

internal fun selectPreferredSavedThreadsRepository(
    currentRepository: SavedThreadRepository?,
    legacyRepository: SavedThreadRepository?,
    currentCount: Int,
    legacyCount: Int
): SavedThreadRepository? {
    if (currentRepository == null) {
        return legacyRepository
    }
    if (legacyRepository == null) {
        return currentRepository
    }
    return when {
        currentCount <= 0 && legacyCount > 0 -> legacyRepository
        legacyCount > currentCount -> legacyRepository
        else -> currentRepository
    }
}

internal suspend fun resolveActiveSavedThreadsRepository(
    inputs: FutachaActiveSavedThreadsRepositoryInputs
): SavedThreadRepository? {
    if (inputs.currentRepository == null) {
        return inputs.legacyRepository
    }
    val currentCount = runCatching { inputs.currentRepository.getThreadCount() }.getOrDefault(0)
    val legacyCount = inputs.legacyRepository?.let {
        runCatching { it.getThreadCount() }.getOrDefault(0)
    } ?: 0
    return selectPreferredSavedThreadsRepository(
        currentRepository = inputs.currentRepository,
        legacyRepository = inputs.legacyRepository,
        currentCount = currentCount,
        legacyCount = legacyCount
    )
}

internal fun isDefaultManualSaveRoot(directory: String): Boolean {
    val normalized = directory.trim().removePrefix("./").trimEnd('/')
    return normalized.equals(DEFAULT_MANUAL_SAVE_ROOT, ignoreCase = true)
}

internal fun BoardSummary.isMockBoard(): Boolean {
    return url.contains("example.com", ignoreCase = true)
}

internal fun normalizeBoardUrl(raw: String): String {
    val trimmed = raw.trim()
    val withScheme = when {
        trimmed.startsWith("https://", ignoreCase = true) -> trimmed
        trimmed.startsWith("http://", ignoreCase = true) -> {
            Logger.w(
                FUTACHA_APP_RUNTIME_LOG_TAG,
                "HTTP URL detected. Connection may fail if cleartext traffic is disabled: $trimmed"
            )
            trimmed
        }
        else -> "https://$trimmed"
    }

    if (withScheme.contains("futaba.php", ignoreCase = true)) {
        return withScheme
    }

    return runCatching {
        val parsed = Url(withScheme)
        val normalizedPath = when {
            parsed.encodedPath.isBlank() || parsed.encodedPath == "/" -> "/futaba.php"
            parsed.encodedPath.endsWith("/") -> "${parsed.encodedPath}futaba.php"
            else -> "${parsed.encodedPath}/futaba.php"
        }
        URLBuilder(parsed).apply { encodedPath = normalizedPath }.buildString()
    }.getOrElse {
        val fragment = withScheme.substringAfter('#', missingDelimiterValue = "")
        val withoutFragment = withScheme.substringBefore('#')
        val base = withoutFragment.substringBefore('?').trimEnd('/')
        val query = withoutFragment.substringAfter('?', missingDelimiterValue = "")
        buildString {
            append(base)
            append("/futaba.php")
            if (query.isNotEmpty()) {
                append('?')
                append(query)
            }
            if (fragment.isNotEmpty()) {
                append('#')
                append(fragment)
            }
        }
    }
}
