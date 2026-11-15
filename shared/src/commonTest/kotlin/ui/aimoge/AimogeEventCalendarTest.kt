package com.valoser.futacha.shared.ui.aimoge

import com.valoser.futacha.shared.compat.AimogeAvailability
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AimogeEventCalendarTest {
    @BeforeTest fun on() { AimogeAvailability.sitesEnabled = true }
    @AfterTest fun off() { AimogeAvailability.sitesEnabled = false }

    private val json = """
        {"events":[
          {"id":"e2","slug":"img","content":{"title":" 後の回 ","body":"b","durationMinutes":60},
           "attachments":[{"url":"https://x/a.png","thumbnailUrl":"https://x/t.png"}],
           "occurrences":[{"startsAt":"2026-11-20T12:00:00Z","state":"CREATED","count":3,"threadId":"0193abcd-0000-7000-8000-000000000001"}]},
          {"id":"e1","slug":"b","content":{"title":"先の回"},
           "occurrences":[{"startsAt":"2026-11-13T12:00:00Z","state":"SCHEDULED"},
                          {"startsAt":"2026-11-13T12:00:00Z","state":"SCHEDULED"},
                          {"startsAt":"bad","state":"SCHEDULED"}]},
          {"id":"","slug":"b","content":{"title":"idなし"},"occurrences":[{"startsAt":"2026-11-13T12:00:00Z"}]},
          {"id":"e3","slug":"../x","content":{"title":"変な板"},"occurrences":[{"startsAt":"2026-11-14T12:00:00Z"}]}
        ]}
    """.trimIndent()

    @Test
    fun rowsAreSortedOnePerOccurrenceAndTheBadOnesAreDropped() {
        val rows = parseAimogeCalendar(json)
        assertEquals(listOf("先の回", "後の回"), rows.map { it.title })
        assertNull(rows[0].threadUrl)
        assertEquals("https://nijiurachan.net/b/img/", rows[1].boardUrl)
        assertTrue(rows[1].threadUrl!!.startsWith("https://nijiurachan.net/b/img/"))
        assertEquals("https://x/t.png", rows[1].thumbnailUrl)
    }

    @Test
    fun rowKeysAreUniqueSoTheListCannotCrash() {
        // Before (audit 2026-11-12 M1): a repeated occurrence and an event without an id gave the same list key
        // and LazyColumn threw IllegalArgumentException.
        val rows = parseAimogeCalendar(json)
        val keys = rows.map { "${it.eventId}-${it.startsAtRaw}" }
        assertEquals(keys.size, keys.toSet().size)
        assertTrue(rows.none { it.eventId.isBlank() })
    }

    @Test
    fun anEmptyAnswerGivesNoRows() {
        assertEquals(emptyList(), parseAimogeCalendar("{}"))
    }
}
