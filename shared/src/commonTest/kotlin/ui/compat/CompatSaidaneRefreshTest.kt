package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.CompatPostSnapshot
import kotlin.test.*

class CompatSaidaneRefreshTest {
    private fun post(no: String, label: String? = null) =
        CompatPostSnapshot(position = no.toInt(), postNo = no, timestamp = "t", messageHtml = "m", saidaneLabel = label)

    @Test fun anOpenPopupsCopyGetsTheNewSaidaneCount() {
        val popup = listOf(post("1"), post("2", "そうだねx1"))
        val updated = compatPostsWithSaidane(popup, "2", "そうだねx2")
        assertEquals(listOf(null, "そうだねx2"), updated.map { it.saidaneLabel })
    }

    @Test fun aPopupWithoutThatPostIsLeftAsItIs() {
        val popup = listOf(post("1"))
        assertSame(popup, compatPostsWithSaidane(popup, "9", "そうだねx1"))
    }
}
