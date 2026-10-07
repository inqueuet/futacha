package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.PostDeletionKind
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.model.postDeletionKind
import com.valoser.futacha.shared.model.threadDeletionSummary
import com.valoser.futacha.shared.util.canonicalCp932Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.coroutineContext
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The NG / search / deletion-summary speed-ups return exactly what the previous implementations
 * returned (kept below as references; they fold CP932 aliases and full-width ASCII only), and the kana
 * folding added to the search text keeps the length and does not widen matches beyond
 * hiragana/katakana (and half-width katakana) of the same sound.
 */
class ThreadFilterPerformanceEquivalenceTest {
    // ---- the previous implementations, verbatim in behaviour ----

    // The previous canonical text: CP932 aliases unified, then full-width ASCII folded (no kana folding).
    private fun legacyCanonical(text: String): String = foldFullWidthAscii(canonicalCp932Text(text))

    private fun legacyCanonicalNgFilters(values: List<String>): List<String> =
        values.mapNotNull { value ->
            value.trim().takeIf { it.isNotBlank() }?.lowercase()?.let(::legacyCanonical)
        }

    private fun legacyMatchesNg(
        post: Post,
        headerFilters: List<String>,
        wordFilters: List<String>,
        lowerBodyByPost: Map<Post, String>
    ): Boolean {
        if (headerFilters.isNotEmpty()) {
            val headerText = legacyCanonical(buildPostHeaderText(post))
            if (headerFilters.any { headerText.contains(legacyCanonical(it)) }) return true
        }
        if (wordFilters.isNotEmpty()) {
            val bodyText = legacyCanonical(lowerBodyByPost[post] ?: "")
            if (wordFilters.any { bodyText.contains(legacyCanonical(it)) }) return true
        }
        return false
    }

    private fun legacyApplyNg(page: ThreadPage, headers: List<String>, words: List<String>): List<Post> {
        val headerFilters = legacyCanonicalNgFilters(headers)
        val wordFilters = legacyCanonicalNgFilters(words)
        if (headerFilters.isEmpty() && wordFilters.isEmpty()) return page.posts
        val lower = if (wordFilters.isEmpty()) emptyMap() else buildLowerBodyByPost(page.posts)
        return page.posts.filterNot { legacyMatchesNg(it, headerFilters, wordFilters, lower) }
    }

    // ---- random input without katakana (where the folding is the same as the previous one) ----

    private val chunks = listOf(
        "あ", "い", "猫", "犬", "ng", "NG", "Ｎ", "Ｇ", "〜", "～", "−", "－", "‖", "∥", "—", "―",
        "a", "B", "１", "2", "　", " ", "ー", "<br>", "&gt;", "http://x.y/a", "ID:abc", "ｚ", "Ｙ"
    )
    private val wordChunks = chunks.filter { it != "<br>" && it != "&gt;" }

    private fun randomText(random: Random, source: List<String>, min: Int, max: Int): String =
        (0 until random.nextInt(min, max + 1)).joinToString("") { source[random.nextInt(source.size)] }

    private fun randomPosts(random: Random, count: Int): List<Post> = (1..count).map { index ->
        Post(
            id = index.toString(),
            author = if (random.nextInt(4) == 0) null else randomText(random, wordChunks, 1, 3),
            subject = if (random.nextInt(3) == 0) null else randomText(random, wordChunks, 1, 3),
            timestamp = "25/01/01(水)00:00:00",
            posterId = if (random.nextInt(3) == 0) null else "ID:" + randomText(random, wordChunks, 1, 2),
            messageHtml = randomText(random, chunks, 1, 12),
            imageUrl = if (random.nextInt(3) == 0) "https://img.example/$index.jpg" else null,
            thumbnailUrl = null,
            isDeleted = random.nextInt(6) == 0
        )
    }

