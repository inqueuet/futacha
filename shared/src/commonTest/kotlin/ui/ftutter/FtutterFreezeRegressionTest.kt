package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.QuoteReference
import com.valoser.futacha.shared.ui.board.buildThreadTreeNodes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * docs/code-audit-2026-10-14.md の A4〜A7（ふたったー風の固まり）の回帰試験。以前の計算（このファイルの`old…`）と
 * 結果が同じこと、計算量・時間が抑えられていることを確かめる。
 */
class FtutterFreezeRegressionTest {
    // ---- 以前の実装（比べるための写し） ----

    private fun oldQuoteTargets(posts: List<Post>, index: Int): List<Int> {
        val position = posts.withIndex().associate { (i, p) -> p.id to i }
        return posts[index].quoteReferences.flatMap { it.targetPostIds }.mapNotNull { position[it] }
            .filter { it < index }.distinct()
    }

    private fun oldConversationGroups(posts: List<Post>): List<Set<Int>> {
        val parent = IntArray(posts.size) { it }
        fun find(x: Int): Int {
            var r = x
            while (parent[r] != r) r = parent[r]
            var c = x
            while (parent[c] != r) { val n = parent[c]; parent[c] = r; c = n }
            return r
        }
        posts.indices.forEach { i -> oldQuoteTargets(posts, i).forEach { t -> parent[find(i)] = find(t) } }
        return posts.indices.groupBy { find(it) }.values.map { it.toSet() }
    }

    private suspend fun oldConversationOf(posts: List<Post>, index: Int): List<Pair<Int, Int>> {
        val group = oldConversationGroups(posts).firstOrNull { index in it } ?: return emptyList()
        if (group.size < 2) return emptyList()
        val position = posts.withIndex().associate { (i, p) -> p.id to i }
        return buildThreadTreeNodes(group.sorted().map { posts[it] }).map { position.getValue(it.post.id) to it.depth }
    }

    private fun oldThin(rows: List<FtutterRow>): List<FtutterRow> {
        var current: List<FtutterRow> = rows
        while (true) {
            val next = current.filterIndexed { at, row ->
                if (row !is FtutterRow.HiddenRow) return@filterIndexed true
                val following = current.drop(at + 1).firstOrNull { it !is FtutterRow.ReadMark }
                val depth = (following as? FtutterRow.PostRow)?.depth ?: (following as? FtutterRow.HiddenRow)?.depth
                depth != null && depth > row.depth
            }
            if (next.size == current.size) return next
            current = next
        }
    }

    // ---- 試験のデータ ----

    private class CountingList(private val inner: List<Post>) : AbstractList<Post>() {
        var reads = 0L
        override val size: Int get() = inner.size
        override fun get(index: Int): Post {
            reads++
            return inner[index]
        }
    }

    private fun post(id: String, quotes: List<String> = emptyList(), body: String = "本文$id", deleted: Boolean = false, posterId: String? = null) = Post(
        id = id, author = "としあき", subject = null, timestamp = "", posterId = posterId,
        messageHtml = (quotes.map { "&gt;No.$it" } + body).joinToString("<br>"), imageUrl = null, thumbnailUrl = null,
        isDeleted = deleted, saidaneLabel = null,
        quoteReferences = quotes.map { QuoteReference(">No.$it", listOf(it)) }
    )

    /** 引用が前・後ろ・自分・存在しない番号を指し、番号が重複することもあるスレ。 */
    private fun randomThread(random: Random, size: Int): List<Post> = (0 until size).map { i ->
        val id = if (i > 3 && random.nextInt(40) == 0) "${1000 + random.nextInt(i)}" else "${1000 + i}"
        val quotes = (0 until random.nextInt(4)).map {
            when (random.nextInt(10)) {
                0 -> "${1000 + i + 1 + random.nextInt(5)}"
                1 -> "${1000 + i}"
                2 -> "9${random.nextInt(100)}"
                else -> "${1000 + random.nextInt(i + 1)}"
            }
        }
        val words = listOf("ねこ", "いぬ", "ネコ", "ｎｅｋｏ", "abc", "あいう")
        post(id, quotes, body = "本文 ${words[random.nextInt(words.size)]} $i", deleted = random.nextInt(15) == 0,
            posterId = "ID:${random.nextInt(8)}")
    }

