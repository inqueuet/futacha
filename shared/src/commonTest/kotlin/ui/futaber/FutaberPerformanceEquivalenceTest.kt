package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.compat.CompatNgKind
import com.valoser.futacha.shared.compat.CompatNgRule
import com.valoser.futacha.shared.compat.buildCompatThreadNgRuleIndex
import com.valoser.futacha.shared.compat.matchesCompatThreadNg
import com.valoser.futacha.shared.compat.toCompatThreadSnapshot
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.QuoteReference
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.model.normalizeCatalogSearchText
import com.valoser.futacha.shared.network.NetworkException
import com.valoser.futacha.shared.ui.board.ThreadLoadTimeoutException
import com.valoser.futacha.shared.ui.board.applyNgFilters
import com.valoser.futacha.shared.ui.board.messageHtmlToPlainText
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The thread screen's NG matching, search texts and catalog search moved off the main thread and were made to do their
 * work once. Each is compared with the code it replaced (copied here as the reference) on random input: the answer
 * must be the same.
 */
class FutaberPerformanceEquivalenceTest {

    // ---------- the replaced implementations ----------

    private fun legacyNgHiddenIds(
        posts: List<Post>, ngWords: List<String>, ngHeaders: List<String>, rules: List<CompatNgRule>,
        tabKey: String, boardKey: String
    ): Set<String> {
        if (posts.isEmpty()) return emptySet()
        val hidden = HashSet<String>()
        if (ngWords.any { it.isNotBlank() } || ngHeaders.any { it.isNotBlank() }) {
            val page = ThreadPage(threadId = "", boardTitle = null, expiresAtLabel = null, deletedNotice = null, posts = posts)
            val kept = applyNgFilters(page, ngHeaders, ngWords, enabled = true).posts.mapTo(HashSet()) { it.id }
            posts.forEach { if (it.id !in kept) hidden += it.id }
        }
        if (rules.isNotEmpty()) {
            val index = buildCompatThreadNgRuleIndex(rules, tabKey, boardKey)
            val snapshots = ThreadPage(threadId = "", boardTitle = null, expiresAtLabel = null, deletedNotice = null, posts = posts)
                .toCompatThreadSnapshot(tabKey, 0).posts
            snapshots.forEach { if (it.matchesCompatThreadNg(index)) hidden += it.postNo }
        }
        return hidden
    }

    private val legacyUrlPattern = Regex("t?tps?://|https?://", RegexOption.IGNORE_CASE)

    private fun legacyMatchesExtract(post: Post, kind: FutaberExtract, selfIds: Set<String>): Boolean = when (kind) {
        FutaberExtract.SelfPosts -> post.id in selfIds
        FutaberExtract.Saidane -> (futaberSaidaneCount(post.saidaneLabel) ?: 0) >= FUTABER_MANY_SAIDANE_THRESHOLD
        FutaberExtract.Deleted -> post.isDeleted || post.isIsolated
        FutaberExtract.Url -> legacyUrlPattern.containsMatchIn(messageHtmlToPlainText(post.messageHtml))
        FutaberExtract.Image -> !post.imageUrl.isNullOrBlank() || !post.thumbnailUrl.isNullOrBlank()
    }

    private fun legacySearchText(post: Post): String = buildString {
        append(messageHtmlToPlainText(post.messageHtml)).append('\n')
        post.subject?.let { append(it).append('\n') }
        post.author?.let { append(it).append('\n') }
        post.posterId?.let { append(it).append('\n') }
        append(post.id)
    }

    private fun legacyVisibleRows(
        posts: List<Post>, filter: FutaberViewFilter, replyIndex: Map<String, List<Post>>,
        hidden: Set<String>, threshold: Int, selfIds: Set<String>
    ): List<FutaberRow> {
        val needle = normalizeCatalogSearchText(filter.query)
        return posts.mapIndexedNotNull { ordinal, post ->
            when {
                post.id in hidden -> null
                filter.repliesOnly && (replyIndex[post.id]?.size ?: 0) < threshold -> null
                filter.extract != null && !legacyMatchesExtract(post, filter.extract, selfIds) -> null
                filter.sameId.isNotBlank() && post.posterId != filter.sameId -> null
                needle.isNotEmpty() && !normalizeCatalogSearchText(legacySearchText(post)).contains(needle) -> null
                else -> FutaberRow(ordinal, post)
            }
        }
    }

