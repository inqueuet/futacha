package com.valoser.futacha.shared.compat.aimoge

import com.valoser.futacha.shared.compat.AimogeAvailability
import com.valoser.futacha.shared.network.aimoge.parseAimogePostSearchRows
import com.valoser.futacha.shared.network.aimogePostSearchSlug
import com.valoser.futacha.shared.network.futabaServerSearchBase
import com.valoser.futacha.shared.network.serverPostSearchTitle
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** あいもげのレス検索：4モードのカタログのメニューに、あいもげの板では「あいもげのレス検索」を出す。 */
class AimogePostSearchTest {
    @AfterTest fun off() { AimogeAvailability.sitesEnabled = false }

    @Test fun theMenuNamesTheSearchOfTheBoardsSite() {
        AimogeAvailability.sitesEnabled = true
        assertEquals("あいもげのレス検索", serverPostSearchTitle("https://nijiurachan.net/b/aimg/"))
        assertEquals("aimg", aimogePostSearchSlug("https://nijiurachan.net/b/aimg/"))
        assertEquals("ふたばのレス検索", serverPostSearchTitle("https://may.2chan.net/b/futaba.php"))
        // The Futaba search is never sent to あいもげ.
        assertNull(futabaServerSearchBase("https://nijiurachan.net/b/aimg/"))
    }

    @Test fun nothingIsOfferedForAimogeWhileItIsOff() {
        AimogeAvailability.sitesEnabled = false
        assertNull(serverPostSearchTitle("https://nijiurachan.net/b/aimg/"))
    }

    @Test fun eachPublicPostIsARowThatOpensItsThread() {
        val id = "01a1e622-5273-71fd-8269-92652012589c"
        val rows = parseAimogePostSearchRows(
            """{"posts":[{"threadId":"$id","seq":3,"status":"public","body":"あいう\nえお","attachment":{"thumbnailUrl":"https://storage.nijiurachan.net/t.webp"}},
               {"threadId":"$id","seq":4,"status":"deleted","body":"x"},{"threadId":"bad","seq":1,"body":"y"}]}""", "aimg")
        assertEquals(1, rows.size)
        assertEquals("https://nijiurachan.net/b/aimg/thread/$id", rows[0].threadUrl)
        assertEquals(3, rows[0].seq)
        assertEquals("https://storage.nijiurachan.net/t.webp", rows[0].thumbnailUrl)
    }
}
