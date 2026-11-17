package com.valoser.futacha.shared.ui.futaber

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 監査M13：送信中に書き込み画面が外れても、送信は最後まで行われ、結果が知らされる。 */
class FutaberSendOutlivingScreenTest {
    @Test
    fun aSendGoesOnAfterTheScreenIsGoneAndItsResultIsTold() = runBlocking<Unit> {
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val release = CompletableDeferred<Unit>()
        val sent = CompletableDeferred<String>()
        val started = CompletableDeferred<Unit>()
        var open = true
        // 書き込み画面のスコープ：送信を待っている間に外れる（取り消される）。
        val screen = launch(Dispatchers.Default) {
            futaberSendOutlivingScreen(
                scope = appScope,
                send = { started.complete(Unit); release.await(); Result.success("123") },
                screenStillOpen = { open },
                onSentAway = { sent.complete(it) },
                onFailedAway = { sent.complete("failed: ${it.message}") }
            )
        }
        withTimeout(5_000) { started.await() }
        open = false
        screen.cancelAndJoin()
        release.complete(Unit)
        assertEquals("123", withTimeout(5_000) { sent.await() })
        appScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    @Test
    fun aFailureAfterTheScreenIsGoneIsToldAndAThrowIsAFailure() = runBlocking<Unit> {
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val told = CompletableDeferred<Throwable>()
        val result = futaberSendOutlivingScreen<String>(
            scope = appScope,
            send = { throw IllegalStateException("規制中") },
            screenStillOpen = { false },
            onSentAway = {},
            onFailedAway = { told.complete(it) }
        )
        assertTrue(result.isFailure)
        assertEquals("規制中", withTimeout(5_000) { told.await() }.message)
        assertTrue(futaberPostFailedAwayNotice(told.await()).contains("規制中"))
        appScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    @Test
    fun whileTheScreenIsOpenItGetsTheResultAndNothingElseIsTold() = runBlocking<Unit> {
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var told = 0
        val result = futaberSendOutlivingScreen(appScope, { Result.success("9") }, { true }, { told++ }, { told++ })
        assertEquals("9", result.getOrNull())
        assertEquals(0, told)
        appScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }
}