    // ---- A4：会話の木 ----

    @Test
    fun conversationGroupsReadThePostsLinearly() {
        val n = 2000
        val posts = CountingList((0 until n).map { i -> post("${1000 + i}", if (i > 0) listOf("${1000 + i / 2}") else emptyList()) })
        ftutterConversationGroups(posts)
        // 以前は全レス×全レス（2000レスで約400万回）読んでいた。
        assertTrue(posts.reads <= 10L * n, "reads=${posts.reads}")
    }

    @Test
    fun conversationGroupsQuoteTargetsAndTreesAreTheSameAsBefore() = runBlocking {
        val random = Random(20261014)
        repeat(40) { round ->
            val posts = randomThread(random, 1 + random.nextInt(120))
            assertEquals(oldConversationGroups(posts), ftutterConversationGroups(posts), "round $round")
            posts.indices.forEach { i ->
                assertEquals(oldQuoteTargets(posts, i), ftutterQuoteTargets(posts, i), "round $round post $i")
                assertEquals(oldConversationOf(posts, i), ftutterConversationOf(posts, i), "round $round post $i")
            }
            assertEquals(
                ftutterPopular(posts, 2, 1).let { it.main to it.related },
                ftutterPopular(posts.toList(), 2, 1).let { it.main to it.related }
            )
        }
    }

    @Test
    fun theIndexIsBuiltOncePerPostList() {
        val posts = randomThread(Random(3), 50)
        assertTrue(ftutterThreadIndexOf(posts) === ftutterThreadIndexOf(posts))
        assertFalse(ftutterThreadIndexOf(posts) === ftutterThreadIndexOf(posts.toList()))
    }

    // ---- A5：スレ内検索・ツリーの置換行 ----

    @Test
    fun thinningTheReplacementRowsInOnePassMatchesTheRepeatedPasses() {
        val random = Random(7)
        repeat(2000) {
            val rows = (0 until random.nextInt(30)).map { i ->
                when (random.nextInt(5)) {
                    0 -> FtutterRow.ReadMark
                    1, 2 -> FtutterRow.HiddenRow(i, random.nextInt(4), isNg = random.nextBoolean())
                    else -> FtutterRow.PostRow(i, random.nextInt(4))
                }
            }
            assertEquals(oldThin(rows), ftutterThinReplacementRows(rows), "rows=$rows")
        }
    }

    @Test
    fun theSearchUsesTheCachedTextsWithTheSameResult() = runBlocking {
        val random = Random(11)
        repeat(20) {
            val posts = randomThread(random, 1 + random.nextInt(80))
            val texts = ftutterBuildPostTexts(null, posts)
            listOf("ねこ", "ネコ", " neko ", "ABC", "本文", "zzz", "").forEach { query ->
                listOf(false, true).forEach { tree ->
                    val settings = FtutterThreadSettings(isTree = tree)
                    val filter = FtutterThreadFilter(query = query)
                    val hidden = ftutterHidden(posts, settings, emptySet(), ngChain = false)
                    val withTexts = ftutterBuildRows(posts, settings, filter, 3, hidden, texts)
                    val without = ftutterBuildRows(posts, settings, filter, 3, hidden)
                    assertEquals(without, withTexts, "query=$query tree=$tree")
                    // 以前の判定（ftutterMatchesQuery）と同じレスが残る。
                    val expected = posts.indices.filter { query.isBlank() || ftutterMatchesQuery(posts[it], query) }
                        .filter { it !in hidden.deleted }
                    if (!tree) assertEquals(expected, without.filterIsInstance<FtutterRow.PostRow>().map { it.index })
                }
            }
        }
    }

    @Test
    fun theTextsOfAThreadThatOnlyGrewAreReused() {
        val posts = randomThread(Random(5), 30)
        val first = ftutterBuildPostTexts(null, posts)
        val grown = posts + randomThread(Random(6), 35).drop(30)
        val second = ftutterBuildPostTexts(first, grown)
        assertEquals(grown.map(::ftutterPostSearchText), second.texts)
        (0 until 30).forEach { assertTrue(first.texts[it] === second.texts[it]) }
    }