    private fun plainPost(id: String, body: String) = Post(
        id = id,
        author = null,
        subject = null,
        timestamp = "t",
        messageHtml = body,
        imageUrl = null,
        thumbnailUrl = null
    )

    private fun page(posts: List<Post>) = ThreadPage(
        threadId = "1",
        boardTitle = null,
        expiresAtLabel = null,
        deletedNotice = null,
        posts = posts
    )

    private fun randomRules(random: Random): Pair<List<String>, List<String>> {
        fun list() = (0 until random.nextInt(0, 6)).map {
            when (random.nextInt(6)) {
                0 -> "  "
                1 -> ""
                else -> randomText(random, wordChunks, 1, 3)
            }
        }
        return list() to list()
    }

    @Test
    fun canonicalSearchTextIsUnchangedFromBefore() {
        val random = Random(20261007)
        repeat(500) {
            val text = randomText(random, chunks, 0, 14)
            assertEquals(legacyCanonical(text), canonicalThreadSearchText(text), "text=$text")
        }
    }

    @Test
    fun ngFiltersKeepTheSamePostsAsBefore() {
        val random = Random(1)
        repeat(300) { round ->
            val posts = randomPosts(random, random.nextInt(0, 25))
            val (headers, words) = randomRules(random)
            val expected = legacyApplyNg(page(posts), headers, words)
            val actual = applyNgFilters(page(posts), headers, words, enabled = true).posts
            assertEquals(expected, actual, "round=$round headers=$headers words=$words")
            assertEquals(posts, applyNgFilters(page(posts), headers, words, enabled = false).posts)
            // matchesNgFilters takes rules as given (not trimmed or lowercased), as it always did.
            val lower = buildLowerBodyByPost(posts)
            posts.forEach { post ->
                assertEquals(
                    legacyMatchesNg(post, headers, words, lower),
                    matchesNgFilters(post, headers, words, lower),
                    "post=${post.id} headers=$headers words=$words"
                )
            }
        }
    }

    @Test
    fun cancellableFilterResultMatchesTheSynchronousOne() {
        val random = Random(2)
        val optionSets = listOf(
            emptySet(),
            setOf(ThreadFilterOption.Url),
            setOf(ThreadFilterOption.Keyword),
            setOf(ThreadFilterOption.Image, ThreadFilterOption.Deleted),
            setOf(ThreadFilterOption.SelfPosts, ThreadFilterOption.Keyword),
            setOf(ThreadFilterOption.HighReplies),
            setOf(ThreadFilterOption.HighSaidane, ThreadFilterOption.Url)
        )
        repeat(200) { round ->
            val posts = randomPosts(random, random.nextInt(0, 25))
                .mapIndexed { index, post -> post.copy(referencedCount = (index * 7) % 5) }
            val (headers, words) = randomRules(random)
            val options = optionSets[random.nextInt(optionSets.size)]
            val criteria = ThreadFilterCriteria(
                options = options,
                keyword = randomText(random, wordChunks, 0, 2),
                selfPostIdentifiers = posts.filter { random.nextInt(4) == 0 }.map { it.id },
                sortOption = options.firstNotNullOfOrNull { it.sortOption }
            )
            val ngEnabled = random.nextBoolean()
            val source = page(posts)
            val expected = applyThreadFilterResult(source, criteria, headers, words, ngEnabled).postIndices
            val precomputed = buildLowerBodyByPost(posts)
            val cache = ThreadPostTextCache()
            val results = runBlocking {
                listOf(
                    applyThreadFilterResultCancellable(source, criteria, headers, words, ngEnabled),
                    applyThreadFilterResultCancellable(source, criteria, headers, words, ngEnabled, textCache = cache),
                    applyThreadFilterResultCancellable(source, criteria, headers, words, ngEnabled, precomputedLowerBodyByPost = precomputed),
                    // The same screen path: bodies from the shared text cache.
                    applyThreadFilterResultCancellable(
                        source, criteria, headers, words, ngEnabled,
                        precomputedLowerBodyByPost = buildLowerBodyByPost(posts, cache),
                        textCache = cache
                    )
                )
            }
            results.forEachIndexed { index, result ->
                assertEquals(expected, result.postIndices, "round=$round variant=$index headers=$headers words=$words options=$options")
            }
        }
    }

