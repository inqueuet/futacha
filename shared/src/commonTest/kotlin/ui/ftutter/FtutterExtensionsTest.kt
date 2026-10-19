package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.ui.futaber.FutaberSettingKeys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FtutterExtensionsTest {
    private fun post(id: String, body: String = "本文", author: String? = null) = Post(
        id = id, author = author, subject = null, timestamp = "", posterId = null, messageHtml = body, imageUrl = null, thumbnailUrl = null
    )
    private fun item(id: String, title: String) = CatalogItem(
        id = id, threadUrl = "https://may.2chan.net/b/res/$id.htm", title = title, thumbnailUrl = null, fullImageUrl = null, replyCount = 0
    )

    @Test
    fun historyToolsAreOnByDefaultAndExplicitOffIsRespected() {
        assertEquals(setOf(FtutterExtension.HISTORY_TOOLS), parseFtutterExtensions(emptyMap()).enabled)
        assertEquals(setOf(FtutterExtension.HISTORY_TOOLS), parseFtutterExtensions(FtutterExtension.entries.associate { it.key to "x" }).enabled)
        assertTrue(parseFtutterExtensions(mapOf(FtutterExtension.HISTORY_TOOLS.key to "false")).enabled.isEmpty())
        assertTrue(parseFtutterExtensions(FtutterExtension.entries.associate { it.key to "false" }).enabled.isEmpty())
    }

    @Test
    fun extensionKeysLiveUnderTheModePrefix() {
        FtutterExtension.entries.forEach { assertEquals("compat.ftutter.ext.${it.stored}", it.key) }
        assertEquals(FtutterExtension.entries.size, FtutterExtension.entries.map { it.key }.toSet().size)
        val on = parseFtutterExtensions(mapOf(FtutterExtension.READ_ALOUD.key to "true", FtutterExtension.AI.key to "true"))
        assertTrue(FtutterExtension.READ_ALOUD in on)
        assertTrue(FtutterExtension.AI in on)
        assertFalse(FtutterExtension.MHT in on)
    }

    @Test
    fun handwritingMustBeExplicitlyEnabled() {
        assertFalse(FtutterExtension.HANDWRITING in parseFtutterExtensions(emptyMap()))
        assertTrue(FtutterExtension.HANDWRITING in parseFtutterExtensions(mapOf("compat.ftutter.ext.handwriting" to "true")))
        assertFalse(FtutterExtension.HANDWRITING in parseFtutterExtensions(mapOf("compat.ftutter.ext.handwriting" to "false")))
    }

    @Test
    fun commonCatalogNgDropsTitlesAndListsThemAsNg() {
        val base = FtutterCatalogNgResult(listOf(item("1", "猫スレ"), item("2", "ＡＢＣスレ")), emptyMap(), listOf(item("9", "前から")))
        assertSame(base, ftutterApplyCommonCatalogNg(base, emptyList()))
        assertSame(base, ftutterApplyCommonCatalogNg(base, listOf("  ")))
        assertSame(base, ftutterApplyCommonCatalogNg(base, listOf("鳥")))
        // 全角半角を同一視する（検索と同じ比べ方）。
        val result = ftutterApplyCommonCatalogNg(base, listOf("abc"))
        assertEquals(listOf("1"), result.items.map { it.id })
        assertEquals(listOf("9", "2"), result.ngRemoved.map { it.id })
    }

    @Test
    fun commonNgPostsMatchWordsAndHeaders() {
        val posts = listOf(post("1", "スレ本文"), post("2", "うんこ"), post("3", "普通", author = "荒らし"), post("4", "普通"))
        assertEquals(emptySet(), ftutterCommonNgPosts(posts, emptyList(), emptyList()))
        assertEquals(setOf(1), ftutterCommonNgPosts(posts, listOf("うんこ"), emptyList()))
        assertEquals(setOf(2), ftutterCommonNgPosts(posts, emptyList(), listOf("荒らし")))
        assertEquals(setOf(1, 2), ftutterCommonNgPosts(posts, listOf("うんこ"), listOf("荒らし")))
    }

    @Test
    fun autoScrollUsesTheSharedValuesWithinRange() {
        assertEquals(5 to 50L, ftutterAutoScrollSettings(emptyMap()))
        assertEquals(
            12 to 120L,
            ftutterAutoScrollSettings(mapOf(FutaberSettingKeys.AUTO_SCROLL_PIXEL to "12", FutaberSettingKeys.AUTO_SCROLL_SPEED to "120"))
        )
        assertEquals(
            5 to 50L,
            ftutterAutoScrollSettings(mapOf(FutaberSettingKeys.AUTO_SCROLL_PIXEL to "31", FutaberSettingKeys.AUTO_SCROLL_SPEED to "5"))
        )
    }

    @Test
    fun listedPostsSkipHiddenRowsAndBands() {
        val rows = listOf(
            FtutterRow.PostRow(0, 0), FtutterRow.HiddenRow(1, 0), FtutterRow.ReadMark, FtutterRow.PostRow(2, 0), FtutterRow.PostRow(3, 1)
        )
        assertEquals(listOf(0, 2, 3), ftutterListedPostIndexes(rows))
        assertEquals(0, ftutterListedBefore(rows, 0))
        assertEquals(1, ftutterListedBefore(rows, 3))
        assertEquals(3, ftutterListedBefore(rows, 99))
        val posts = listOf(post("10"), post("11"), post("12"), post("13"))
        assertEquals(mapOf("p0" to "10", "p2" to "12", "p3" to "13"), ftutterAiItemKeys(rows, posts))
    }
}