    @Test
    fun theThreadWorkGivesTheSameRowsAsTheScreenUsedToWorkOut() = runBlocking {
        val random = Random(99)
        repeat(30) { round ->
            val posts = randomThread(random, 1 + random.nextInt(60))
            val rules = listOf(
                FtutterNgRule("いぬ", FtutterNgType.WORD.stored),
                FtutterNgRule("ID:3", FtutterNgType.ID.stored),
                FtutterNgRule("本文 a.c", FtutterNgType.WORD.stored, isReg = true)
            ).filter { random.nextBoolean() }
            val matcher = FtutterNgMatcher(rules, null)
            val words = if (random.nextBoolean()) listOf("あいう") else emptyList()
            val headers = if (random.nextBoolean()) listOf("ID:5") else emptyList()
            val extra = if (random.nextBoolean()) setOf(posts.random(random).id) else emptySet()
            val settings = FtutterThreadSettings(isTree = random.nextBoolean(), deletedChain = random.nextBoolean())
            val chain = random.nextBoolean()
            val filter = FtutterThreadFilter(popular = random.nextInt(4) == 0, query = listOf("", "ねこ", "本文")[random.nextInt(3)])
            // 以前の画面の計算。
            val common = ftutterCommonNgPosts(posts, words, headers)
            val ngPosts = posts.indices.filterTo(common.toMutableSet()) { matcher.hidesPost(posts[it]) || posts[it].id in extra }
            val hidden = ftutterHidden(posts, settings, ngPosts, chain)
            val expected = ftutterBuildRows(posts, settings, filter, 5, hidden)

            val work = FtutterThreadWork()
            val view = work.view(posts, settings, filter, 5, matcher, words, headers, extra, chain)
            assertEquals(hidden, view.hidden, "round $round")
            assertEquals(expected, view.rows, "round $round")
            // 画像NG・AIの結果が届いても、レスごとの照合はやり直さずに同じ結果になる。
            val more = extra + posts.first().id
            val ngAgain = posts.indices.filterTo(common.toMutableSet()) { matcher.hidesPost(posts[it]) || posts[it].id in more }
            assertEquals(ftutterHidden(posts, settings, ngAgain, chain), work.view(posts, settings, filter, 5, matcher, words, headers, more, chain).hidden)
        }
    }

    // ---- A6：NGの正規表現 ----

    private val slowPattern = "(a+){1,30}b"

    @Test
    fun theDeadlineStopsACatastrophicRegexOnThisPlatform() {
        val text = "a".repeat(40) + "!"
        val started = TimeSource.Monotonic.markNow()
        assertFailsWith<FtutterRegexTimeoutException> {
            Regex(slowPattern).containsMatchIn(FtutterDeadlineCharSequence(text, TimeSource.Monotonic.markNow() + 100.milliseconds))
        }
        assertTrue(started.elapsedNow() < 2_000.milliseconds, "took ${started.elapsedNow()}")
        // 普通の照合は包んでも同じ結果。
        listOf("ねこ" to "白いねこ", "^a+$" to "aaa", "b\\d{2}" to "ab12", "x" to "abc").forEach { (pattern, input) ->
            assertEquals(
                Regex(pattern).containsMatchIn(input),
                Regex(pattern).containsMatchIn(FtutterDeadlineCharSequence(input, TimeSource.Monotonic.markNow() + 1_000.milliseconds))
            )
        }
    }

    @Test
    fun theDeadlineAlsoLooksAtCancellation() {
        assertFailsWith<CancellationException> {
            Regex(slowPattern).containsMatchIn(
                FtutterDeadlineCharSequence("a".repeat(40) + "!", TimeSource.Monotonic.markNow() + 60_000.milliseconds, { throw CancellationException("stop") })
            )
        }
    }