    @Test
    fun hiddenPostIdsMatchTheIdComparisonOfTheFullFilter() {
        val random = Random(3)
        repeat(200) { round ->
            val base = randomPosts(random, random.nextInt(0, 25))
            // Some ids repeat: a post counts as hidden only when no kept post has its id.
            val posts = base.mapIndexed { index, post ->
                if (index > 0 && random.nextInt(5) == 0) post.copy(id = base[index - 1].id) else post
            }
            val (headers, words) = randomRules(random)
            val visibleIds = legacyApplyNg(page(posts), headers, words).mapTo(hashSetOf()) { it.id }
            val expected = posts.filter { it.id !in visibleIds }.mapTo(hashSetOf()) { it.id }
            val cache = ThreadPostTextCache()
            runBlocking {
                assertEquals(expected, findNgHiddenPostIds(posts, headers, words, null), "round=$round")
                assertEquals(expected, findNgHiddenPostIds(posts, headers, words, cache), "round=$round cached")
            }
        }
    }

    @Test
    fun cancelledFilterStopsInsteadOfFinishing() {
        val posts = randomPosts(Random(4), 400)
        var finished = false
        var cancelled = false
        runBlocking {
            launch {
                coroutineContext[Job]!!.cancel()
                try {
                    findNgHiddenPostIds(posts, listOf("あ"), listOf("猫"), null)
                    finished = true
                } catch (e: CancellationException) {
                    cancelled = true
                    throw e
                }
            }
        }
        assertTrue(cancelled, "a cancelled run must throw CancellationException")
        assertFalse(finished)
    }

    // ---- deletion summary ----

    @Test
    fun deletionSummaryIsUnchangedByNotConvertingOrdinaryPosts() {
        val random = Random(5)
        val notices = PostDeletionKind.entries.map { it.notice }
        repeat(200) { round ->
            val posts = (0 until random.nextInt(1, 20)).map { index ->
                val isDeleted = random.nextInt(3) == 0
                Post(
                    id = index.toString(),
                    author = null,
                    subject = null,
                    timestamp = "t",
                    messageHtml = when (random.nextInt(3)) {
                        0 -> notices[random.nextInt(notices.size)]
                        1 -> "本文<br>" + notices[random.nextInt(notices.size)] + "<br>末尾"
                        else -> randomText(random, chunks, 0, 4)
                    },
                    imageUrl = null,
                    thumbnailUrl = null,
                    isDeleted = isDeleted,
                    isIsolated = random.nextInt(6) == 0
                )
            }
            val source = ThreadPage("1", null, null, if (random.nextBoolean()) "削除された記事が3件あります." else null, posts)
            val expected = threadDeletionSummary(
                source.deletedNotice,
                source.posts.drop(1).mapNotNull { post ->
                    postDeletionKind(messageHtmlToPlainText(post.messageHtml), post.isDeleted, post.isIsolated)
                }
            )
            assertEquals(expected, threadDeletionSummaryForPage(source), "round=$round")
        }
    }

    // ---- kana folding of the search / NG text ----

    @Test
    fun foldingKeepsTheLengthAndIsIdempotent() {
        val kana = listOf(
            "ガ", "か", "カ", "ｶ", "ﾞ", "ﾟ", "ｳ", "ヴ", "ヷ", "ー", "ｰ", "ｦ", "ｯ", "ﾊ", "ヽ", "ヾ", "ゝ",
            "ァ", "ヶ", "ｧ", "ﾝ", "ん", "・", "漢", "〜", "Ａ", "　", "a", "゙"
        )
        val random = Random(6)
        repeat(500) {
            val text = randomText(random, kana, 0, 16)
            val folded = canonicalThreadSearchText(text)
            assertEquals(text.length, folded.length, "text=$text")
            assertEquals(folded, canonicalThreadSearchText(folded), "text=$text")
        }
    }

