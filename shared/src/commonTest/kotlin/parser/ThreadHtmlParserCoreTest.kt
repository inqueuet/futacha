package com.valoser.futacha.shared.parser

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ThreadHtmlParserCoreTest {
    @Test
    fun parseThread_keepsAllMediaAcrossOneThousandReplies() {
        val replyCount = 1_000
        val html = buildString {
            append(
                """
                    <html><head><link rel="canonical" href="https://may.2chan.net/b/res/9000000000.htm"></head>
                    <body><div class="thre" data-res="9000000000">
                    <span class="cnw">26/08/23(日)00:00:00 ID:OP</span><span class="cno">No.9000000000</span>
                    <a href="/b/src/9000000000.jpg">9000000000.jpg</a><img src="/b/thumb/9000000000s.jpg">
                    <blockquote>OP</blockquote></div>
                """.trimIndent()
            )
            repeat(replyCount) { index ->
                val postId = 9_000_000_001L + index
                val extension = if (index % 2 == 0) "png" else "webm"
                append(
                    """
                        <table border=0><tr><td class=rtd>
                        <span id="delcheck$postId" class="rsc">$index</span>
                        <span class="cnw">26/08/23(日)00:00:01 ID:R$index</span><span class="cno">No.$postId</span>
                        <a href="/b/src/$postId.$extension">$postId.$extension</a><img src="/b/thumb/${postId}s.jpg">
                        <blockquote>reply $index</blockquote>
                        </td></tr></table>
                    """.trimIndent()
                )
            }
            append("</body></html>")
        }

        val page = runBlocking {
            ThreadHtmlParserCore.parseThread(html, "https://may.2chan.net/b/res/9000000000.htm")
        }

        assertEquals(replyCount + 1, page.posts.size)
        assertEquals(replyCount + 1, page.posts.count { it.imageUrl != null })
        assertEquals(replyCount + 1, page.posts.count { it.thumbnailUrl != null })
        assertTrue(page.posts.any { it.imageUrl?.endsWith(".webm") == true })
    }

    @Test
    fun parseThread_acceptsQuotedBorderAndAttributeOrderUsedByOlderBoards() {
        val html = """
            <html><body>
              <div class="thre" data-res="800">
                <span class="cnw">25/01/01(水)00:00:00 ID:OLD</span>
                <span class="cno">No.800</span>
                <blockquote>本文</blockquote>
              </div>
              <table class="r" data-layout="legacy" border="0">
                <tr><td class="rtd">
                  <span class="cnw">25/01/01(水)00:01:00 ID:NEW</span>
                  <span class="cno">No.801</span>
                  <blockquote>返信<br/>二行目</blockquote>
                </td></tr>
              </table>
            </body></html>
        """.trimIndent()

        val page = runBlocking { ThreadHtmlParserCore.parseThread(html) }

        assertEquals("800", page.threadId)
        assertEquals(listOf("800", "801"), page.posts.map { it.id })
        assertEquals("返信<br/>二行目", page.posts[1].messageHtml)
    }

    @Test
    fun parseThread_extractsThreadAndPosts() {
        val page = runBlocking { ThreadHtmlParserCore.parseThread(sampleThreadHtml) }

        assertEquals("354621", page.threadId)
        assertEquals("料理＠ふたば", page.boardTitle)
        assertEquals("1月18日頃消えます", page.expiresAtLabel)
        assertEquals("削除された記事が1件あります.見る", page.deletedNotice)
        assertEquals(3, page.posts.size)

        val op = page.posts[0]
        assertEquals("スレタイ", op.subject)
        assertEquals("名無し", op.author)
        assertEquals("25/11/03(日)13:47:04 ID:IDOP", op.timestamp)
        assertEquals("ID:IDOP", op.posterId)
        assertEquals("本文<br>2行目", op.messageHtml)
        assertEquals(0, op.order)
        assertEquals("そうだねx1", op.saidaneLabel)
        assertEquals(false, op.isDeleted)
        assertEquals(1, op.referencedCount)
        assertEquals(
            "https://www.example.com/t/src/1762145224666.jpg",
            op.imageUrl
        )
        assertEquals(
            "https://www.example.com/t/thumb/1762145224666s.jpg",
            op.thumbnailUrl
        )

        val reply = page.posts[1]
        assertEquals("無題", reply.subject)
        assertEquals("テスト", reply.author)
        assertEquals("返信1", reply.messageHtml)
        assertEquals(1, reply.order)
        assertEquals("そうだねx5", reply.saidaneLabel)
        assertEquals("ID:IDA1", reply.posterId)
        assertEquals(1, reply.referencedCount)
        assertEquals(null, reply.imageUrl)

        val imageReply = page.posts[2]
        assertEquals("画像レス", imageReply.subject)
        assertTrue(imageReply.messageHtml.contains("画像本文"))
        assertTrue(imageReply.isDeleted)
        assertEquals(0, imageReply.referencedCount)
        assertEquals("ID:IDIMG", imageReply.posterId)
        val thumb = imageReply.thumbnailUrl
        assertNotNull(thumb)
        assertTrue(thumb.endsWith("1762246395132s.jpg"))
    }

    @Test
    fun parseThread_retainsIsolationNoticePostAndMarksItIsolated() {
        val html = """
            <html>
            <head><link rel="canonical" href="https://www.example.com/res/500.htm"></head>
            <body>
            <div class="thre" data-res="500">
            <span class="cnw">25/01/01(月)00:00:00 ID:OP</span><span class="cno">No.500</span>
            <blockquote>OP body</blockquote>
            </div>
            <table border=0>
            <tr><td class=rtd>
            <span class="cnw">25/01/01(月)00:01:00 ID:DEL</span><span class="cno">No.501</span>
            <a href="/b/src/501.jpg"><img src="/b/thumb/501s.jpg" width="120" height="90"></a>
            <blockquote>削除依頼によって隔離されました<br>二行目</blockquote>
            </td></tr>
            </table>
            <table border=0>
            <tr><td class=rtd>
            <span class="cnw">25/01/01(月)00:02:00 ID:OK</span><span class="cno">No.502</span>
            <blockquote>通常レス</blockquote>
            </td></tr>
            </table>
            </body>
            </html>
        """.trimIndent()

        val page = runBlocking { ThreadHtmlParserCore.parseThread(html) }

        assertEquals(listOf("500", "501", "502"), page.posts.map { it.id })
        assertTrue(page.posts[1].isIsolated)
        assertEquals("https://www.example.com/b/src/501.jpg", page.posts[1].imageUrl)
    }

    @Test
    fun parseThread_marksThreadOwnerDeletedNoticePostsAsDeleted() {
        val html = """
            <html>
            <head><link rel="canonical" href="https://www.example.com/res/600.htm"></head>
            <body>
            <div class="thre" data-res="600">
            <span class="cnw">25/01/01(月)00:00:00 ID:OP</span><span class="cno">No.600</span>
            <blockquote>OP body</blockquote>
            </div>
            <table border=0>
            <tr><td class=rtd>
            <span class="cnw">25/01/01(月)00:01:00 ID:DEL</span><span class="cno">No.601</span>
            <blockquote><font color="#ff0000">スレッドを立てた人によって削除されました</font></blockquote>
            </td></tr>
            </table>
            </body>
            </html>
        """.trimIndent()

        val page = runBlocking { ThreadHtmlParserCore.parseThread(html) }

        assertTrue(page.posts[1].isDeleted)
    }

    @Test
    fun parseThread_doesNotTreatQuotedDeletedNoticeAsDeleted() {
        val html = """
            <html>
            <head><link rel="canonical" href="https://may.2chan.net/b/res/1431249663.htm"></head>
            <body>
            <div class="thre" data-res="1431249663">
            <span class="cnw">26/08/29(土)00:00:00</span><span class="cno">No.1431249663</span>
            <blockquote>OP body</blockquote>
            </div>
            <table border=0><tr><td class=rtd>
            <span class="cnw">26/08/29(土)18:25:16</span><span class="cno">No.1431289721</span>
            <a href="/b/src/1787995516778.png">1787995516778.png</a>
            <img src="/b/thumb/1787995516778s.jpg">
            <blockquote><font color="#789922">&gt;書き込みをした人によって削除されました</font><br>引用の下に書いた通常本文</blockquote>
            </td></tr></table>
            </body>
            </html>
        """.trimIndent()

        val post = runBlocking { ThreadHtmlParserCore.parseThread(html) }.posts[1]

        assertFalse(post.isDeleted)
        assertEquals("https://may.2chan.net/b/src/1787995516778.png", post.imageUrl)
        assertTrue(post.messageHtml.contains("引用の下に書いた通常本文"))
    }

    @Test
    fun parseThread_doesNotTreatQuotedIsolationNoticeAsIsolated() {
        val html = """
            <html><body>
            <div class="thre" data-res="610">
            <span class="cnw">25/01/01(月)00:00:00</span><span class="cno">No.610</span>
            <blockquote>OP body</blockquote>
            </div>
            <table border=0><tr><td class=rtd>
            <span class="cnw">25/01/01(月)00:01:00</span><span class="cno">No.611</span>
            <blockquote><font color="#789922">&gt;削除依頼によって隔離されました</font><br>通常本文</blockquote>
            </td></tr></table>
            </body></html>
        """.trimIndent()

        val post = runBlocking { ThreadHtmlParserCore.parseThread(html) }.posts[1]

        assertFalse(post.isIsolated)
    }

    @Test
    fun parseThread_retainsMailFieldForCompatSearch() {
        val html = """
            <html><body>
            <div class="thre" data-res="700">
            <a href="mailto:sage&amp;token"><span class="cnm">としあき</span></a>
            <span class="cnw">25/01/01(月)00:00:00</span><span class="cno">No.700</span>
            <blockquote>本文</blockquote>
            </div>
            </body></html>
        """.trimIndent()

        val page = runBlocking { ThreadHtmlParserCore.parseThread(html) }

        assertEquals("sage&token", page.posts.single().mail)
    }

    @Test
    fun parseThread_retainsThumbnailDimensionsForCompatLayout() {
        val html = """
            <html><body>
            <div class="thre" data-res="701">
            <span class="cnw">25/01/01(水)00:00:00</span><span class="cno">No.701</span>
            <a href="/src/701.webm">701.webm</a>
            <img width="640" height="360" src="/thumb/701s.jpg">
            <blockquote>動画</blockquote>
            </div>
            </body></html>
        """.trimIndent()

        val post = runBlocking { ThreadHtmlParserCore.parseThread(html) }.posts.single()

        assertEquals(640, post.thumbnailWidth)
        assertEquals(360, post.thumbnailHeight)
    }

    @Test
    fun extractOpImageUrl_returnsFirstSrcLinkFromSnippet() {
        val snippet = sampleThreadHtml
            .lineSequence()
            .take(25)
            .joinToString("\n")

        val url = ThreadHtmlParserCore.extractOpImageUrl(snippet, "https://www.example.com/t")

        assertEquals("https://www.example.com/t/src/1762145224666.jpg", url)
    }

    @Test
    fun extractOpImageUrl_prefersThumbnailForVideo() {
        val videoSnippet = """
            <html>
            <head><link rel="canonical" href="https://www.example.com/t/res/888.htm"></head>
            <body>
            <div class="thre">
            画像ファイル名：<a href="/t/src/123456789.webm">123456789.webm</a>
            <img src="/t/thumb/123456789s.jpg">
            </div>
            </body>
            </html>
        """.trimIndent()

        val url = ThreadHtmlParserCore.extractOpImageUrl(videoSnippet, "https://www.example.com/t")

        assertEquals("https://www.example.com/t/thumb/123456789s.jpg", url)
    }

    @Test
    fun extractOpImageUrl_prefersArchiveFetchUrlOverFutabaCanonical() {
        val snippet = """
            <html>
            <head><link rel="canonical" href="https://may.2chan.net/b/res/1418544510.htm"></head>
            <body>
            <div class="thre" data-res="1418544510">
            画像ファイル名：<a href="/b/src/1782878082789.png">1782878082789.png</a>
            <img src="/b/thumb/1782878082789s.jpg">
            </div>
            </body>
            </html>
        """.trimIndent()

        val url = ThreadHtmlParserCore.extractOpImageUrl(
            snippet,
            "https://may.inqueuet.com/b/res/1418544510.htm"
        )

        assertEquals("https://may.inqueuet.com/b/src/1782878082789.png", url)
    }

    @Test
    fun parseThread_handlesLegacyCntdClassSpan() {
        val legacyHtml = sampleThreadHtml.replace("id=\"contdisp\"", "class=\"cntd\"")
        val page = runBlocking { ThreadHtmlParserCore.parseThread(legacyHtml) }

        assertEquals("1月18日頃消えます", page.expiresAtLabel)
    }

    @Test
    fun quoteCounting_requiresAllQuoteLinesToAgree() {
        val html = """
            <html>
            <body>
            <div class="thre" data-res="100">
            <span class="csb">OP</span>Name<span class="cnm">名無しさん</span>
            <span class="cnw">25/01/01(月)00:00:00 ID:IDOP</span><span class="cno">No.100</span>
            <blockquote>alpha<br>beta</blockquote>
            </div>
            <table border=0>
            <tr><td class=rtd>
            <span id="delcheck101" class="rsc">1</span>
            <span class="csb">Reply</span>Name<span class="cnm">Tester</span>
            <span class="cnw">25/01/01(月)00:01:00 ID:ID1</span><span class="cno">No.101</span>
            <blockquote>&gt;alpha<br>&gt;beta</blockquote>
            </td></tr>
            </table>
            <table border=0>
            <tr><td class=rtd>
            <span id="delcheck102" class="rsc">2</span>
            <span class="csb">Another</span>Name<span class="cnm">Tester</span>
            <span class="cnw">25/01/01(月)00:02:00 ID:ID2</span><span class="cno">No.102</span>
            <blockquote>&gt;alpha<br>&gt;gamma</blockquote>
            </td></tr>
            </table>
            </body>
            </html>
        """.trimIndent()

        val page = runBlocking { ThreadHtmlParserCore.parseThread(html) }

        val op = page.posts[0]
        val firstReply = page.posts[1]
        val secondReply = page.posts[2]

        assertEquals(3, page.posts.size)
        assertEquals(1, op.referencedCount)
        assertEquals(0, firstReply.referencedCount)
        assertEquals(0, secondReply.referencedCount)
    }

    @Test
    fun quoteCounting_countsMediaFilenames() {
        val html = """
            <html>
            <body>
            <div class="thre" data-res="200">
            <span class="csb">OP</span>Name<span class="cnm">名無しさん</span>
            <span class="cnw">25/01/01(月)00:00:00 ID:IDOP</span><span class="cno">No.200</span>
            画像ファイル名：<a href="/src/200.jpg">200.jpg</a>
            <img src="/thumb/200s.jpg">
            <blockquote>op body</blockquote>
            </div>
            <table border=0>
            <tr><td class=rtd>
            <span class="cnw">25/01/01(月)00:01:00 ID:ID1</span><span class="cno">No.201</span>
            <blockquote>&gt;200.jpg<br>&gt;&gt;No.200</blockquote>
            </td></tr>
            </table>
            <table border=0>
            <tr><td class=rtd>
            <span class="cnw">25/01/01(月)00:02:00 ID:ID2</span><span class="cno">No.202</span>
            画像ファイル名：<a href="/src/202.webm">202.webm</a>
            <img src="/thumb/202s.jpg">
            <blockquote>&gt;202.webm</blockquote>
            </td></tr>
            </table>
            <table border=0>
            <tr><td class=rtd>
            <span class="cnw">25/01/01(月)00:03:00 ID:ID3</span><span class="cno">No.203</span>
            <blockquote>&gt;202.webm</blockquote>
            </td></tr>
            </table>
            </body>
            </html>
        """.trimIndent()

        val page = runBlocking { ThreadHtmlParserCore.parseThread(html) }

        assertEquals(4, page.posts.size)
        val op = page.posts[0]
        val firstReply = page.posts[1]
        val videoPost = page.posts[2]
        val videoReply = page.posts[3]

        // 200.jpg + >>No.200 の両方を引用しても1件としてカウント
        assertEquals(1, op.referencedCount)
        assertEquals(0, firstReply.referencedCount)
        // webm へのファイル名引用を逆引き
        assertEquals(1, videoPost.referencedCount)
        assertEquals(0, videoReply.referencedCount)

        // media filename が quoteReferences にも反映される
        val videoReference = videoReply.quoteReferences.single()
        assertEquals(listOf("202"), videoReference.targetPostIds)
        assertTrue(videoReference.text.contains("202.webm"))
    }

    @Test
    fun parseThread_doesNotTreatQuotedApuSmallFilenameAsAttachment() {
        val html = """
            <html><body>
            <div class="thre" data-res="910">
              <span class="cnw">25/01/01(水)00:00:00 ID:OP</span>
              <span class="cno">No.910</span>
              <blockquote>op</blockquote>
            </div>
            <table border="0"><tr><td class="rtd">
              <span class="cnw">25/01/01(水)00:01:00 ID:ONE</span>
              <span class="cno">No.911</span>
              <blockquote>fu7085829.jpg<br>添付</blockquote>
            </td></tr></table>
            <table border="0"><tr><td class="rtd">
              <span class="cnw">25/01/01(水)00:02:00 ID:TWO</span>
              <span class="cno">No.912</span>
              <blockquote>&gt;fu7085829.jpg<br>&gt;添付</blockquote>
            </td></tr></table>
            </body></html>
        """.trimIndent()

        val page = runBlocking { ThreadHtmlParserCore.parseThread(html) }

        assertEquals("https://dec.2chan.net/up2/src/fu7085829.jpg", page.posts[1].imageUrl)
        assertEquals(null, page.posts[2].imageUrl)
    }

    @Test
    fun quoteCounting_prefersMediaOverLeadingDigits() {
        val html = """
            <html>
            <body>
            <div class="thre" data-res="300">
            <span class="cnw">25/01/01(月)00:00:00 ID:IDOP</span><span class="cno">No.300</span>
            画像ファイル名：<a href="/src/1763808769494.jpg">1763808769494.jpg</a>
            <img src="/thumb/1763808769494s.jpg">
            <blockquote>op</blockquote>
            </div>
            <table border=0>
            <tr><td class=rtd>
            <span class="cnw">25/01/01(月)00:01:00 ID:ID1</span><span class="cno">No.301</span>
            <blockquote>&gt;1763808769494.jpg</blockquote>
            </td></tr>
            </table>
            </body>
            </html>
        """.trimIndent()

        val page = runBlocking { ThreadHtmlParserCore.parseThread(html) }
        assertEquals(2, page.posts.size)
        val op = page.posts[0]
        val reply = page.posts[1]

        // ファイル名の数字部分を投稿番号として扱わず、メディア優先で逆引きされる
        assertEquals(1, op.referencedCount)
        assertEquals(0, reply.referencedCount)

        val ref = reply.quoteReferences.single()
        assertEquals(listOf("300"), ref.targetPostIds)
        assertTrue(ref.text.contains("1763808769494.jpg"))
    }

    @Test
    fun quoteCounting_matchesQuotesWithoutPlainTextSource() {
        val html = """
            <html>
            <body>
            <div class="thre" data-res="400">
            <span class="cnw">25/01/01(月)00:00:00 ID:OP</span><span class="cno">No.400</span>
            <blockquote>
            &gt;そういえば今日で結婚8年目ので<br>
            &gt;まだ関係は良好ので
            </blockquote>
            </div>
            <table border=0>
            <tr><td class=rtd>
            <span class="cnw">25/01/01(月)00:05:00 ID:Reply</span><span class="cno">No.401</span>
            <blockquote>
            &gt;&gt;そういえば今日で結婚8年目ので<br>
            &gt;&gt;まだ関係は良好ので<br>
            僕は結婚もしていないし、同棲もしたことないので
            </blockquote>
            </td></tr>
            </table>
            </body>
            </html>
        """.trimIndent()

        val page = runBlocking { ThreadHtmlParserCore.parseThread(html) }
        assertEquals(2, page.posts.size)
        val op = page.posts[0]
        val reply = page.posts[1]

        assertEquals(1, op.referencedCount)
        assertEquals(0, reply.referencedCount)

        val ref = reply.quoteReferences.single()
        assertEquals(listOf("400"), ref.targetPostIds)
        assertTrue(ref.text.contains("結婚8年目"))
    }

    @Test
    fun quoteCounting_partialTextMatchWhenQuotedSubset() {
        val html = """
            <html>
            <body>
            <div class="thre" data-res="500">
            <span class="cnw">25/01/01(月)00:00:00 ID:OP</span><span class="cno">No.500</span>
            <blockquote>
            &gt;ふう、月曜に買ったお肉を回鍋肉としてやっと使い切ったので<br>
            &gt;ジャンボパックはやはり悪…ので
            </blockquote>
            </div>
            <table border=0>
            <tr><td class=rtd>
            <span class="cnw">25/01/01(月)00:02:00 ID:Reply1</span><span class="cno">No.501</span>
            <blockquote>ポリ袋に小分けして冷凍するといいので</blockquote>
            </td></tr>
            </table>
            <table border=0>
            <tr><td class=rtd>
            <span class="cnw">25/01/01(月)00:04:00 ID:Reply2</span><span class="cno">No.502</span>
            <blockquote>
            &gt;小分けして冷凍<br>
            小分けンモー<br>
            冷凍庫になんか母が冷凍したと思われる何年ものかわからぬ小間肉が凍ってるので
            </blockquote>
            </td></tr>
            </table>
            </body>
            </html>
        """.trimIndent()

        val page = runBlocking { ThreadHtmlParserCore.parseThread(html) }
        assertEquals(3, page.posts.size)
        val firstReply = page.posts[1]
        val secondReply = page.posts[2]

        // 部分一致で No.501 が引用されたとみなされる
        assertEquals(1, firstReply.referencedCount)
        assertEquals(0, secondReply.referencedCount)

        val ref = secondReply.quoteReferences.single()
        assertEquals(listOf("501"), ref.targetPostIds)
        assertTrue(ref.text.contains("小分けして冷凍"))
    }

    @Test
    fun parseThread_skipsOversizedReplyBlock_andMarksPageTruncated() {
        val oversizedMessage = "a".repeat(310_000)
        val html = """
            <html>
            <head><link rel="canonical" href="https://www.example.com/t/res/900.htm"></head>
            <body>
            <div class="thre" data-res="900">
            <span class="cnw">25/01/01(月)00:00:00 ID:OP</span><span class="cno">No.900</span>
            <blockquote>op</blockquote>
            </div>
            <table border=0>
            <tr><td class=rtd>
            <span class="cnw">25/01/01(月)00:01:00 ID:BIG</span><span class="cno">No.901</span>
            <blockquote>$oversizedMessage</blockquote>
            </td></tr>
            </table>
            <table border=0>
            <tr><td class=rtd>
            <span class="cnw">25/01/01(月)00:02:00 ID:OK</span><span class="cno">No.902</span>
            <blockquote>通常レス</blockquote>
            </td></tr>
            </table>
            </body>
            </html>
        """.trimIndent()

        val page = runBlocking { ThreadHtmlParserCore.parseThread(html) }

        assertEquals(listOf("900", "902"), page.posts.map { it.id })
        assertTrue(page.isTruncated)
        val truncationReason = assertNotNull(page.truncationReason)
        assertTrue(truncationReason.startsWith("Skipped oversized post block ("))
    }

    @Test
    fun extractOpImageUrl_ignoresUntrustedCanonicalHost() {
        val snippet = """
            <html>
            <head><link rel="canonical" href="https://evil.example/res/777.htm"></head>
            <body>
            <div class="thre">
            <a href="/img/src/777.jpg">777.jpg</a>
            <img src="/img/thumb/777s.jpg">
            </div>
            </body>
            </html>
        """.trimIndent()

        val url = ThreadHtmlParserCore.extractOpImageUrl(snippet, "https://www.example.com/img")

        assertEquals("https://www.example.com/img/src/777.jpg", url)
    }

    @Test
    fun parseThread_resolvesProtocolRelativeVideoUrlsWithoutCanonical() {
        val html = """
            <html>
            <body>
            <div class="thre" data-res="701">
            <span class="cnw">25/01/01(月)00:00:00 ID:VID</span><span class="cno">No.701</span>
            画像ファイル名：<a href="//may.2chan.net/b/src/701.mp4?dl=1">701.mp4</a>
            <img src="//may.2chan.net/b/thumb/701s.jpg?x=1">
            <blockquote>動画つき本文</blockquote>
            </div>
            </body>
            </html>
        """.trimIndent()

        val page = runBlocking { ThreadHtmlParserCore.parseThread(html) }

        assertEquals("701", page.threadId)
        assertEquals(1, page.posts.size)
        val op = page.posts.single()
        assertEquals("https://may.2chan.net/b/src/701.mp4?dl=1", op.imageUrl)
        assertEquals("https://may.2chan.net/b/thumb/701s.jpg?x=1", op.thumbnailUrl)
        assertEquals("ID:VID", op.posterId)
        assertTrue(op.messageHtml.contains("動画つき本文"))
    }

    @Test
    fun parseThread_resolvesFutabaUploaderBareFileNames() {
        val html = """
            <html>
            <body>
            <div class="thre" data-res="801">
            <span class="cnw">25/01/01(月)00:00:00 ID:OP</span><span class="cno">No.801</span>
            <blockquote>あぷ小に置いた<br>fu12345.jpg</blockquote>
            </div>
            <table border=0>
            <tr><td class=rtd>
            <span class="cnw">25/01/01(月)00:01:00 ID:VID</span><span class="cno">No.802</span>
            <blockquote>あぷの動画 f67890.mp4</blockquote>
            </td></tr>
            </table>
            </body>
            </html>
        """.trimIndent()

        val page = runBlocking { ThreadHtmlParserCore.parseThread(html) }

        assertEquals(2, page.posts.size)
        assertEquals("https://dec.2chan.net/up2/src/fu12345.jpg", page.posts[0].imageUrl)
        assertEquals(null, page.posts[0].thumbnailUrl)
        assertEquals("https://dec.2chan.net/up/src/f67890.mp4", page.posts[1].imageUrl)
        assertEquals(null, page.posts[1].thumbnailUrl)
    }

    @Test
    fun parseThread_keepsAttachedMediaOverUploaderBareFileName() {
        val html = """
            <html>
            <body>
            <div class="thre" data-res="803">
            <span class="cnw">25/01/01(月)00:00:00 ID:OP</span><span class="cno">No.803</span>
            画像ファイル名：<a href="/t/src/attached.jpg">attached.jpg</a>
            <img src="/t/thumb/attacheds.jpg">
            <blockquote>本文 fu12345.jpg</blockquote>
            </div>
            </body>
            </html>
        """.trimIndent()

        val page = runBlocking { ThreadHtmlParserCore.parseThread(html) }

        assertEquals("https://www.example.com/t/src/attached.jpg", page.posts.single().imageUrl)
        assertEquals("https://www.example.com/t/thumb/attacheds.jpg", page.posts.single().thumbnailUrl)
    }

    @Test
    fun parseThread_doesNotResolveUploaderFileNameInsideUrlPath() {
        val html = """
            <html>
            <body>
            <div class="thre" data-res="804">
            <span class="cnw">25/01/01(月)00:00:00 ID:OP</span><span class="cno">No.804</span>
            <blockquote>別サイト https://example.com/files/f12345.jpg</blockquote>
            </div>
            </body>
            </html>
        """.trimIndent()

        val page = runBlocking { ThreadHtmlParserCore.parseThread(html) }

        assertEquals(null, page.posts.single().imageUrl)
    }

    @Test
    fun extractOpImageUrl_resolvesProtocolRelativeThumbnailForVideo() {
        val snippet = """
            <html>
            <body>
            <div class="thre" data-res="702">
            画像ファイル名：<a href="//may.2chan.net/b/src/702.webm">702.webm</a>
            <img src="//may.2chan.net/b/thumb/702s.jpg?foo=1">
            </div>
            </body>
            </html>
        """.trimIndent()

        val url = ThreadHtmlParserCore.extractOpImageUrl(snippet, "https://may.2chan.net/b")

        assertEquals("https://may.2chan.net/b/thumb/702s.jpg?foo=1", url)
    }

    @Test
    fun parseThread_resolvesRelativeArchiveMediaAgainstFetchUrl() {
        val html = """
            <html>
            <head><link rel="canonical" href="https://may.2chan.net/b/res/1415555296.htm"></head>
            <body>
            <div class="thre" data-res="1415555296">
            画像ファイル名：<a href="/b/src/1234567890.jpg" target="_blank">1234567890.jpg</a>
            <img src="/b/thumb/1234567890s.jpg">
            <span class="cnw">25/01/01(月)00:00:00 ID:OP</span><span class="cno">No.1415555296</span>
            <blockquote>archive body</blockquote>
            </div>
            </body>
            </html>
        """.trimIndent()

        val page = runBlocking {
            ThreadHtmlParserCore.parseThread(
                html,
                baseUrl = "https://may.inqueuet.com/b/res/1415555296.htm"
            )
        }

        assertEquals("https://may.inqueuet.com/b/src/1234567890.jpg", page.posts.single().imageUrl)
        assertEquals("https://may.inqueuet.com/b/thumb/1234567890s.jpg", page.posts.single().thumbnailUrl)
    }

    @Test
    fun parseThread_archiveApuBodyLinkUsesCanonicalUploaderMediaOnlyOnce() {
        val html = """
            <html><body>
            <div class="thre" data-res="1415555300">
              <span class="cnw">25/01/01(月)00:00:00 ID:OP</span><span class="cno">No.1415555300</span>
              <blockquote><a href="/cache/fu7190971.png">fu7190971.png</a><span onclick="previewImg('body','/cache/fu7190971.png')">[見る]</span><br>本文</blockquote>
            </div>
            </body></html>
        """.trimIndent()

        val page = runBlocking {
            ThreadHtmlParserCore.parseThread(
                html,
                baseUrl = "https://may.inqueuet.com/b/res/1415555300.htm"
            )
        }

        assertEquals("https://dec.2chan.net/up2/src/fu7190971.png", page.posts.single().imageUrl)
        assertEquals(null, page.posts.single().thumbnailUrl)
        assertEquals(
            "<a href=\"/cache/fu7190971.png\">fu7190971.png</a><br>本文",
            page.posts.single().messageHtml
        )
    }

    @Test
    fun parseThread_archiveApuQuoteLinkIsNotPromotedToReplyMedia() {
        val html = """
            <html><body>
            <div class="thre" data-res="1415555301">
              <span class="cnw">25/01/01(月)00:00:00 ID:OP</span><span class="cno">No.1415555301</span>
              <blockquote><font color="#789922">&gt;<a href="/cache/fu7190971.png">fu7190971.png</a><span onclick="previewImg('quote','/cache/fu7190971.png')">[見る]</span></font><br>返信</blockquote>
            </div>
            </body></html>
        """.trimIndent()

        val page = runBlocking {
            ThreadHtmlParserCore.parseThread(
                html,
                baseUrl = "https://may.inqueuet.com/b/res/1415555301.htm"
            )
        }

        assertEquals(null, page.posts.single().imageUrl)
        assertEquals(null, page.posts.single().thumbnailUrl)
        assertEquals(
            "<font color=\"#789922\">&gt;<a href=\"/cache/fu7190971.png\">fu7190971.png</a></font><br>返信",
            page.posts.single().messageHtml
        )
    }
}

