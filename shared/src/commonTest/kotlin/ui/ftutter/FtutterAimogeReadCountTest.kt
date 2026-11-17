package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.model.Post
import kotlin.test.Test
import kotlin.test.assertEquals

/** あいもげは消えたレスを posts に入れないが件数（seq）には数える。全部読んだら未読を残さない（スレ01a1e1d7）。 */
class FtutterAimogeReadCountTest {
    private fun post(i: Int, seq: Int?) = Post(id = "p$i", author = null, subject = null, timestamp = "", messageHtml = "", imageUrl = null, thumbnailUrl = null, displayNumber = seq)

    // seq 0..113、15と107は消えた：112件、件数（返信）は113
    private val aimoge = (0..113).filter { it != 15 && it != 107 }.mapIndexed { i, seq -> post(i, seq) }

    @Test fun readingToTheEndStoresTheSitesReplyCount() {
        val read = ftutterSitePostsRead(aimoge, aimoge.size)
        assertEquals(114, read)
        assertEquals(113, ftutterStoredFromPostsRead(read))
    }

    @Test fun theStoredCountComesBackAsTheLocalIndex() {
        assertEquals(aimoge.size, ftutterPostsReadFromSite(aimoge, ftutterSitePostsRead(aimoge, aimoge.size)))
        // 途中（seq 20まで＝手元の20件目まで）も往復で同じ位置
        val partial = aimoge.indexOfFirst { it.displayNumber == 20 } + 1
        assertEquals(partial, ftutterPostsReadFromSite(aimoge, ftutterSitePostsRead(aimoge, partial)))
    }

    @Test fun futabaIsUnchanged() {
        val futaba = (0 until 50).map { post(it, null) }
        assertEquals(50, ftutterSitePostsRead(futaba, 50))
        assertEquals(30, ftutterPostsReadFromSite(futaba, 30))
    }
}
