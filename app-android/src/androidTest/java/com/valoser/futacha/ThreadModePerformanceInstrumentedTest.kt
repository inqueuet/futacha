@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import coil3.ImageLoader
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.board.*
import com.valoser.futacha.shared.ui.compat.CompatibilityApp
import com.valoser.futacha.shared.ui.image.*
import kotlinx.coroutines.runBlocking
import org.junit.*

/** Same 500-post data and device; excludes networking so rendering differences can be measured. */
class ThreadModePerformanceInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun compareBothModesWithIdenticalPosts() {
        val url="https://may.2chan.net/b/"
        val loader=ImageLoader(rule.activity)
        val page=ThreadPage("1001","Performance",null,null,(0 until 500).map { i ->
            Post((1001+i).toString(),i,null,null,"26/10/04 18:00",messageHtml="PERF-$i<br>同じ本文で表示時間とスクロールを確認します。",imageUrl=null,thumbnailUrl=null)
        })
        val repo=object : BoardRepository by FakeBoardRepository() {
            override suspend fun getThread(board:String,threadId:String)=page
            override suspend fun getThreadByUrl(threadUrl:String)=page
            override suspend fun getThreadContent(board:String,threadId:String)=ThreadPageContent(page)
            override suspend fun getThreadContentByUrl(threadUrl:String)=ThreadPageContent(page)
        }
        try {
            for (round in 0..2) for (compat in if(round % 2 == 0) listOf(false,true) else listOf(true,false)) {
                val db="mode_perf_${round}_${compat}.db"
                val store=AndroidCompatibilityStore(rule.activity,databaseName=db)
                runBlocking {
                    store.initialize()
                    store.savePreference("compat.commonUsedVersion","1.0")
                    store.upsertBoard(CompatBoard(compatBoardKey(url),"may",url,url,0))
                }
                val start=System.nanoTime()
                rule.runOnUiThread { rule.activity.setContent { MaterialTheme {
                    CompositionLocalProvider(LocalFutachaImageLoader provides loader,LocalFutachaCatalogImageLoader provides loader) {
                        if(compat) CompatibilityApp(store=store,repository=repo,imageLoader=loader,initialThreadDeepLink="${url}res/1001.htm",onExitApplication={})
                        else ThreadScreen(board=BoardSummary("perf","may","test",url,""),history=emptyList(),threadId="1001",threadTitle="Performance",initialReplyCount=499,repository=repo,onBack={},preferencesState=ScreenPreferencesState("test"))
                    }
                } } }
                rule.waitUntil(20_000) { rule.onAllNodesWithText("PERF-0",substring=true).fetchSemanticsNodes(atLeastOneRootRequired=false).isNotEmpty() }
                val first=(System.nanoTime()-start)/1_000_000
                val scrollStart=System.nanoTime()
                repeat(5) { rule.onRoot().performTouchInput { swipeUp(durationMillis=250) } }
                rule.waitForIdle()
                val scroll=(System.nanoTime()-scrollStart)/1_000_000
                Log.i("ThreadModePerformance","round=$round mode=${if(compat) "toshiaki" else "futacha"} firstMs=$first scroll5Ms=$scroll")
                rule.runOnUiThread { rule.activity.setContent {} }
                rule.waitForIdle()
                runBlocking { store.closeForTest() }
                rule.activity.deleteDatabase(db)
            }
        } finally { loader.shutdown() }
    }
}