    @Test
    fun aCatastrophicNgRegexEndsInTimeAndIsReportedAndDropped() {
        // 正規表現の入れ子の繰り返し（JVM・Kotlin/Native のどちらでも指数時間になる）。題名・本文・ピックアップで使う。
        val pattern = "(x+){1,30}y"
        val rule = FtutterNgRule(pattern, FtutterNgType.WORD.stored, isReg = true)
        val normal = FtutterNgRule("ねこ$", FtutterNgType.WORD.stored, isReg = true)
        val matcher = FtutterNgMatcher(listOf(rule, normal), null)
        val slow = post("1", body = "x".repeat(40) + "!")
        val started = TimeSource.Monotonic.markNow()
        assertFalse(matcher.hidesPost(slow))
        assertTrue(started.elapsedNow() < 3_000.milliseconds, "took ${started.elapsedNow()}")
        assertEquals(listOf(rule), matcher.slowRules)
        assertTrue(ftutterIsSlowNgPattern(pattern))
        assertEquals("照合に時間がかかるため使っていません", ftutterNgRuleProblem(rule))
        assertNotNull(ftutterSlowNgNotice(matcher.slowRules))
        // 以後は使わない（すぐ終わる）。普通のルールは働き続ける。
        val again = TimeSource.Monotonic.markNow()
        repeat(50) { assertFalse(matcher.hidesPost(slow)) }
        assertTrue(again.elapsedNow() < 1_000.milliseconds)
        assertTrue(matcher.hidesPost(post("2", body = "白いねこ")))
        assertFalse(matcher.hidesPost(post("3", body = "ねこです")))
    }

    @Test
    fun aRuleThatIsSlowOverManyPostsStopsAtItsBudget() {
        // 1回は制限内でも、合計が予算を超えたら止める。
        val pattern = "(z+){1,30}w"
        val matcher = FtutterNgMatcher(
            listOf(FtutterNgRule(pattern, FtutterNgType.TITLE.stored, isReg = true)), null,
            matchLimit = 30.milliseconds, ruleBudget = 100.milliseconds
        )
        val started = TimeSource.Monotonic.markNow()
        repeat(200) { assertFalse(matcher.hidesTitle("z".repeat(40) + "!")) }
        assertTrue(started.elapsedNow() < 2_000.milliseconds, "took ${started.elapsedNow()}")
        assertTrue(ftutterIsSlowNgPattern(pattern))
    }

    /** 再監査M6：時間の予算は照合のまとまりごと。更新を何度繰り返しても、普通の正規表現は止められない。 */
    @Test
    fun aNormalRegexKeepsWorkingOverManyRefreshes() {
        val pattern = "ねこ[0-9]+号M6"
        val matcher = FtutterNgMatcher(
            listOf(FtutterNgRule(pattern, FtutterNgType.WORD.stored, isReg = true)), null,
            matchLimit = 30.milliseconds, ruleBudget = 20.milliseconds
        )
        val posts = (1..20).map { post("$it", body = if (it == 7) "ねこ12号M6です" else "ふつうの本文 $it ".repeat(300)) }
        // 1回の更新はすぐ終わるが、合計は予算を大きく超える。
        repeat(3_000) { ftutterNgPostIndexes(posts, matcher, emptyList(), emptyList()) }
        assertFalse(ftutterIsSlowNgPattern(pattern))
        assertEquals(setOf(6), ftutterNgPostIndexes(posts, matcher, emptyList(), emptyList()))
        assertEquals(setOf(6), ftutterNgHiddenPosts(posts, matcher, chain = false))
    }

