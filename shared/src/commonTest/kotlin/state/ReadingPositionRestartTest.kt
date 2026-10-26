package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.*

class ReadingPositionRestartTest {
    @Test fun firstMiddleAndLastPositionsSurviveRecreatingBothStateAndHistoryStores(): Unit = runBlocking {
        val storage = FakePlatformStateStorage()
        val files = InMemoryFileSystem()
        val json = Json { ignoreUnknownKeys = true }
        for ((index, id, offset) in listOf(Triple(0, "100", 0), Triple(61, "160", 37), Triple(114, "213", 235))) {
            val store = AppStateStore(storage, AppStateHistoryFileStore(files, json, "write"))
            store.setHistory(listOf(ThreadHistoryEntry("100", "b", "fixture", "", "board", "https://may.2chan.net/b/res/100.htm",
                lastVisitedEpochMillis = 1, replyCount = 120)))
            store.updateHistoryScrollPositionImmediately(AppStateHistoryScrollUpdateRequest(
                threadId = "100", boardId = "b", index = index, offset = offset, postId = id,
                title = "fixture", titleImageUrl = "", boardName = "board", boardUrl = "https://may.2chan.net/b/res/100.htm", replyCount = 120))
            store.close()
            val restarted = AppStateStore(storage, AppStateHistoryFileStore(files, json, "restart"))
            try {
                val restored = restarted.readHistory().single()
                assertEquals(index, restored.lastReadItemIndex)
                assertEquals(id, restored.lastReadPostId)
                assertEquals(offset, restored.lastReadItemOffset)
            } finally { restarted.close() }
        }
    }
}