    // ---------- random input ----------

    private val vocabulary = listOf(
        "ねこ", "ネコ", "イヌ", "犬", "abc", "ABC", "ＡＢＣ", "テスト", "ﾃｽﾄ", "http://example.com/a", "ttps://x.y/z",
        "〜", "～", "−", "禁止", "荒らし", "あ", "ア", "1234", "ＩＤ", ">引用", "<br>", "&amp;", "sage", "ふたば"
    )

    private fun randomText(random: Random, max: Int): String =
        (0 until random.nextInt(0, max + 1)).joinToString(random.pick(listOf("", " ", "<br>"))) { random.pick(vocabulary) }

    private fun <T> Random.pick(list: List<T>): T = list[nextInt(list.size)]

    private fun randomPost(random: Random, id: String): Post = Post(
        id = id,
        author = random.pick(listOf(null, "名無し", "としあき", "荒らし", "ＡＢＣ")),
        subject = random.pick(listOf(null, "無題", randomText(random, 2))),
        timestamp = "25/10/05(日)12:${random.nextInt(10, 60)}:00",
        posterId = random.pick(listOf(null, "", "ID:abc123", "ID:XYZ789", "zzz")),
        messageHtml = randomText(random, 6),
        imageUrl = random.pick(listOf(null, "https://img.example/a.jpg", "https://img.example/b.jpg")),
        thumbnailUrl = random.pick(listOf(null, "https://img.example/a-s.jpg")),
        saidaneLabel = random.pick(listOf(null, "そうだねx3", "+5", "")),
        isDeleted = random.nextInt(10) == 0,
        isIsolated = random.nextInt(15) == 0,
        mail = random.pick(listOf(null, "sage")),
        quoteReferences = if (random.nextBoolean()) emptyList() else listOf(QuoteReference(">x", listOf("1")))
    )

    private fun randomPosts(random: Random, size: Int, uniqueIds: Boolean = true, startId: Int = 100): List<Post> =
        (0 until size).map { index ->
            val id = if (uniqueIds || random.nextInt(6) != 0) (startId + index).toString() else (startId + random.nextInt(0, maxOf(1, index))).toString()
            randomPost(random, id)
        }

    private fun randomWords(random: Random, max: Int): List<String> =
        (0 until random.nextInt(0, max + 1)).map {
            when (random.nextInt(6)) {
                0 -> ""
                1 -> "  "
                2 -> " " + random.pick(vocabulary).uppercase() + " "
                else -> random.pick(vocabulary)
            }
        }

    private fun randomRules(random: Random, posts: List<Post>, tab: String): List<CompatNgRule> =
        (0 until random.nextInt(0, 7)).map { n ->
            val kind = random.pick(
                listOf(
                    CompatNgKind.THREAD_POST_NO, CompatNgKind.THREAD_POSTER_ID, CompatNgKind.THREAD_WORD,
                    CompatNgKind.THREAD_IGNORE, CompatNgKind.THREAD_REFUSE, CompatNgKind.THREAD_IMAGE,
                    CompatNgKind.THREAD_IMAGE_PHASH, CompatNgKind.CATALOG_THREAD
                )
            )
            val value = when (kind) {
                CompatNgKind.THREAD_POST_NO -> posts.randomOrNull(random)?.id ?: "999"
                CompatNgKind.THREAD_POSTER_ID -> random.pick(listOf("ID:abc123", "XYZ789", "ID:none"))
                CompatNgKind.THREAD_IMAGE -> random.pick(listOf("https://img.example/a.jpg", "https://img.example/zzz.jpg"))
                CompatNgKind.THREAD_IMAGE_PHASH -> "0123456789abcdef"
                else -> random.pick(vocabulary)
            }
            CompatNgRule(
                id = "r$n-$kind", kind = kind, scopeKey = random.pick(listOf(tab, "*", "other-tab")),
                normalizedValue = value, createdAtEpochMillis = 0L
            )
        }

    // ---------- NG ----------

    @Test
    fun ngHiddenIdsMatchTheReplacedImplementationOnRandomInput() {
        repeat(400) { seed ->
            val random = Random(seed)
            val posts = randomPosts(random, random.nextInt(0, 40), uniqueIds = random.nextInt(4) != 0)
            val words = randomWords(random, 6)
            val headers = randomWords(random, 4)
            val rules = randomRules(random, posts, "tab")
            assertEquals(
                legacyNgHiddenIds(posts, words, headers, rules, "tab", "board"),
                futaberNgHiddenIds(posts, words, headers, rules, "tab", "board"),
                "seed=$seed"
            )
        }
    }

