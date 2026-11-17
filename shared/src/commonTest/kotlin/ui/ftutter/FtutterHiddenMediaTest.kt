package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.model.Post
import kotlin.test.Test
import kotlin.test.assertEquals

class FtutterHiddenMediaTest {
    private fun post(id: String, deleted: Boolean = false) = Post(id, author = null, subject = null, timestamp = "", messageHtml = "body",
        imageUrl = "https://may.2chan.net/b/src/$id.png", thumbnailUrl = null, isDeleted = deleted)
    @Test fun explicitlyOpeningAnNgPostCannotOverrideTheMediaExclusion() {
        val posts = listOf(post("100"), post("101"))
        assertEquals(listOf("100"), ftutterMediaPosts(posts, true, keepPostId = "101", hiddenPostIds = setOf("101")).map { it.postNo })
        assertEquals(emptyList(), ftutterMediaPosts(posts, true, keepPostId = "101", onlyPostId = "101", hiddenPostIds = setOf("101")))
    }
    @Test fun theDeletedOpeningExceptionRemainsSeparateFromNg() {
        val posts = listOf(post("100"), post("101", deleted = true))
        assertEquals(listOf("100", "101"), ftutterMediaPosts(posts, true, keepPostId = "101").map { it.postNo })
        assertEquals(listOf("100"), ftutterMediaPosts(posts, false, keepPostId = "101", hiddenPostIds = setOf("101")).map { it.postNo })
    }
}
