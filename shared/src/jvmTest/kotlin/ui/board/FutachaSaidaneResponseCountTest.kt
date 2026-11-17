package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.Post
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class FutachaSaidaneResponseCountTest {
    private fun labelAfter(response: Any?, fail: Boolean = false): String {
        var label = "そうだねx2"
        val handlers = buildThreadScreenPostActionHandlers(ThreadScreenPostActionInputs(
            currentOverlayState = { ThreadPostOverlayState() },
            setOverlayState = {}, lastUsedDeleteKey = "",
            currentSaidaneLabel = { label }, isSelfPost = { false },
            onShowOptionalMessage = {}, onSaidaneLabelUpdated = { _, next -> label = next },
            launchUnitAction = { _, _, success, block ->
                if (runCatching { runBlocking { block() } }.isSuccess) success()
            },
            voteSaidane = { if (fail) error("network failed") else response }, requestDeletion = {}
        ))
        handlers.onSaidane(Post(id = "1", author = null, subject = null, timestamp = "t", messageHtml = "body",
            imageUrl = null, thumbnailUrl = null, saidaneLabel = label))
        return label
    }

    @Test fun returnedCountIncludesOtherVotesAndCanCorrectAnOvercount() {
        assertEquals("そうだねx7", labelAfter("7\n"))
        assertEquals("そうだねx1", labelAfter("1"))
        assertEquals("そうだねx0", labelAfter("0"))
    }

    @Test fun aSiteWithoutACountKeepsItsExistingSuccessfulVoteBehavior() {
        assertEquals("そうだねx3", labelAfter(""))
        assertEquals("そうだねx3", labelAfter(Unit))
    }

    @Test fun aFailureNeverChangesTheCount() {
        assertEquals("そうだねx2", labelAfter("7", fail = true))
    }
}