    @Test
    fun anAppendedThreadOnlyLooksAtTheNewPostsAndGivesTheSameAnswer() {
        repeat(300) { seed ->
            val random = Random(1000 + seed)
            val all = randomPosts(random, random.nextInt(2, 50))
            val cut = random.nextInt(1, all.size)
            val words = randomWords(random, 5)
            val headers = randomWords(random, 3)
            val rules = randomRules(random, all, "tab")
            val prepared = futaberNgPrepare(words, headers, rules, "tab", "board")
            val prefix = all.take(cut)
            val first = FutaberNgSnapshot(prefix, prepared, futaberNgResolve(null, prefix, prepared))
            // The grown list is a new list of the same (equal) first posts, as a reload gives.
            val grown = all.map { it.copy() }
            val expected = legacyNgHiddenIds(all, words, headers, rules, "tab", "board")
            assertEquals(expected, futaberNgResolve(first, grown, prepared), "resolve seed=$seed")
            val quick = futaberNgQuick(first, grown, prepared)
            assertTrue(quick.complete, "a small job is done at once, seed=$seed")
            assertEquals(expected, quick.hidden, "quick seed=$seed")
        }
    }

    @Test
    fun aChangedOrShrunkThreadIsWorkedOutInFull() {
        repeat(200) { seed ->
            val random = Random(5000 + seed)
            val all = randomPosts(random, random.nextInt(3, 40))
            val words = randomWords(random, 5)
            val prepared = futaberNgPrepare(words, emptyList(), emptyList(), "tab", "board")
            val earlier = FutaberNgSnapshot(all, prepared, futaberNgResolve(null, all, prepared))
            // A post in the middle changed (its body now holds an NG word), and the list also lost its last post.
            val changed = all.toMutableList()
            val target = random.nextInt(changed.size)
            changed[target] = changed[target].copy(messageHtml = "禁止 " + changed[target].messageHtml)
            val next = changed.dropLast(1)
            val nextWords = words + "禁止"
            val nextPrepared = futaberNgPrepare(nextWords, emptyList(), emptyList(), "tab", "board")
            assertEquals(
                legacyNgHiddenIds(next, nextWords, emptyList(), emptyList(), "tab", "board"),
                futaberNgResolve(earlier, next, nextPrepared), "seed=$seed"
            )
            assertEquals(
                legacyNgHiddenIds(next, words, emptyList(), emptyList(), "tab", "board"),
                futaberNgResolve(earlier, next, prepared), "same lists, changed posts, seed=$seed"
            )
            // The same number of posts with one changed in place is not an append either.
            assertEquals(
                legacyNgHiddenIds(changed, words, emptyList(), emptyList(), "tab", "board"),
                futaberNgResolve(earlier, changed, prepared), "changed in place, seed=$seed"
            )
        }
    }

    @Test
    fun appendedPostsReusingAnIdAreNotTakenForNew() {
        val a = Post("100", author = null, subject = null, timestamp = "", messageHtml = "ok", imageUrl = null, thumbnailUrl = null)
        val b = Post("101", author = null, subject = null, timestamp = "", messageHtml = "禁止", imageUrl = null, thumbnailUrl = null)
        // The new post has the id of a kept post: the shared filter hides an id only when no post with it survives.
        val c = Post("100", author = null, subject = null, timestamp = "", messageHtml = "禁止", imageUrl = null, thumbnailUrl = null)
        val prepared = futaberNgPrepare(listOf("禁止"), emptyList(), emptyList(), "tab", "board")
        val earlier = FutaberNgSnapshot(listOf(a, b), prepared, futaberNgResolve(null, listOf(a, b), prepared))
        val grown = listOf(a, b, c)
        assertEquals(legacyNgHiddenIds(grown, listOf("禁止"), emptyList(), emptyList(), "tab", "board"), futaberNgResolve(earlier, grown, prepared))
    }

