@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.valoser.futacha.shared.compat.CompatNgKind
import com.valoser.futacha.shared.compat.CompatNgRule
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.ui.ftutter.rememberFtutterReportedPostNgRules
import com.valoser.futacha.shared.ui.ftutter.rememberFtutterVisibleCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * L25: the catalog's visible list and the reported post NG rules keep their instance while their inputs stay, so an
 * unrelated recomposition (a setting saved) does not hand the receivers a new list.
 */
class FtutterDerivedListsInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun derivedListsKeepTheirInstanceUntilTheirInputsChange() {
        val items = (1..5).map { CatalogItem(id = "$it", title = "スレ$it", thumbnailUrl = null, fullImageUrl = null,
            threadUrl = "https://may.2chan.net/b/res/$it.htm", replyCount = it) }
        val rules = listOf(
            CompatNgRule("a", CompatNgKind.THREAD_POST_NO, "https://may.2chan.net/b/res/1.htm", "12", 1),
            CompatNgRule("b", CompatNgKind.THREAD_POST_NO, "*", "13", 2)
        )
        var unrelated by mutableIntStateOf(0)
        var max by mutableIntStateOf(3)
        val visibleSeen = mutableListOf<List<CatalogItem>>()
        val reportedSeen = mutableListOf<List<CompatNgRule>>()
        rule.setContent {
            Text("${unrelated}")
            val visible = rememberFtutterVisibleCatalog(items, max, null)
            val reported = rememberFtutterReportedPostNgRules(rules)
            SideEffect { visibleSeen += visible; reportedSeen += reported }
        }
        repeat(3) { rule.runOnIdle { unrelated++ } }
        rule.waitForIdle()
        assertTrue("The scope recomposed: ${visibleSeen.size}", visibleSeen.size >= 4)
        assertTrue(visibleSeen.all { it === visibleSeen.first() })
        assertTrue(reportedSeen.all { it === reportedSeen.first() })
        assertEquals(listOf("1", "2", "3"), visibleSeen.first().map { it.id })
        assertEquals(listOf("a"), reportedSeen.first().map { it.id })
        rule.runOnIdle { max = 4 }
        rule.waitForIdle()
        assertNotSame(visibleSeen.first(), visibleSeen.last())
        assertEquals(4, visibleSeen.last().size)
    }
}