    @Test
    fun normalRulesMatchAsBefore() {
        val rules = listOf(
            FtutterNgRule("ﾈｺ", FtutterNgType.WORD.stored),
            FtutterNgRule("^いぬ\\d+", FtutterNgType.WORD.stored, isReg = true),
            FtutterNgRule("ID:abc", FtutterNgType.ID.stored),
            FtutterNgRule("荒らし", FtutterNgType.TITLE.stored),
            FtutterNgRule("[0-9]{3}円", FtutterNgType.TITLE.stored, isReg = true),
            FtutterNgRule("ピック", FtutterNgType.PICKUP.stored)
        )
        val matcher = FtutterNgMatcher(rules, null)
        assertTrue(matcher.hidesPost(post("1", body = "かわいいねこ")))
        assertTrue(matcher.hidesPost(post("2", body = "いぬ12")))
        assertFalse(matcher.hidesPost(post("3", body = "あいぬ12")))
        assertTrue(matcher.hidesPost(post("4", body = "x", posterId = "ID:abc")))
        assertTrue(matcher.hidesTitle("荒らしスレ"))
        assertTrue(matcher.hidesTitle("100円"))
        assertFalse(matcher.hidesTitle("10円"))
        assertEquals("ピック", matcher.pickupWord("ピックのスレ"))
        assertTrue(matcher.slowRules.isEmpty())
        rules.forEach { assertNull(ftutterNgRuleProblem(it)) }
    }

    @Test
    fun aCatastrophicRegexIsRefusedAtRegistrationButNormalOnesAreNot() {
        assertEquals(FTUTTER_NG_REGEX_TOO_SLOW, ftutterNgValidationError(FtutterNgRule("(q+){1,30}r", FtutterNgType.WORD.stored, isReg = true), emptyList()))
        assertEquals(FTUTTER_NG_REGEX_INVALID, ftutterNgValidationError(FtutterNgRule("[", FtutterNgType.WORD.stored, isReg = true), emptyList()))
        listOf("^ねこ.*$", "a+b", "\\d{3}", "(いぬ|ねこ)+", ".*", "^(\\s*)$").forEach { pattern ->
            assertNull(ftutterNgValidationError(FtutterNgRule(pattern, FtutterNgType.WORD.stored, isReg = true), emptyList()), pattern)
        }
        // 正規表現でないルールは試さない。
        assertNull(ftutterNgValidationError(FtutterNgRule("(q+){1,30}r", FtutterNgType.WORD.stored), emptyList()))
    }

    /**
     * 再監査H1：Androidの正規表現は入力を`String`にしてネイティブへ渡すので、期限の包みが効かない。包まずに（Androidと同じく
     * 途中で止められない形で）試しても、長さを少しずつ伸ばすので、遅いパターンを短い時間で見つけて止まる。
     */
    @Test
    fun aCatastrophicRegexIsFoundQuicklyEvenWhenTheEngineCannotBeInterrupted() {
        val plain: (String, kotlin.time.TimeMark) -> CharSequence = { probe, _ -> probe }
        // JDKは`(x+x+)+y`・`(\\w+\\s?)+$`の繰り返しを内部で覚えて速く終えるので、どの環境でも遅いものを使う
        // （Androidの正規表現での動きは計装試験`aCatastrophicRegexIsRefusedWithoutFreezingOnAndroid`で確かめる）。
        listOf("(q+){1,30}r", "(r+){1,40}s").forEach { pattern ->
            val started = kotlin.time.TimeSource.Monotonic.markNow()
            assertTrue(ftutterRegexIsTooSlow(Regex(pattern), pattern, input = plain), pattern)
            assertTrue(started.elapsedNow() < 3.seconds, "$pattern took ${started.elapsedNow()}")
        }
        listOf("^ねこ.*$", "a+b", "\\d{3}", "(いぬ|ねこ)+", ".*", "^(\\s*)$").forEach { pattern ->
            assertFalse(ftutterRegexIsTooSlow(Regex(pattern), pattern, input = plain), pattern)
        }
    }