    private fun canon(text: String) = canonicalThreadSearchText(text)

    @Test
    fun kanaTypesAndFullWidthFormsAreFolded() {
        // Katakana = hiragana = half-width katakana, for search text and rules alike.
        assertEquals(canon("ネコ"), canon("ねこ"))
        assertEquals(canon("ネコ"), canon("ﾈｺ"))
        assertEquals("ねこ", canon("ﾈｺ"))
        assertEquals("がっこう", canon("ガッコウ"))
        assertEquals(canon("ガッコウ"), canon("がっこう"))
        assertEquals("がﾞ", canon("ｶﾞ"))
        assertEquals("ぱﾟ", canon("ﾊﾟ"))
        assertEquals("ゔﾞ", canon("ｳﾞ"))
        assertEquals("ぁぃぅぇぉゃゅょっーを", canon("ｧｨｩｪｫｬｭｮｯｰｦ"))
        assertEquals("をぁぃぅぇぉゃゅょっーあいうえおかきくけこさしすせそたちつてとなにぬねのはひふへほまみむめもやゆよらりるれろわん",
            canon("ｦｧｨｩｪｫｬｭｮｯｰｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄﾅﾆﾇﾈﾉﾊﾋﾌﾍﾎﾏﾐﾑﾒﾓﾔﾕﾖﾗﾘﾙﾚﾛﾜﾝ"))
        assertEquals("がぎぐげござじずぜぞだぢづでどばびぶべぼ", canon("ｶﾞｷﾞｸﾞｹﾞｺﾞｻﾞｼﾞｽﾞｾﾞｿﾞﾀﾞﾁﾞﾂﾞﾃﾞﾄﾞﾊﾞﾋﾞﾌﾞﾍﾞﾎﾞ").filter { it != 'ﾞ' })
        assertEquals("ぁぃぅぇぉゔゕゖゝゞ", canon("ァィゥェォヴヵヶヽヾ"))
        assertTrue(canon("ガ").contains("が"))
        assertTrue(canon("ｶﾞ").contains(canon("ガ")))
        assertEquals("abc 123!~", canon("ＡＢＣ　１２３！～").lowercase())
        assertEquals("~", canon("〜"))
        assertEquals("-", canon("−"))

        // Sounds are still told apart.
        assertNotEquals(canon("か"), canon("ガ"))
        assertNotEquals(canon("ハゲ"), canon("ハケ"))
        assertNotEquals(canon("ラーメン"), canon("らあめん"))
        assertNotEquals(canon("ヴ"), canon("う"))
        assertNotEquals(canon("つ"), canon("ｯﾞ"))
        assertEquals("っﾞ", canon("ｯﾞ"))
        assertNotEquals(canon("あ"), canon("亜"))
        assertNotEquals(canon("・"), canon("ー"))
        assertEquals("ヷヸヹヺ", canon("ヷヸヹヺ"))
        assertEquals("ー・", canon("ー・"))
        // A voiced mark that cannot voice its base leaves the base alone; the combining marks are left alone.
        assertEquals("あﾞ", canon("ｱﾞ"))
        assertEquals("か\u3099", canon("か\u3099"))
        assertEquals("ﾞ", canon("ﾞ"))
        assertEquals("がﾞﾞ", canon("ｶﾞﾞ"))

        assertFalse(ngMatches(listOf("ねこ"), "うちのいぬ"))
        assertTrue(ngMatches(listOf("ネコ"), "うちのねこ"))
        assertTrue(ngMatches(listOf("ねこ"), "ﾈｺ"))
        assertTrue(ngMatches(listOf("ﾈｺ"), "うちのネコ"))
        assertTrue(ngMatches(listOf("ＮＧ"), "ngです"))
        assertTrue(ngMatches(listOf("ng"), "ＮＧです"))
        assertFalse(ngMatches(listOf("か"), "ガ"))
        assertFalse(ngMatches(listOf("ハケ"), "ハゲ"))
    }

