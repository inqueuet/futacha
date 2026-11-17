package com.valoser.futacha.shared.compat.aimoge

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

// Goldens produced from today's code (2026-11-11) by running the dumps in [FutabaGoldenDumps].
private val GOLDEN_CAT = """
CatalogItem(id=1439323740, threadUrl=https://may.2chan.net/b/futaba.php/res/1439323740.htm, title=スレ題その1, thumbnailUrl=https://may.2chan.net/b/thumb/1762145224666s.jpg, fullImageUrl=https://may.2chan.net/b/src/1762145224666.jpg, thumbnailWidth=50, thumbnailHeight=38, replyCount=17, expiresAtEpochMillis=null)
CatalogItem(id=1439323741, threadUrl=https://may.2chan.net/b/futaba.php/res/1439323741.htm, title=&記号<入り>, thumbnailUrl=https://may.2chan.net/b/thumb/1762145224667s.png, fullImageUrl=https://may.2chan.net/b/src/1762145224667.png, thumbnailWidth=38, thumbnailHeight=50, replyCount=0, expiresAtEpochMillis=null)
CatalogItem(id=1439323742, threadUrl=https://may.2chan.net/b/futaba.php/res/1439323742.htm, title=No.1439323742, thumbnailUrl=https://may.2chan.net/b/thumb/1762145224668s.jpg?x=1, fullImageUrl=https://may.2chan.net/b/src/1762145224668s.jpg?x=1, thumbnailWidth=50, thumbnailHeight=50, replyCount=999, expiresAtEpochMillis=null)
CatalogItem(id=1439323743, threadUrl=https://may.2chan.net/b/futaba.php/res/1439323743.htm, title=画像なし, thumbnailUrl=null, fullImageUrl=null, thumbnailWidth=null, thumbnailHeight=null, replyCount=3, expiresAtEpochMillis=null)
CatalogItem(id=1364612020, threadUrl=https://may.2chan.net/b/futaba.php/res/1364612020.htm, title=チュートリアル, thumbnailUrl=https://may.2chan.net/t/thumb/1762436883775s.jpg, fullImageUrl=https://may.2chan.net/t/src/1762436883775.jpg, thumbnailWidth=50, thumbnailHeight=25, replyCount=0, expiresAtEpochMillis=null)
""".trimIndent()
private val GOLDEN_REF = """
100 count=4 parsed=true refs=[]
101 count=2 parsed=true refs=[QuoteReference(text=>本文A, targetPostIds=[100], isDirectReply=true, replyTargetPostIds=[100])]
102 count=0 parsed=true refs=[QuoteReference(text=>No.101, targetPostIds=[101], isDirectReply=true, replyTargetPostIds=[101])]
103 count=0 parsed=true refs=[QuoteReference(text=>ID:aaa111, targetPostIds=[100], isDirectReply=true, replyTargetPostIds=[100])]
104 count=0 parsed=true refs=[QuoteReference(text=>1762000000111.jpg, targetPostIds=[100], isDirectReply=true, replyTargetPostIds=[100])]
105 count=0 parsed=true refs=[QuoteReference(text=>>本文A
>返信1, targetPostIds=[101], isDirectReply=true, replyTargetPostIds=[101])]
106 count=0 parsed=true refs=[]
107 count=0 parsed=true refs=[]
108 count=0 parsed=true refs=[QuoteReference(text=>行B, targetPostIds=[100], isDirectReply=true, replyTargetPostIds=[100])]
""".trimIndent()
private val GOLDEN_QIDX = """
101 [text:本文A] -> [100] first=100
102 [no:101] -> [101] first=101
103 [id:aaa111] -> [100] first=100
104 [file:1762000000111.jpg] -> [100] first=100
105 [text:本文A] -> [101, 100] first=101
105 [text:返信1] -> [101] first=101
106 [text:存在しない行] -> [] first=null
107 [no:3\ntext:3時のおやつ] -> [] first=null
108 [text:行B] -> [100] first=100
""".trimIndent()
private val GOLDEN_QIDXEX = """
1364612099 [text:テスト2] -> [] first=null
1364612689 [no:1364612475] -> [1364612475] first=1364612475
1364612689 [no:1364612475] -> [1364612475] first=1364612475
1364612713 [no:1364612475] -> [1364612475] first=1364612475
1364612713 [no:1364612475] -> [1364612475] first=1364612475
1364612823 [text:テスト16] -> [] first=null
1364612824 [no:1364612475] -> [1364612475] first=1364612475
1364612824 [no:1364612475] -> [1364612475] first=1364612475
1364612840 [text:テスト18] -> [] first=null
""".trimIndent()
private val GOLDEN_FRESH = """
noLocal=false
emptyFetched=true
localLonger=true
serverLonger=false
sameSize=false
serverTruncatedSameSize=true
localTruncatedShorterServer=false
serverDeletedFlagSameSize=false
notAPrefix=false
""".trimIndent()
private val GOLDEN_REC = """
notArchive: offline=false truncated=false posts=100:[arc]:false,101:[arc]:false,102:[arc]:false,103:[arc]:false
otherThread: offline=false truncated=false posts=100:[arc]:false,101:[arc]:false,102:[arc]:false,103:[arc]:false
localCopyWins: offline=true truncated=false posts=100:br>行B:false,101:r>返信1:false,102:>番号引用:false,103:>ID引用:false,104:イル名引用:false,105:r>入れ子:false,106:font>:false,107:数字始まり:false,108:font>:false
remoteFillsGaps: offline=false truncated=false posts=100:[arc]:false,101:[arc]:false,102:[arc]:false,103:[arc]:false,104:イル名引用:false,105:r>入れ子:false,106:font>:false,107:数字始まり:false,108:font>:false
remoteCovered: offline=false truncated=false posts=100:br>行B:false,101:r>返信1:false,102:>番号引用:false,103:>ID引用:false,104:イル名引用:false,105:r>入れ子:false,106:font>:false,107:数字始まり:false,108:font>:false
truncatedVisible: offline=false truncated=false posts=100:[arc]:false,101:[arc]:false,102:[arc]:false,103:[arc]:false,104:イル名引用:false,105:r>入れ子:false,106:font>:false,107:数字始まり:false,108:font>:false
deletedInArchiveLocal: offline=true truncated=false posts=100:br>行B:false,101:r>返信1:false,102:>番号引用:false,103:>ID引用:false,104:イル名引用:false,105:r>入れ子:false,106:font>:false,107:数字始まり:false,108:font>:false
deletedInArchiveRemote: offline=false truncated=false posts=100:br>行B:false,101:r>返信1:false,102:>番号引用:false,103:削除:true,104:イル名引用:false,105:r>入れ子:false,106:font>:false,107:数字始まり:false,108:font>:false
noVisible: offline=false truncated=false posts=100:[arc]:false,101:[arc]:false,102:[arc]:false,103:[arc]:false
""".trimIndent()
private val GOLDEN_HIST = """
run1 window2 requests=3
  POST https://may.2chan.net/b/futaba.php?mode=catset
  GET https://may.2chan.net/b/res/1439323741.htm
  GET https://may.2chan.net/b/res/1439323742.htm
  cursor={"index":2,"identity":"b::1439323743"}
run2 window2 newInstance requests=2
  GET https://may.2chan.net/b/res/1439323743.htm
  GET https://may.2chan.net/b/res/1439323744.htm
  cursor={"index":4,"identity":"b::1439323745"}
run3 window3 budget0 requests=0
  cursor={"index":4,"identity":"b::1439323745"}
run4 window2 afterBudget requests=2
  GET https://may.2chan.net/b/res/1439323745.htm
  GET https://may.2chan.net/b/res/1439323741.htm
  cursor={"index":1,"identity":"b::1439323742"}
history=1439323741:21:チュートリアル,1439323742:21:チュートリアル,1439323743:21:チュートリアル,1439323744:21:チュートリアル,1439323745:21:チュートリアル
""".trimIndent()

