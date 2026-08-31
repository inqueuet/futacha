package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.util.FileSystem
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope

interface ScreenServiceDependenciesOwner {
    val services: ScreenServiceDependencies
}

val ScreenServiceDependenciesOwner.stateStore: AppStateStore?
    get() = services.stateStore

val ScreenServiceDependenciesOwner.autoSavedThreadRepository: SavedThreadRepository?
    get() = services.autoSavedThreadRepository

val ScreenServiceDependenciesOwner.cookieRepository: CookieRepository?
    get() = services.cookieRepository

val ScreenServiceDependenciesOwner.fileSystem: FileSystem?
    get() = services.fileSystem

val ScreenServiceDependenciesOwner.httpClient: HttpClient?
    get() = services.httpClient

val ScreenServiceDependenciesOwner.compatibilityStore: CompatibilityStore?
    get() = services.compatibilityStore

data class ScreenServiceDependencies(
    val stateStore: AppStateStore? = null,
    val autoSavedThreadRepository: SavedThreadRepository? = null,
    val cookieRepository: CookieRepository? = null,
    val fileSystem: FileSystem? = null,
    val httpClient: HttpClient? = null,
    val compatibilityStore: CompatibilityStore? = null
)

data class BoardManagementScreenDependencies(
    override val services: ScreenServiceDependencies = ScreenServiceDependencies()
) : ScreenServiceDependenciesOwner

data class CatalogScreenDependencies(
    val repository: BoardRepository? = null,
    val resolveCatalogHeadMetadata: Boolean = true,
    override val services: ScreenServiceDependencies = ScreenServiceDependencies()
) : ScreenServiceDependenciesOwner

data class ThreadScreenDependencies(
    val repository: BoardRepository? = null,
    val longRunningScope: CoroutineScope? = null,
    override val services: ScreenServiceDependencies = ScreenServiceDependencies()
) : ScreenServiceDependenciesOwner

internal fun BoardManagementScreenDependencies.withOverrides(
    cookieRepository: CookieRepository? = this.cookieRepository,
    fileSystem: FileSystem? = this.fileSystem,
    autoSavedThreadRepository: SavedThreadRepository? = this.autoSavedThreadRepository,
    compatibilityStore: CompatibilityStore? = this.compatibilityStore
): BoardManagementScreenDependencies {
    return copy(
        services = services.copy(
            cookieRepository = cookieRepository,
            fileSystem = fileSystem,
            autoSavedThreadRepository = autoSavedThreadRepository,
            compatibilityStore = compatibilityStore
        )
    )
}

internal fun CatalogScreenDependencies.withOverrides(
    repository: BoardRepository? = this.repository,
    stateStore: AppStateStore? = this.stateStore,
    autoSavedThreadRepository: SavedThreadRepository? = this.autoSavedThreadRepository,
    cookieRepository: CookieRepository? = this.cookieRepository,
    fileSystem: FileSystem? = this.fileSystem,
    httpClient: HttpClient? = this.httpClient,
    compatibilityStore: CompatibilityStore? = this.compatibilityStore
): CatalogScreenDependencies {
    return copy(
        repository = repository,
        services = services.copy(
            stateStore = stateStore,
            autoSavedThreadRepository = autoSavedThreadRepository,
            cookieRepository = cookieRepository,
            fileSystem = fileSystem,
            httpClient = httpClient,
            compatibilityStore = compatibilityStore
        )
    )
}

internal fun ThreadScreenDependencies.withOverrides(
    repository: BoardRepository? = this.repository,
    httpClient: HttpClient? = this.httpClient,
    fileSystem: FileSystem? = this.fileSystem,
    cookieRepository: CookieRepository? = this.cookieRepository,
    stateStore: AppStateStore? = this.stateStore,
    autoSavedThreadRepository: SavedThreadRepository? = this.autoSavedThreadRepository,
    compatibilityStore: CompatibilityStore? = this.compatibilityStore
): ThreadScreenDependencies {
    return copy(
        repository = repository,
        services = services.copy(
            stateStore = stateStore,
            autoSavedThreadRepository = autoSavedThreadRepository,
            cookieRepository = cookieRepository,
            fileSystem = fileSystem,
            httpClient = httpClient,
            compatibilityStore = compatibilityStore
        )
    )
}