    @Test
    fun foldedKanaTextKeepsTheLengthAndIsIdempotentForRandomInput() {
        val random = Random(8)
        val pieces = listOf(
            "ガ", "か", "カ", "ｶ", "ﾞ", "ﾟ", "ｳ", "ヴ", "ヷ", "ー", "ｰ", "ｦ", "ｯ", "ﾊ", "ヽ", "ヾ", "ゝ",
            "ァ", "ヶ", "ｧ", "ﾝ", "ん", "・", "漢", "〜", "Ａ", "　", "a", "゙", "ﾟ", "ﾞ", "ｱ", "ｿ", "ﾀ", "ﾌ"
        )
        repeat(500) {
            val text = randomText(random, pieces, 0, 16)
            val folded = canon(text)
            assertEquals(text.length, folded.length, "text=$text")
            assertEquals(folded, canon(folded), "text=$text")
        }
    }

    @Test
    fun textThatNeedsNoFoldingIsReturnedAsTheSameInstance() {
        listOf("", "漢字とひらがなABC123", "ひらがなだけ", "abc def", "あー", "ー・").forEach { plain ->
            assertSame(plain, canon(plain), "text=$plain")
        }
        // Half-width marks alone and unsupported katakana are left as they are.
        val marks = "ﾞﾟヷヸヹヺ"
        assertSame(marks, canon(marks))
    }

    @Test
    fun searchHighlightsRangesOfTheOriginalText() {
        val posts = listOf(plainPost("1", "猫はネコと鳴く"), plainPost("2", "ＡＢＣ abc"), plainPost("3", "犬だけ"), plainPost("4", "ﾈｺだ"))
        val targets = runBlocking { buildThreadSearchTargets(posts) }
        // Hiragana finds katakana and half-width katakana, and the ranges index the original text.
        val byHiragana = runBlocking { buildThreadSearchMatches(targets, "ねこ") }
        assertEquals(listOf("1", "4"), byHiragana.map { it.postId })
        assertEquals(listOf(2..3), byHiragana[0].highlightRanges)
        assertEquals(listOf(0..1), byHiragana[1].highlightRanges)
        val byKatakana = runBlocking { buildThreadSearchMatches(targets, "ネコ") }
        assertEquals(listOf("1", "4"), byKatakana.map { it.postId })
        val byAscii = runBlocking { buildThreadSearchMatches(targets, "abc") }
        // Full-width and half-width ASCII are not told apart: the full-width text is found too.
        assertEquals(listOf("2"), byAscii.map { it.postId })
        assertEquals(listOf(0..2, 4..6), byAscii.single().highlightRanges)
        val byFullWidth = runBlocking { buildThreadSearchMatches(targets, "ＡＢＣ") }
        assertEquals(listOf(0..2, 4..6), byFullWidth.single().highlightRanges)
    }

    @Test
    fun searchTargetKeepsItsCanonicalTextOncePerBuild() {
        val targets = runBlocking {
            buildThreadSearchTargets(
                listOf(plainPost("1", "ＡＢＣ　カナ"))
            )
        }
        assertEquals(canonicalThreadSearchText(targets[0].searchableText), targets[0].canonicalSearchableText)
        assertTrue(targets[0].canonicalSearchableText.contains(" かな"))
    }

    private fun ngMatches(words: List<String>, body: String): Boolean {
        val post = plainPost("1", body)
        return applyNgFilters(page(listOf(post)), emptyList(), words, enabled = true).posts.isEmpty()
    }
}