    @Test
    fun theCatalogTitleResultsAreTheSameWithThePrecomputedTable() {
        val random = Random(31)
        val titles = listOf("ねこスレ", "荒らし", "ピックアップ", "100円", "いぬ", "", "ネコ荒らし")
        val items = (0 until 60).map { i ->
            CatalogItem(id = "$i", threadUrl = "https://may.2chan.net/b/res/$i.htm", title = titles[random.nextInt(titles.size)].ifEmpty { null },
                thumbnailUrl = null, fullImageUrl = null, replyCount = i)
        }
        val matcher = FtutterNgMatcher(listOf(
            FtutterNgRule("荒らし", FtutterNgType.TITLE.stored),
            FtutterNgRule("[0-9]+円", FtutterNgType.TITLE.stored, isReg = true),
            FtutterNgRule("ピック", FtutterNgType.PICKUP.stored),
            FtutterNgRule("ねこ", FtutterNgType.PICKUP.stored)
        ), null)
        val common = listOf("いぬ")
        val table = ftutterCatalogTitleNg(items, matcher, common)
        assertTrue(table.isFor(items, matcher, common))
        FtutterOrder.entries.forEach { order ->
            listOf(emptySet(), FtutterOrder.entries.toSet()).forEach { pickupOrders ->
                val before = ftutterApplyCommonCatalogNg(ftutterApplyCatalogNg(items, matcher, order, pickupOrders), common)
                val after = ftutterApplyCommonCatalogNg(ftutterApplyCatalogNg(items, matcher, order, pickupOrders, table.byTitle), common, table.byTitle)
                assertEquals(before, after)
            }
        }
    }