    @Test
    fun rulesThatCannotHideAnythingMakeNoSnapshotWork() {
        val posts = randomPosts(Random(1), 20)
        // Another thread's rule and a picture-hash rule (matched by hashing, not here) do not count.
        val rules = listOf(
            CompatNgRule("a", CompatNgKind.THREAD_WORD, "other-tab", "ねこ", 0L),
            CompatNgRule("b", CompatNgKind.THREAD_IMAGE_PHASH, "*", "0123456789abcdef", 0L),
            CompatNgRule("c", CompatNgKind.CATALOG_THREAD, "*", "x", 0L)
        )
        val prepared = futaberNgPrepare(emptyList(), emptyList(), rules, "tab", "board")
        assertNull(prepared.ruleIndex)
        assertTrue(prepared.isEmpty)
        assertTrue(futaberNgEvaluate(posts, prepared).isEmpty())
        assertEquals(legacyNgHiddenIds(posts, emptyList(), emptyList(), rules, "tab", "board"), emptySet())
        // A rule of this thread does count.
        val own = futaberNgPrepare(emptyList(), emptyList(), rules + CompatNgRule("d", CompatNgKind.THREAD_POST_NO, "tab", "105", 0L), "tab", "board")
        assertNotNull(own.ruleIndex)
        assertEquals(setOf("105"), futaberNgEvaluate(posts, own))
    }

    @Test
    fun aBigJobIsLeftToTheWorkerWithoutShowingPostsItWouldHide() {
        val random = Random(77)
        val posts = randomPosts(random, 120)
        // Enough distinct words that the job is above what the main thread may do.
        val words = (0 until 6000).map { "語$it" } + "禁止"
        val prepared = futaberNgPrepare(words, emptyList(), emptyList(), "tab", "board")
        assertTrue(posts.size * prepared.costPerPost > FUTABER_NG_SYNC_BUDGET)

        // No earlier result: the list waits for the first one.
        val first = futaberNgQuick(null, posts, prepared)
        assertFalse(first.complete)
        assertTrue(first.hold)

        // An earlier result: what it hid stays hidden, and the posts it did not know wait.
        val earlier = FutaberNgSnapshot(posts, prepared, futaberNgEvaluate(posts, prepared))
        val more = posts + randomPosts(random, 30, startId = 500)
        val standIn = futaberNgQuick(earlier, more, prepared)
        // (30 new posts of this cost fit the budget; a bigger tail below does not.)
        assertTrue(standIn.complete || standIn.hidden.containsAll(earlier.hidden))
        val bigger = futaberNgPrepare((0 until 40000).map { "語$it" } + "禁止", emptyList(), emptyList(), "tab", "board")
        val earlierBig = FutaberNgSnapshot(posts, bigger, futaberNgEvaluate(posts, bigger))
        val waiting = futaberNgQuick(earlierBig, more, bigger)
        assertFalse(waiting.complete)
        assertFalse(waiting.hold)
        assertTrue(waiting.hidden.containsAll(earlierBig.hidden))
        assertTrue(waiting.hidden.containsAll(more.drop(posts.size).map { it.id }))
        // The finished result is the same as working it out in one go.
        assertEquals(futaberNgEvaluate(more, bigger), futaberNgResolve(earlierBig, more, bigger))
    }

    @Test
    fun nothingToMatchIsDoneAtOnceAndHidesNothing() {
        val posts = randomPosts(Random(3), 10)
        val prepared = futaberNgPrepare(listOf("  "), listOf(""), emptyList(), "tab", "board")
        assertTrue(prepared.isEmpty)
        val quick = futaberNgQuick(null, posts, prepared)
        assertTrue(quick.complete)
        assertFalse(quick.hold)
        assertTrue(quick.hidden.isEmpty())
        // The result of a thread with no posts is not an earlier result worth waiting on.
        val real = futaberNgPrepare(listOf("禁止"), emptyList(), emptyList(), "tab", "board")
        val empty = FutaberNgSnapshot(emptyList(), real, emptySet())
        assertTrue(futaberNgQuick(empty, posts, real).complete)
    }

    // ---------- search texts ----------

    private fun randomFilter(random: Random, posts: List<Post>): FutaberViewFilter = FutaberViewFilter(
        query = random.pick(listOf("", " ", "ねこ", "ネコ", "abc", "ＡＢＣ", "http", "テスト", "id:abc123", "104", " 犬 ", "存在しない")),
        repliesOnly = random.nextInt(4) == 0,
        extract = random.pick(listOf(null) + FutaberExtract.entries),
        sameId = random.pick(listOf("", "", "ID:abc123", "zzz"))
    )

