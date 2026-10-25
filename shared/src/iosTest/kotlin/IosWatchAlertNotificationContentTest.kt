package com.valoser.futacha.shared

import com.valoser.futacha.shared.service.CatalogWatchAlertMatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * L119: an iOS watch notification named no thread, so a tap only brought the app forward. Android and Desktop open
 * the thread when the notification is for one match; iOS now carries it in userInfo for the app delegate's tap
 * handler, which hands it to the same path as a thread link.
 */
class IosWatchAlertNotificationContentTest {
    private fun match(
        threadId: String = "1474754356",
        boardUrl: String = "https://img.2chan.net/b/",
        title: String = "タイトル"
    ) = CatalogWatchAlertMatch(threadId, "b", "二次元裏", boardUrl, title, "", 3, 10L)

    @Test
    fun oneMatchNamesItsThreadSoATapOpensIt() {
        val content = buildIosWatchAlertNotificationContent(listOf(match()))
        val thread = "https://img.2chan.net/b/res/1474754356.htm"

        assertEquals("監視ワードに一致しました", content.title)
        assertEquals("二次元裏: タイトル", content.body)
        assertEquals(thread, content.userInfo[IOS_WATCH_ALERT_THREAD_URL_KEY])
        assertEquals(thread, iosNotificationThreadUrl(content.userInfo))
        // The link is accepted by the thread-link path the tap goes through.
        assertEquals(thread, normalizeIosThreadDeepLink(thread))
    }

    @Test
    fun aBoardPageUrlStillNamesTheThread() {
        val content = buildIosWatchAlertNotificationContent(
            listOf(match(threadId = "123", boardUrl = "https://may.2chan.net/b/futaba.php"))
        )
        assertEquals("https://may.2chan.net/b/res/123.htm", iosNotificationThreadUrl(content.userInfo))
    }

    @Test
    fun severalMatchesOrAThreadOutside2chanOnlyOpenTheApp() {
        val several = buildIosWatchAlertNotificationContent(listOf(match(), match(threadId = "1474754357", title = "別")))
        assertEquals("監視ワードに 2 件一致しました", several.title)
        assertEquals("二次元裏: タイトル ほか", several.body)
        assertNull(iosNotificationThreadUrl(several.userInfo))

        val elsewhere = buildIosWatchAlertNotificationContent(listOf(match(boardUrl = "https://example.com/b/")))
        assertNull(iosNotificationThreadUrl(elsewhere.userInfo))
        val noNumber = buildIosWatchAlertNotificationContent(listOf(match(threadId = "abc")))
        assertNull(iosNotificationThreadUrl(noNumber.userInfo))
    }

    @Test
    fun aNotificationWithoutAThreadOpensNothing() {
        assertFalse(openIosNotificationThread(emptyMap<Any?, Any?>()))
        assertFalse(openIosNotificationThread(mapOf<Any?, Any?>(IOS_WATCH_ALERT_THREAD_URL_KEY to "")))
        assertTrue(iosNotificationThreadUrl(mapOf<Any?, Any?>(IOS_WATCH_ALERT_THREAD_URL_KEY to "x")) == "x")
    }
}