    @Test
    fun anImportedBrokenOrSlowRegexIsNotImported() {
        val list = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIGxpc3Qu!"
        val items = listOf(
            """{"text":"[","type":1,"isReg":true}""",
            """{"text":"(w+){1,30}v","type":0,"isReg":true}""",
            """{"text":"ok","type":1}""",
            """{"text":"ok","type":1}""",
            """{"text":"^ねこ$","type":0,"isReg":true}"""
        )
        val body = (list + "[" + items.joinToString(",") { "\"" + it.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" } + "]")
            .replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")
        val xml = "<map><string name=\"flutter.NGList\">$body</string></map>"
        val plan = ftutterBackupPlan(parseFtutterPreferencesXml(xml), emptyList<BoardSummary>(), emptyList(), emptyMap(), 0)
        val saved = parseFtutterNgList(plan.preferences[FtutterKeys.NG_LIST])
        assertEquals(listOf(FtutterNgRule("ok", 1), FtutterNgRule("^ねこ$", 0, isReg = true)), saved)
        assertEquals(2, plan.ngCount)
        assertEquals(2, plan.rejectedNg)
        assertTrue(plan.summary.contains("使えないNG 2件"))
    }

    // ---- A7：.ftt の取り込み ----

    private fun oldCtr(aes: FtutterAes, data: ByteArray, iv: ByteArray): ByteArray {
        val counter = iv.copyOf()
        val out = ByteArray(data.size)
        var offset = 0
        while (offset < data.size) {
            val stream = aes.encryptBlock(counter)
            val n = minOf(16, data.size - offset)
            for (i in 0 until n) out[offset + i] = (data[offset + i].toInt() xor stream[i].toInt()).toByte()
            offset += n
            for (i in 15 downTo 0) {
                val v = (counter[i].toInt() and 0xFF) + 1
                counter[i] = v.toByte()
                if (v <= 0xFF) break
            }
        }
        return out
    }

    @Test
    fun theCtrWithReusedBuffersIsByteIdentical() {
        val random = Random(42)
        listOf(16, 24, 32).forEach { keySize ->
            val aes = FtutterAes(random.nextBytes(keySize))
            listOf(0, 1, 15, 16, 17, 255, 4096, 70_001).forEach { size ->
                val data = random.nextBytes(size)
                // 末尾が FF のカウンタ（繰り上がり）も含める。
                listOf(random.nextBytes(16), ByteArray(16) { if (it < 8) 0x12 else 0xFF.toByte() }, ByteArray(16) { 0xFF.toByte() }).forEach { iv ->
                    val expected = oldCtr(aes, data, iv)
                    assertContentEquals(expected, aes.ctr(data, iv), "key=$keySize size=$size")
                    (0 until (size + 15) / 16).filter { it < 3 || it > (size + 15) / 16 - 3 || it % 97 == 0 }.forEach { block ->
                        assertContentEquals(expected.copyOfRange(block * 16, minOf(size, block * 16 + 16)), aes.ctrBlock(data, iv, block), "block $block")
                    }
                }
            }
        }
    }

    @Test
    fun anObviouslyWrongFileIsRefusedBeforeDecryptingAllOfIt() {
        val key = ByteArray(32) { it.toByte() }
        val iv = ByteArray(16) { (it * 3).toByte() }
        var checks = 0
        val random = Random(1)
        // 鍵の違うファイル・無関係なファイル（4MB）：末尾か先頭の1ブロックで断り、全体の復号（取り消しの確認が呼ばれる）に進まない。
        repeat(20) {
            assertNull(ftutterDecryptBackup(random.nextBytes(4 * 1024 * 1024 / 16 * 16), key, iv) { checks++ })
        }
        assertEquals(0, checks)
        // 詰め物は正しいが中身が XML でないもの。
        val notXml = "hello world, this is not a backup".encodeToByteArray()
        val pad = 16 - notXml.size % 16
        assertNull(ftutterDecryptBackup(FtutterAes(key).ctr(notXml + ByteArray(pad) { pad.toByte() }, iv), key, iv) { checks++ })
        assertEquals(0, checks)
        // 正しいファイルは読める（BOM と空白の後の`<`も）。
        val xml = "﻿\n <?xml version='1.0'?><map></map>"
        val plain = xml.encodeToByteArray()
        val padding = 16 - plain.size % 16
        assertEquals(xml, ftutterDecryptBackup(FtutterAes(key).ctr(plain + ByteArray(padding) { padding.toByte() }, iv), key, iv))
    }

    @Test
    fun decryptingCanBeCancelled() {
        val key = ByteArray(32) { it.toByte() }
        val iv = ByteArray(16)
        val plain = ("<?xml version='1.0'?><map>" + "x".repeat(200_000) + "</map>").encodeToByteArray()
        val pad = 16 - plain.size % 16
        val encrypted = FtutterAes(key).ctr(plain + ByteArray(pad) { pad.toByte() }, iv)
        assertFailsWith<CancellationException> {
            ftutterDecryptBackup(encrypted, key, iv) { throw CancellationException("stop") }
        }
    }

    /**
     * 再監査M4：過去ログでレスが減った直後は、行の組み立てが追いつくまで古い行が残る。画面は行とレスを同じ組み立ての結果
     * （[FtutterThreadView.posts]）から読むので、古い行の番号でも外れない（以前は新しいレスの並びで読んで落ちた）。
     */
    @Test
    fun theRowsAndThePostsOfAViewStayTogetherWhenTheThreadShrinks() = runBlocking<Unit> {
        val long = (1..20).map { post("$it") }
        val short = long.take(5)
        val work = FtutterThreadWork()
        val matcher = FtutterNgMatcher(emptyList(), null)
        val settings = FtutterThreadSettings()
        val stale = work.view(long, settings, FtutterThreadFilter(), 0, matcher, emptyList(), emptyList(), emptySet(), false)
        val indexes = stale.rows.mapNotNull { (it as? FtutterRow.PostRow)?.index ?: (it as? FtutterRow.HiddenRow)?.index }
        // 古い行は、新しい（短い）レスの並びの外を指す。
        assertTrue(indexes.any { it >= short.size })
        // 同じ組み立てのレスなら、どの行も読める。
        assertEquals(indexes.map { long[it].id }, indexes.map { stale.posts[it].id })
        val fresh = work.view(short, settings, FtutterThreadFilter(), 0, matcher, emptyList(), emptyList(), emptySet(), false)
        assertTrue(fresh.rows.all { row -> ((row as? FtutterRow.PostRow)?.index ?: 0) < fresh.posts.size })
    }

    /** Re-audit L26: popups stay while posts are only added, and are closed when the positions mean other posts. */
    @Test
    fun popupsStayOnlyWhilePostsOnlyGrow() {
        val three = (1..3).map { post("$it") }
        assertTrue(ftutterPostsOnlyGrew(three, three + post("4")))
        assertTrue(ftutterPostsOnlyGrew(three, three))
        assertFalse(ftutterPostsOnlyGrew(three, three.take(2)))
        assertFalse(ftutterPostsOnlyGrew(three, listOf(post("9")) + three))
    }
}
