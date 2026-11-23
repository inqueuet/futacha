@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.model.defaultCatalogNavEntries
import com.valoser.futacha.shared.ui.board.CatalogNavigationBar
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/**
 * may 1449174385: the compact catalog bar kept icons only, which were hard to tell apart. It now keeps a
 * small one-line label per item that is never cut off on a 1080px (411dp) phone, at 56dp with 48dp targets.
 */
class CatalogCompactNavLabelsInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun compactBarKeepsWholeLabelsAndTouchTargets() {
        rule.setContent {
            MaterialTheme {
                Box(Modifier.width(411.dp)) {
                    CatalogNavigationBar(defaultCatalogNavEntries(), onNavigate = {}, compact = true)
                }
            }
        }
        val labels = rule.onAllNodesWithTag("catalog-nav-compact-label", useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(listOf("スレッド作成", "一番上に行く", "カタログ更新", "過去スレ検索", "モード", "設定"),
            labels.map { it.config[SemanticsProperties.Text].joinToString("") { t -> t.text } })
        labels.forEach { node ->
            val results = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
            val r = results.single(); assertFalse("cut off: ${node.config[SemanticsProperties.Text]} w=${r.size} ow=${r.didOverflowWidth} lines=${r.lineCount} ell=${r.isLineEllipsized(0)} c=${r.layoutInput.constraints}", r.lineCount != 1 || r.isLineEllipsized(0))
        }
        val bar = rule.onNodeWithTag("catalog-nav-compact").fetchSemanticsNode()
        val density = rule.density
        rule.onAllNodes(hasClickAction()).fetchSemanticsNodes().forEach {
            assertTrue("target ${it.size}", with(density) { it.size.height.toDp() } >= 48.dp)
            assertTrue("target ${it.size}", with(density) { it.size.width.toDp() } >= 48.dp)
        }
        assertTrue(with(density) { bar.size.height.toDp() } >= 56.dp)
    }
}
