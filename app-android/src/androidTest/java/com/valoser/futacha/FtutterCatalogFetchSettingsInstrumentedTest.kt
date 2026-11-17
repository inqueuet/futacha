@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import coil3.ImageLoader
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.ftutter.*
import com.valoser.futacha.shared.ui.image.*
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.CopyOnWriteArrayList

class FtutterCatalogFetchSettingsInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val board = BoardSummary("fetch-img", "img", "", "https://img.2chan.net/b/", "")
    private val holder = FtutterCatalogHolder()
    private var preferences by mutableStateOf<Map<String, String>>(emptyMap())
    private var rules by mutableStateOf<List<FtutterNgRule>>(emptyList())
    private val requests = CopyOnWriteArrayList<Pair<CatalogMode, CatalogFetchSettings>>()
    private lateinit var images: ImageLoader
    private val repository = object : BoardRepository by FakeBoardRepository() {
        override suspend fun getCatalogPageWithSettings(board: String, mode: CatalogMode, settings: CatalogFetchSettings): CatalogPageContent {
            requests += mode to settings
            val id = if (mode == CatalogMode.Many) "2" else "1"
            return CatalogPageContent(listOf(CatalogItem(id = id, title = "前半".repeat(25) + "監視する語", thumbnailUrl = null,
                fullImageUrl = null, threadUrl = "${board}res/$id.htm", replyCount = 10)))
        }
    }
    @After fun cleanup() { rule.runOnUiThread { rule.activity.setContent {} }; if (::images.isInitialized) images.shutdown() }
    @Test fun countAndPickupRuleChangesRefetchWithFullTextInsteadOfReusingTheOldLoad() {
        images = ImageLoader.Builder(rule.activity).build()
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides images, LocalFutachaCatalogImageLoader provides images) {
                MaterialTheme { FtutterTheme(null, FtutterBrightness.LIGHT) {
                    FtutterCatalogScreen(board, listOf(board), repository, emptyList(), preferences, holder,
                        { _, _ -> }, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}, ngRules = rules)
                } }
            }
        }
        rule.waitUntil(10_000) { requests.size == 1 && holder.load?.loading == false }
        assertEquals(500, requests.first().second.approximateThreadCount)
        rule.runOnUiThread { preferences = mapOf(FtutterKeys.MAX_CATALOG_THREAD_COUNT to "2000") }
        rule.waitUntil(10_000) { requests.size == 2 && holder.load?.loading == false }
        assertEquals(2000, requests.last().second.approximateThreadCount)
        rule.runOnUiThread { rules = listOf(FtutterNgRule("監視する語", FtutterNgType.PICKUP.stored)) }
        rule.waitUntil(10_000) { requests.size == 4 && holder.load?.loading == false }
        assertEquals(setOf("1", "2"), holder.load!!.items.map { it.id }.toSet())
        assertTrue(requests.all { it.second.titleLines == 256 })
    }
}
