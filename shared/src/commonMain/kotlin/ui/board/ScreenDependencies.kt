package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.util.FileSystem
import io.ktor.client.HttpClient

data class BoardManagementScreenDependencies(
    val cookieRepository: CookieRepository? = null,
    val fileSystem: FileSystem? = null,
    val autoSavedThreadRepository: SavedThreadRepository? = null
)

data class CatalogScreenDependencies(
    val repository: BoardRepository? = null,
    val stateStore: AppStateStore? = null,
    val autoSavedThreadRepository: SavedThreadRepository? = null,
    val cookieRepository: CookieRepository? = null,
    val fileSystem: FileSystem? = null,
    val httpClient: HttpClient? = null
)

data class ThreadScreenDependencies(
    val repository: BoardRepository? = null,
    val httpClient: HttpClient? = null,
    val fileSystem: FileSystem? = null,
    val cookieRepository: CookieRepository? = null,
    val stateStore: AppStateStore? = null,
    val autoSavedThreadRepository: SavedThreadRepository? = null
)
