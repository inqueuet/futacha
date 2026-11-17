package com.valoser.futacha.shared.ui.board

import androidx.compose.ui.graphics.Color
import com.valoser.futacha.shared.network.aimoge.AimogePollDto
import com.valoser.futacha.shared.network.aimoge.AimogePollOptionDto
import com.valoser.futacha.shared.network.aimoge.aimogeMessageHtml
import com.valoser.futacha.shared.network.aimoge.aimogePollHtml
import com.valoser.futacha.shared.compat.toCompatPlainTextCached
import kotlin.test.Test
import kotlin.test.assertEquals

/** Thread 01a1cd5d #74: in ふたちゃ the poll options looked like plain text and could not be pressed. */
class FutachaPollOptionLinkTest {
    @Test fun eachOptionIsALinkToItsVoteAndTheSameWordInTheBodyIsNot() {
        val now = kotlin.time.Instant.parse("2026-11-12T00:00:00Z")
        val poll = AimogePollDto(closesAt = "2026-11-13T00:00:00Z",
            options = listOf(AimogePollOptionDto("a", "はい"), AimogePollOptionDto("b", "犬<猫")))
        val html = aimogeMessageHtml("はいかいいえ") + aimogePollHtml(poll, postId = "p", now = now)
        val text = buildAnnotatedMessageBase(html, emptyList(), Color.Green, Color.Blue)
        val links = text.getStringAnnotations(URL_ANNOTATION_TAG, 0, text.length)
            .map { text.text.substring(it.start, it.end) to it.item }
        assertEquals(listOf(
            "はい" to "https://nijiurachan.net/#futacha-poll/p/a",
            "犬<猫" to "https://nijiurachan.net/#futacha-poll/p/b"
        ), links)
        assertEquals(true, text.text.indexOf("はい") == 0)
    }
}

/** The same for としあき(仮)・ふたばー風, which read `<a>` but took the first same word in the body. */
class CompatPollOptionLinkTest {
    @Test fun theOptionLinksAreOnTheOptionLines() {
        val now = kotlin.time.Instant.parse("2026-11-12T00:00:00Z")
        val poll = AimogePollDto(closesAt = "2026-11-13T00:00:00Z",
            options = listOf(AimogePollOptionDto("a", "はい"), AimogePollOptionDto("b", "いいえ")))
        val html = aimogeMessageHtml("はいかいいえ") + aimogePollHtml(poll, postId = "p", now = now)
        val text = html.toCompatPlainTextCached()
        val links = com.valoser.futacha.shared.compat.compatInlineLinks(html).map { text.substring(it.start, it.endExclusive) to it.url }
        assertEquals(listOf("はい" to "https://nijiurachan.net/#futacha-poll/p/a", "いいえ" to "https://nijiurachan.net/#futacha-poll/p/b"), links)
    }
}
