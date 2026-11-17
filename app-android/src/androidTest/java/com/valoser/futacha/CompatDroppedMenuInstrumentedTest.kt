package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.valoser.futacha.shared.compat.CompatCatalogDroppedClass
import com.valoser.futacha.shared.compat.CompatDroppedCatalogItem
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.ui.compat.CompatDroppedCatalogScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/** 参考アプリ 18-20: the dropped threads' long press adds 「新しいタブに追加」 and 「del」. No server is used. */
class CompatDroppedMenuInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val threadUrl = "https://may.2chan.net/b/res/123.htm"
    private val item = CatalogItem("123", threadUrl, "ふくろうの話", null, null, replyCount = 4)

    private fun show(open: Boolean, added: MutableList<String>, sent: MutableList<String>) {
        rule.setContent {
            MaterialTheme {
                CompatDroppedCatalogScreen(
                    "b", listOf(CompatDroppedCatalogItem("may/b", item, 1, classification = CompatCatalogDroppedClass.DIE)),
                    onBack = {}, onOpenThread = {}, onDeleteDieEntries = {},
                    onAddTab = { added += it.id }, isTabOpen = { open },
                    onSendDel = { sent += it.id }
                )
            }
        }
        rule.onNodeWithTag("compat-dropped-row-123").performTouchInput { longClick() }
        rule.waitForIdle()
    }

    @Test fun theMenuListsAddTabAndDelBeforeTheHistoryDelete() {
        show(false, CopyOnWriteArrayList(), CopyOnWriteArrayList())
        val add = rule.onNodeWithTag("compat-dropped-add-tab", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
        val del = rule.onNodeWithTag("compat-dropped-del", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
        val history = rule.onNodeWithText("落ちスレを履歴から削除").fetchSemanticsNode().boundsInRoot.top
        assertTrue("add $add < del $del < history $history", add < del && del < history)
    }

    @Test fun addingATabAddsItAndSaysSo() {
        val added = CopyOnWriteArrayList<String>()
        show(false, added, CopyOnWriteArrayList())
        rule.onNodeWithTag("compat-dropped-add-tab").performClick()
        rule.onNodeWithText("ふくろう をタブに追加しました").assertExists()
        assertEquals(listOf("123"), added.toList())
    }

    @Test fun anOpenTabIsNotAddedAgain() {
        val again = CopyOnWriteArrayList<String>()
        show(true, again, CopyOnWriteArrayList())
        rule.onNodeWithTag("compat-dropped-add-tab").performClick()
        rule.onNodeWithText("既に追加されています").assertExists()
        assertTrue(again.isEmpty())
    }

    @Test fun delAsksFirstAndSendsOnlyAfterTheConfirmation() {
        val sent = CopyOnWriteArrayList<String>()
        show(false, CopyOnWriteArrayList(), sent)
        rule.onNodeWithTag("compat-dropped-del").performClick()
        rule.onNodeWithText("削除依頼 ふくろうの話").assertExists()
        rule.onNodeWithText("キャンセル").performClick()
        rule.waitForIdle()
        assertTrue(sent.isEmpty())

        rule.onNodeWithTag("compat-dropped-row-123").performTouchInput { longClick() }
        rule.onNodeWithTag("compat-dropped-del").performClick()
        rule.onNodeWithTag("compat-dropped-del-confirm").performClick()
        rule.waitUntil(5_000) { sent.size == 1 }
        rule.onNodeWithText("送信しました").assertExists()
        assertEquals(listOf("123"), sent.toList())
    }
}
