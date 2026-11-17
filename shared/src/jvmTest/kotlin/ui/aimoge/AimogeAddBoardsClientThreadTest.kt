package com.valoser.futacha.shared.ui.aimoge

import com.valoser.futacha.shared.repo.aimoge.AimogeBoardRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

/** Audit 2026-11-11 L4: the board picker's HTTP client is built off the calling (UI) thread. */
class AimogeAddBoardsClientThreadTest {
    @Test
    fun theClientIsNotBuiltOnTheCallersThread() = runBlocking {
        val ui = Executors.newSingleThreadExecutor { Thread(it, "fake-main") }
        try {
            var builtOn: String? = null
            val repository = AimogeBoardRepository.owningClient({
                builtOn = Thread.currentThread().name
                HttpClient(MockEngine { respond("""{"boards":[]}""", HttpStatusCode.OK) })
            })
            withContext(ui.asCoroutineDispatcher()) { runCatching { loadAimogeBoardsOffMain(repository) } }
            repository.close()
            assertNotNull(builtOn)
            assertNotEquals("fake-main", builtOn)
        } finally {
            ui.shutdown()
        }
    }
}
