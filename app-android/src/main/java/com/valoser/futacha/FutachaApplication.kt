package com.valoser.futacha

import android.app.Application
import com.valoser.futacha.shared.network.HttpBoardApi
import com.valoser.futacha.shared.network.createHttpClient
import com.valoser.futacha.shared.parser.createHtmlParser
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.DefaultBoardRepository
import com.valoser.futacha.shared.service.HistoryRefresher
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.createAppStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class FutachaApplication : Application() {
    lateinit var appStateStore: AppStateStore
        private set
    lateinit var httpClient: io.ktor.client.HttpClient
        private set
    lateinit var boardRepository: BoardRepository
        private set
    lateinit var historyRefresher: HistoryRefresher
        private set

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appStateStore = createAppStateStore(applicationContext)
        httpClient = createHttpClient()
        boardRepository = DefaultBoardRepository(
            api = HttpBoardApi(httpClient),
            parser = createHtmlParser()
        )
        historyRefresher = HistoryRefresher(
            stateStore = appStateStore,
            repository = boardRepository,
            dispatcher = Dispatchers.IO
        )
    }

    override fun onTerminate() {
        // This is only called in emulators, but close resources defensively
        boardRepository.close()
        httpClient.close()
        super.onTerminate()
    }
}