    @Test
    fun visibleRowsWithCachedTextsMatchTheReplacedImplementation() {
        repeat(400) { seed ->
            val random = Random(9000 + seed)
            val posts = randomPosts(random, random.nextInt(0, 40))
            val filter = randomFilter(random, posts)
            val replies = futaberReplyIndex(posts)
            val hidden = posts.filter { random.nextInt(8) == 0 }.mapTo(HashSet()) { it.id }
            val selfIds = posts.filter { random.nextInt(6) == 0 }.mapTo(HashSet()) { it.id }
            val threshold = random.nextInt(1, 4)
            val expected = legacyVisibleRows(posts, filter, replies, hidden, threshold, selfIds)
            val texts = futaberBuildPostTexts(null, posts)
            assertEquals(expected, futaberVisibleRows(posts, filter, replies, hidden, threshold, selfIds, texts), "cached seed=$seed")
            assertEquals(expected, futaberVisibleRows(posts, filter, replies, hidden, threshold, selfIds), "uncached seed=$seed")
        }
    }

    @Test
    fun textsOfOtherPostsAreNotUsed() {
        val random = Random(5)
        val posts = randomPosts(random, 12)
        val other = randomPosts(Random(6), 12)
        val filter = FutaberViewFilter(query = "ねこ")
        val replies = futaberReplyIndex(posts)
        assertEquals(
            futaberVisibleRows(posts, filter, replies),
            futaberVisibleRows(posts, filter, replies, texts = futaberBuildPostTexts(null, other))
        )
    }

    @Test
    fun textsOfAGrownThreadReuseTheEntriesOfThePostsThatDidNotChange() {
        repeat(100) { seed ->
            val random = Random(300 + seed)
            val all = randomPosts(random, random.nextInt(2, 40))
            val before = futaberBuildPostTexts(null, all.take(all.size / 2))
            val changed = all.toMutableList()
            if (changed.isNotEmpty()) {
                val at = random.nextInt(changed.size)
                changed[at] = changed[at].copy(messageHtml = "http://changed.example " + changed[at].messageHtml)
            }
            val grown = futaberBuildPostTexts(before, changed)
            val fresh = futaberBuildPostTexts(null, changed)
            assertEquals(fresh.entries.map { it.normalized }, grown.entries.map { it.normalized }, "seed=$seed")
            assertEquals(fresh.entries.map { it.hasUrl }, grown.entries.map { it.hasUrl }, "seed=$seed")
            // An unchanged post of the earlier list keeps the very same entry (nothing was worked out again).
            before.posts.forEachIndexed { i, post ->
                if (changed[i] == post) assertSame(before.entries[i], grown.entries[i])
            }
        }
    }

    // ---------- catalog ----------

    @Test
    fun catalogSearchOverFoldedTitlesGivesTheSameThreads() {
        repeat(200) { seed ->
            val random = Random(70000 + seed)
            val items = (0 until random.nextInt(0, 30)).map {
                CatalogItem(
                    id = (1000 + it).toString(), threadUrl = "https://x/res/$it.htm",
                    title = random.pick(listOf(null, "", randomText(random, 3))),
                    thumbnailUrl = null, fullImageUrl = null, replyCount = it
                )
            }
            val keys = futaberCatalogTitleKeys(items)
            val query = random.pick(listOf("", " ", "ねこ", "ネコ", "ＡＢＣ", "abc", "ﾃｽﾄ", "存在しない"))
            assertEquals(filterFutaberCatalog(items, query), filterFutaberCatalogByKeys(items, keys, query), "seed=$seed")
        }
    }

    // ---------- the seen count ----------