/**
 * AM-B0: the Futaba catalog and thread conversion (every field of every [com.valoser.futacha.shared.model.CatalogItem],
 * [com.valoser.futacha.shared.model.ThreadPage] and [com.valoser.futacha.shared.model.Post]) must stay as is for Futaba
 * after あいもげ support (AM-B2 adds fields with defaults). The full example thread dump (~10KB) is pinned by its
 * SHA-256 plus spot checks, so a failure names the changed field through the spot checks or by re-dumping.
 */
class FutabaConversionGoldenTest {
    @Test
    fun catalogItemsKeepEveryField() = runBlocking {
        assertEquals(GOLDEN_CAT, FutabaGoldenDumps.catalogDump())
    }

    @Test
    fun threadPageAndPostsKeepEveryField() = runBlocking {
        val page = FutabaGoldenDumps.exampleThread()
        assertEquals(21, page.posts.size)
        assertEquals(
            "ThreadPage(threadId=1364612020, boardTitle=ようこそ＠ふたちゃ, expiresAtLabel=19:54頃消えます, deletedNotice=削除された記事が12件あります.見る, posts=[], isTruncated=false, truncationReason=null, quoteReferencesComplete=true)",
            withoutSiteDefaults(page.copy(posts = emptyList()).toString())
        )
        assertEquals(
            "Post(id=1364612020, order=0, author=としあき, subject=無念, timestamp=25/11/08(土)13:42:53 ID:Mua720AI, posterId=ID:Mua720AI, messageHtml=チュートリアル, imageUrl=https://may.2chan.net/b/src/1762576973515.png, thumbnailUrl=https://may.2chan.net/b/thumb/1762576973515s.jpg, saidaneLabel=そうだねx2, isDeleted=false, isIsolated=false, referencedCount=0, quoteReferences=[], mail=id表示, thumbnailWidth=250, thumbnailHeight=251, imageFileSizeBytes=377716, quoteReferencesParsed=true)",
            withoutSiteDefaults(page.posts[0].toString())
        )
        assertEquals(3, page.posts.single { it.id == "1364612475" }.referencedCount)
        assertEquals("bca67716cfaf211151b6ab60e59b989f9053a157da4c5b638f58676182e7ca72", FutabaGoldenDumps.sha256(FutabaGoldenDumps.threadDump()))
    }
}

