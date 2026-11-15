package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.AimogeAvailability
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Thread 01a1cd5d: a thread started on あいもげ in としあき(仮) opened 「No.0」 instead of itself. */
class CompatCreatedThreadTest {
    @BeforeTest fun on() { AimogeAvailability.sitesEnabled = true }
    @AfterTest fun off() { AimogeAvailability.sitesEnabled = false }

    @Test fun anAimogeThreadOpensItsUuid() {
        val uuid = "019A1CD5-1F35-7211-95FD-169BC7E91C39"
        assertEquals(
            uuid.lowercase() to "https://nijiurachan.net/b/aimg/thread/${uuid.lowercase()}",
            compatCreatedThreadOf("https://nijiurachan.net/b/aimg/", uuid)
        )
        assertNull(compatCreatedThreadOf("https://nijiurachan.net/b/aimg/", "12345"))
    }

    @Test fun aFutabaThreadStillOpensItsNumber() {
        assertEquals(
            "1234567" to "https://may.2chan.net/b/res/1234567.htm",
            compatCreatedThreadOf("https://may.2chan.net/b/", "res/1234567.htm")
        )
        assertNull(compatCreatedThreadOf("https://may.2chan.net/b/", null))
    }
}