    @Test
    fun theSeenCountsKeepTheNewestAndAreBounded() {
        var stored: String? = null
        assertNull(futaberSeenCountFor(stored, "b", "1"))
        stored = futaberSeenCountsAfter(stored, "b", "1", 30)
        assertEquals(30, futaberSeenCountFor(stored, "b", "1"))
        // The same value again writes nothing.
        assertNull(futaberSeenCountsAfter(stored, "b", "1", 30))
        stored = futaberSeenCountsAfter(stored, "b", "1", 45)
        assertEquals(45, futaberSeenCountFor(stored, "b", "1"))
        assertNull(futaberSeenCountFor(stored, "other", "1"))
        // Nonsense is refused.
        assertNull(futaberSeenCountsAfter(stored, "b", "1", 0))
        assertNull(futaberSeenCountsAfter(stored, "", "1", 5))
        // The oldest drop first, and the stored text stays under the settings limit.
        for (i in 0 until FUTABER_MAX_SEEN_THREADS + 50) stored = futaberSeenCountsAfter(stored, "board-with-a-long-name", "$i", i + 1) ?: stored
        val all = decodeFutaberSeenCounts(stored)
        assertEquals(FUTABER_MAX_SEEN_THREADS, all.size)
        assertNull(futaberSeenCountFor(stored, "board-with-a-long-name", "0"))
        assertEquals(FUTABER_MAX_SEEN_THREADS + 50, futaberSeenCountFor(stored, "board-with-a-long-name", "${FUTABER_MAX_SEEN_THREADS + 49}"))
        assertTrue(checkNotNull(stored).length <= 20_000)
        // A thread opened again moves to the newest place and is not dropped before the others.
        stored = futaberSeenCountsAfter(stored, "board-with-a-long-name", "50", 999)
        assertEquals("50", decodeFutaberSeenCounts(stored).last().threadId)
    }

    @Test
    fun aDamagedSeenValueReadsAsNothingSeen() {
        assertTrue(decodeFutaberSeenCounts(null).isEmpty())
        assertTrue(decodeFutaberSeenCounts("").isEmpty())
        assertTrue(decodeFutaberSeenCounts("not json").isEmpty())
        assertTrue(decodeFutaberSeenCounts("[\"only-two\\nparts\"]").isEmpty())
        assertTrue(decodeFutaberSeenCounts("[\"b\\n1\\nx\"]").isEmpty())
    }

    @Test
    fun theSeenCountOfThisModeBeatsTheSharedReplyCountOfTheHistoryRow() {
        val ref = FutaberThreadRef("b", "1", "t", "", 0)
        val row = ThreadHistoryEntry(
            threadId = "1", boardId = "b", title = "t", titleImageUrl = "", boardName = "n", boardUrl = "u",
            lastVisitedEpochMillis = 1L, replyCount = 120
        )
        // A background refresh moved the shared count to 120; the person had seen 100.
        assertEquals(100, futaberRefWithHistory(ref, row, seenCount = 100).seenCount)
        assertEquals(100, futaberFirstNewIndex(futaberRefWithHistory(ref, row, seenCount = 100).seenCount, 120))
        // No record of this mode: the history row's count, as before.
        assertEquals(120, futaberRefWithHistory(ref, row, seenCount = null).seenCount)
        assertEquals(120, futaberRefWithHistory(ref, row).seenCount)
        // A thread not in the history is new.
        assertEquals(0, futaberRefWithHistory(ref, null, seenCount = 100).seenCount)
    }

    // ---------- load errors ----------

    @Test
    fun loadErrorsAreSaidInJapanese() {
        fun say(error: Throwable) = futaberFriendlyLoadError(error, "スレッド")
        assertTrue(say(NetworkException("HTTP error 404", statusCode = 404)).startsWith("スレッドが見つかりませんでした"))
        assertTrue(say(NetworkException("gone", statusCode = 410)).contains("見つかりませんでした"))
        assertTrue(say(NetworkException("x", statusCode = 403)).contains("403"))
        assertTrue(say(NetworkException("x", statusCode = 429)).contains("429"))
        assertTrue(say(NetworkException("x", statusCode = 503)).contains("503"))
        assertTrue(say(NetworkException("x", statusCode = 418)).contains("418"))
        assertTrue(say(ThreadLoadTimeoutException("Thread load timed out after 75000ms")).contains("タイムアウト"))
        assertTrue(say(RuntimeException("Unable to resolve host may.2chan.net")).contains("接続"))
        assertTrue(say(RuntimeException("Failed to fetch thread from URL: https://x")).let { it.contains("Failed").not() })
        // A message that is already Japanese is kept; any other text reads the fallback.
        assertEquals("保存先がありません", say(IllegalStateException("保存先がありません")))
        assertEquals("スレッドを読み込めませんでした", say(IllegalStateException("boom")))
        assertEquals("削除できませんでした", futaberFriendlyLoadError(IllegalStateException("EIO"), "保存", "削除できませんでした"))
        assertEquals("カタログを読み込めませんでした", futaberFriendlyLoadError(IllegalStateException(null as String?), "カタログ"))
    }
}
