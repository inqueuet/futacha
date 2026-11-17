package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.FakePlatformStateStorage
import io.ktor.client.HttpClient
import kotlin.test.Test
import kotlin.test.assertSame

class ScreenDependenciesTest {
    @Test
    fun boardManagementScreenDependencies_exposesSharedServices() {
        val fileSystem = InMemoryFileSystem()
        val savedThreadRepository = SavedThreadRepository(fileSystem)
        val dependencies = BoardManagementScreenDependencies(
            services = ScreenServiceDependencies(
                autoSavedThreadRepository = savedThreadRepository,
                fileSystem = fileSystem
            )
        )

        assertSame(savedThreadRepository, dependencies.autoSavedThreadRepository)
        assertSame(fileSystem, dependencies.fileSystem)
    }

    @Test
    fun catalogAndThreadScreenDependencies_exposeSharedServicesViaCommonAccessors() {
        val repository = FakeBoardRepository()
        val stateStore = AppStateStore(FakePlatformStateStorage())
        val fileSystem = InMemoryFileSystem()
        val savedThreadRepository = SavedThreadRepository(fileSystem)
        val httpClient = HttpClient()
        val services = ScreenServiceDependencies(
            stateStore = stateStore,
            autoSavedThreadRepository = savedThreadRepository,
            fileSystem = fileSystem,
            httpClient = httpClient
        )
        val catalogDependencies = CatalogScreenDependencies(
            repository = repository,
            services = services
        )
        val threadDependencies = ThreadScreenDependencies(
            repository = repository,
            services = services
        )

        assertSame(repository, catalogDependencies.repository)
        assertSame(stateStore, catalogDependencies.stateStore)
        assertSame(savedThreadRepository, catalogDependencies.autoSavedThreadRepository)
        assertSame(fileSystem, catalogDependencies.fileSystem)
        assertSame(httpClient, catalogDependencies.httpClient)
        assertSame(repository, threadDependencies.repository)
        assertSame(stateStore, threadDependencies.stateStore)
        assertSame(savedThreadRepository, threadDependencies.autoSavedThreadRepository)
        assertSame(fileSystem, threadDependencies.fileSystem)
        assertSame(httpClient, threadDependencies.httpClient)
        httpClient.close()
    }

    @Test
    fun dependencyOverrideHelpers_replaceOnlySpecifiedValues() {
        val originalFileSystem = InMemoryFileSystem()
        val originalSavedThreadRepository = SavedThreadRepository(originalFileSystem)
        val updatedFileSystem = InMemoryFileSystem()
        val updatedSavedThreadRepository = SavedThreadRepository(updatedFileSystem)
        val stateStore = AppStateStore(FakePlatformStateStorage())
        val updatedStateStore = AppStateStore(FakePlatformStateStorage())
        val originalHttpClient = HttpClient()
        val updatedHttpClient = HttpClient()
        val originalRepository = FakeBoardRepository()
        val updatedRepository = FakeBoardRepository()

        val boardManagement = BoardManagementScreenDependencies(
            services = ScreenServiceDependencies(
                autoSavedThreadRepository = originalSavedThreadRepository,
                fileSystem = originalFileSystem
            )
        ).withOverrides(fileSystem = updatedFileSystem)
        val catalog = CatalogScreenDependencies(
            repository = originalRepository,
            services = ScreenServiceDependencies(
                stateStore = stateStore,
                autoSavedThreadRepository = originalSavedThreadRepository,
                fileSystem = originalFileSystem,
                httpClient = originalHttpClient
            )
        ).withOverrides(
            repository = updatedRepository,
            stateStore = updatedStateStore,
            autoSavedThreadRepository = updatedSavedThreadRepository,
            httpClient = updatedHttpClient
        )
        val thread = ThreadScreenDependencies(
            repository = originalRepository,
            services = ScreenServiceDependencies(
                stateStore = stateStore,
                autoSavedThreadRepository = originalSavedThreadRepository,
                fileSystem = originalFileSystem,
                httpClient = originalHttpClient
            )
        ).withOverrides(
            repository = updatedRepository,
            fileSystem = updatedFileSystem
        )

        assertSame(updatedFileSystem, boardManagement.fileSystem)
        assertSame(originalSavedThreadRepository, boardManagement.autoSavedThreadRepository)
        assertSame(updatedRepository, catalog.repository)
        assertSame(updatedStateStore, catalog.stateStore)
        assertSame(updatedSavedThreadRepository, catalog.autoSavedThreadRepository)
        assertSame(originalFileSystem, catalog.fileSystem)
        assertSame(updatedHttpClient, catalog.httpClient)
        assertSame(updatedRepository, thread.repository)
        assertSame(updatedFileSystem, thread.fileSystem)
        assertSame(stateStore, thread.stateStore)
        assertSame(originalSavedThreadRepository, thread.autoSavedThreadRepository)
        assertSame(originalHttpClient, thread.httpClient)
        originalHttpClient.close()
        updatedHttpClient.close()
    }
}
