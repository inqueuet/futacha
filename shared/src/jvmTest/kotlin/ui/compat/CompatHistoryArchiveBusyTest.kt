package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.mutableStateOf
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.BaseInMemoryPlatformStateStorage
import com.valoser.futacha.shared.ui.board.HistoryViewSettings
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.JvmFileSystem
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** L60: one history archive (export or import) at a time in としあき(仮)'s history tools, told as it starts and ends. */
class CompatHistoryArchiveBusyTest {
    @Test fun aSecondArchiveWhileOneRunsIsToldNotStarted() = runBlocking {
        val root = Files.createTempDirectory("l60-archive").toFile()
        val real = JvmFileSystem(root)
        val gate = CompletableDeferred<Unit>()
        val writes = AtomicInteger()
        val fs = object : FileSystem by real {
            override suspend fun createDirectory(path: String): Result<Unit> { held(); return real.createDirectory(path) }
            override suspend fun writeString(path: String, content: String): Result<Unit> { held(); return real.writeString(path, content) }
            override suspend fun writeBytes(path: String, bytes: ByteArray): Result<Unit> { held(); return real.writeBytes(path, bytes) }
            private suspend fun held() { writes.incrementAndGet(); gate.await() }
        }
        val state = AppStateStore(BaseInMemoryPlatformStateStorage())
        state.setHistory(listOf(ThreadHistoryEntry(threadId = "60", boardId = "may", title = "L60", titleImageUrl = "",
            boardName = "may", boardUrl = "https://may.2chan.net/b/res/60.htm", lastVisitedEpochMillis = 1L, replyCount = 1)))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val messages = CopyOnWriteArrayList<String>()
        try {
            val tools = CompatHistoryTools(mutableStateOf(HistoryViewSettings()), scope, state)
            tools.fileSystem = fs
            tools.onMessage = { messages += it }

            tools.exportAll()
            withTimeout(5_000) { while (writes.get() == 0) delay(10) }
            val writesWhileHeld = writes.get()
            tools.importAll()
            tools.exportAll()
            delay(300)
            assertEquals(writesWhileHeld, writes.get(), "Nothing else ran")
            assertEquals(2, messages.count { "実行中" in it }, "Both were told as busy: $messages")

            gate.complete(Unit)
            withTimeout(5_000) { while (messages.none { it.startsWith("履歴アーカイブを作成しました") }) delay(10) }
            assertTrue(messages.first().contains("エクスポート"), "The start was told: $messages")
        } finally {
            gate.complete(Unit)
            scope.cancel()
            state.close()
            root.deleteRecursively()
        }
    }
}
