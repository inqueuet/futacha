@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import coil3.ImageLoader
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.parser.ThreadHtmlParserCore
import com.valoser.futacha.shared.ui.board.*
import com.valoser.futacha.shared.ui.image.*
import org.junit.*
import org.junit.Assert.*

class FutachaNgQuoteInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var loader: ImageLoader
    private fun page() = ThreadPage("100", null, null, null, ThreadHtmlParserCore.rebuildReferences(listOf(
        Post("100", author = null, subject = null, timestamp = "", messageHtml = "SOURCE_HIDDEN", imageUrl = null, thumbnailUrl = null),
        Post("101", author = null, subject = null, timestamp = "", messageHtml = "&gt;No.100<br>REPLY_HIDDEN", imageUrl = null, thumbnailUrl = null))))
    @Before fun prepare() { loader = ImageLoader.Builder(rule.activity).build() }
    @After fun cleanup() { rule.runOnUiThread { rule.activity.setContent {} }; rule.waitForIdle(); loader.shutdown() }
    private fun open(hide: String) {
        // This is exactly the host's NG-filtered page -> actual ThreadContent route.
        val filtered = applyNgFilters(page(), emptyList(), listOf(hide), true)
        rule.setContent { MaterialTheme {
            CompositionLocalProvider(LocalFutachaImageLoader provides loader, LocalFutachaCatalogImageLoader provides loader) {
                ThreadContent(page = filtered, embeddedHtml = emptyList(), summaryState = null,
                    listState = rememberLazyListState(), saidaneOverrides = emptyMap(), searchHighlightRanges = emptyMap(),
                    onPostLongPress = {}, onQuoteRequestedForPost = {}, onSaidaneClick = {}, onUrlClick = {}, onRefresh = {}, isRefreshing = false)
            }
        } }
        rule.waitForIdle()
    }
    @Test fun hidingTheOnlyReplyRemovesItsReplyCountLabel() {
        open("REPLY_HIDDEN")
        rule.onAllNodesWithText("1レス", useUnmergedTree = true).assertCountEquals(0)
        rule.onAllNodesWithText("REPLY_HIDDEN", substring = true).assertCountEquals(0)
    }
    @Test fun hidingTheSourceExplainsWhyTheQuoteHasNoVisibleSource() {
        open("SOURCE_HIDDEN")
        android.os.SystemClock.sleep(300); rule.waitForIdle()
        rule.onAllNodesWithText(">No.100", substring = true).onLast().performTouchInput { click(androidx.compose.ui.geometry.Offset(24f, 8f)) }
        rule.waitForIdle()
        rule.onNodeWithText("引用元のレスは非表示か、取得できていません。NGや絞り込みの設定も確認してください。").assertIsDisplayed()
    }
}
