package com.valoser.futacha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchAlertThreadUrlTest {
    @Test
    fun boardPageAndDirectoryUrlsBothResolveToTheThreadPage() {
        assertEquals(
            "https://may.2chan.net/b/res/123456.htm",
            watchAlertThreadUrl("https://may.2chan.net/b/futaba.php", "123456")
        )
        assertEquals(
            "https://may.2chan.net/b/res/123456.htm",
            watchAlertThreadUrl("https://may.2chan.net/b/", "123456")
        )
        assertEquals(
            "https://img.2chan.net/b/res/9.htm",
            watchAlertThreadUrl("https://img.2chan.net/b?mode=cat#x", "9")
        )
    }

    @Test
    fun untrustedOrMalformedInputsProduceNoLink() {
        assertNull(watchAlertThreadUrl("https://example.com/b/", "123"))
        assertNull(watchAlertThreadUrl("https://2chan.net.evil.example/b/", "123"))
        assertNull(watchAlertThreadUrl("https://may.2chan.net/", "123"))
        assertNull(watchAlertThreadUrl("may.2chan.net/b/", "123"))
        assertNull(watchAlertThreadUrl("https://may.2chan.net/b/", "12a"))
        assertNull(watchAlertThreadUrl("https://may.2chan.net/b/", ""))
    }
}