private val sampleThreadHtml = """
    <html>
    <head>
    <link rel="canonical" href="https://www.example.com/t/res/354621.htm">
    </head>
    <body>
    <span id="tit">料理＠ふたば</span>
    <span id="contdisp">1月18日頃消えます</span>
    <div class="thre" data-res="354621">
    <span class="csb">スレタイ</span>Name<span class="cnm">名無し</span>
    <span class="cnw">25/11/03(日)13:47:04 ID:IDOP</span><span class="cno">No.354621</span>
    画像ファイル名：<a href="/t/src/1762145224666.jpg">1762145224666.jpg</a>
    <img src="/t/thumb/1762145224666s.jpg">
    <a class="sod" id="sd354621">そうだねx1</a>
    <blockquote>本文<br>2行目</blockquote>
    </div>
    <span id=ddel>削除された記事が<span id=ddnum>1</span>件あります.<span id=ddbut>見る</span><br></span>
    <table border=0>
    <tr><td class=rtd>
    <span id="delcheck354622" class="rsc">1</span>
    <span class="csb">無題</span>Name<span class="cnm">テスト</span>
    <span class="cnw">25/11/03(日)14:01:10 ID:IDA1</span><span class="cno">No.354622</span>
    <a class="sod" id="sd354622">そうだねx5</a>
    <blockquote>返信1</blockquote>
    </td></tr>
    </table>
    <table border=0 class="deleted">
    <tr><td class=rtd>
    <span id="delcheck354652" class="rsc">5</span>
    <span class="csb">画像レス</span>Name<span class="cnm">名無しさん</span>
    <span class="cnw">25/11/04(月)17:53:15 ID:IDIMG</span><span class="cno">No.354652</span>
    <br> &nbsp; &nbsp; <a href="/t/src/1762246395132.png">1762246395132.png</a>
    <img src="/t/thumb/1762246395132s.jpg">
    <a class="sod" id="sd354652">+</a>
    <blockquote style="margin-left:240px;">画像本文<br>&gt;&gt;No.354621<br>&gt;ID:IDA1<br>&gt;返信1<br>&gt;本文</blockquote>
    </td></tr>
    </table>
    </body>
    </html>
""".trimIndent()
