package com.valoser.futacha.shared.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ThreadHtmlParserCoreTest {
    @Test
    fun parseThread_extractsThreadAndPosts() {
        val page = ThreadHtmlParserCore.parseThread(sampleThreadHtml)

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
    fun parseThread_handlesLegacyCntdClassSpan() {
        val legacyHtml = sampleThreadHtml.replace("id=\"contdisp\"", "class=\"cntd\"")
        val page = ThreadHtmlParserCore.parseThread(legacyHtml)

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

        val page = ThreadHtmlParserCore.parseThread(html)

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

        val page = ThreadHtmlParserCore.parseThread(html)

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

        val page = ThreadHtmlParserCore.parseThread(html)
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

        val page = ThreadHtmlParserCore.parseThread(html)
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

        val page = ThreadHtmlParserCore.parseThread(html)
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