/**
 * AM-B0: the quote tables (parser/ThreadHtmlReferenceSupport via `ThreadHtmlParserCore.rebuildReferences`, and
 * `CompatQuoteIndex`) must stay as is for Futaba threads after あいもげ support.
 */
class FutabaQuoteTableGoldenTest {
    @Test
    fun rebuildReferencesOnHandMadeQuotes() {
        assertEquals(GOLDEN_REF, FutabaGoldenDumps.referenceDump(FutabaGoldenDumps.quotePosts))
    }

    @Test
    fun rebuildReferencesOnTheExampleThread() = runBlocking {
        val posts = FutabaGoldenDumps.exampleThread().posts
        assertEquals("50295823a8531845057fc5e2fd3337fa5e5c1322896c623a4e32cf3d144dcdcb", FutabaGoldenDumps.sha256(FutabaGoldenDumps.referenceDump(posts)))
    }

    @Test
    fun compatQuoteIndexOnHandMadeQuotes() {
        assertEquals(GOLDEN_QIDX, FutabaGoldenDumps.quoteIndexDump(FutabaGoldenDumps.quotePosts))
    }

    @Test
    fun compatQuoteIndexOnTheExampleThread() = runBlocking {
        assertEquals(GOLDEN_QIDXEX, FutabaGoldenDumps.quoteIndexDump(FutabaGoldenDumps.exampleThread().posts))
    }
}

/**
 * AM-B0: "local copy wins" (`shouldPreferLocalCompatSnapshot` and `reconcileArchiveLoadWithVisiblePage`) must stay as
 * is for Futaba boards after あいもげ support: newer local, newer server, truncated and deleted posts.
 */
class FutabaLocalCopyWinsGoldenTest {
    @Test
    fun snapshotFreshness() {
        assertEquals(GOLDEN_FRESH, FutabaGoldenDumps.freshnessDump())
    }

    @Test
    fun archiveReconcileWithTheVisiblePage() {
        assertEquals(GOLDEN_REC, FutabaGoldenDumps.reconcileDump())
    }
}

/**
 * AM-B0: the background refresh (`HistoryRefresher` / `HistoryRefreshSupport`) must stay as is for Futaba history
 * rows after あいもげ support: request order and count (MockEngine, one at a time), the window cursor written to
 * private/history_refresh_cursor.json, and the rows a zero run budget leaves for the next run.
 */
class FutabaHistoryRefreshGoldenTest {
    @Test
    fun requestsCursorAndBudgetCarryOver() = runBlocking {
        assertEquals(GOLDEN_HIST, FutabaGoldenDumps.historyDump())
    }
}
