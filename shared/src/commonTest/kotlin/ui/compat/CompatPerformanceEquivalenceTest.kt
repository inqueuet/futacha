package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.CompatHistoryEntry
import com.valoser.futacha.shared.compat.CompatNgKind
import com.valoser.futacha.shared.compat.CompatNgRule
import com.valoser.futacha.shared.compat.CompatNgRuleSearchIndex
import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.compat.CompatThreadNgFilterCache
import com.valoser.futacha.shared.compat.CompatThreadSnapshot
import com.valoser.futacha.shared.compat.buildCompatThreadNgRuleIndex
import com.valoser.futacha.shared.compat.compatCatalogManagementRules
import com.valoser.futacha.shared.compat.compatQuoteQueryForLine
import com.valoser.futacha.shared.compat.compatThreadReferenceDisplayValue
import com.valoser.futacha.shared.compat.compatThreadReferenceRules
import com.valoser.futacha.shared.compat.filterCompatNgRulesBySearch
import com.valoser.futacha.shared.compat.filterCompatThreadPosts
import com.valoser.futacha.shared.compat.filterCompatThreadPostsCached
import com.valoser.futacha.shared.compat.normalizeCompatSearchText
import com.valoser.futacha.shared.compat.resolveCompatQuotePosts
import com.valoser.futacha.shared.compat.sortedByPrecomputedKey
import com.valoser.futacha.shared.compat.toCompatPlainText
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.QuoteReference
import com.valoser.futacha.shared.ui.board.buildThreadTreeNodes
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The faster implementations must return what the straightforward ones returned. */
class CompatPerformanceEquivalenceTest {
    private val words = listOf("おやつ", "猫", "ふたば", "テスト", "ＡＢＣ", "abc", "ｱｲｳ", "アイウ", "いいね", "なるほど")

    private fun randomPosts(random: Random, count: Int): List<CompatPostSnapshot> = (0 until count).map { i ->
        val lines = buildList {
            repeat(random.nextInt(0, 4)) {
                when (random.nextInt(6)) {
                    0 -> add("&gt;" + words.random(random))
                    1 -> add("&gt;No.${1000 + random.nextInt(count + 2)}")
                    2 -> add("&gt;ID:id${random.nextInt(5)}")
                    3 -> add("&gt;" + words.random(random) + " " + words.random(random))
                    else -> add(words.random(random) + " " + words.random(random))
                }
            }
        }
        CompatPostSnapshot(
            position = i,
            postNo = "${1000 + i}",
            author = if (random.nextInt(4) == 0) words.random(random) else null,
            subject = if (random.nextInt(4) == 0) words.random(random) else null,
            mail = if (random.nextInt(8) == 0) "sage" else null,
            timestamp = "25/10/07(火)10:00:00" + if (random.nextInt(3) == 0) " ID:id${random.nextInt(5)}" else "",
            posterId = if (random.nextInt(6) == 0) "id${random.nextInt(5)}" else null,
            messageHtml = lines.joinToString("<br>"),
            imageUrl = if (random.nextInt(5) == 0) "https://may.2chan.net/b/src/17000000000${random.nextInt(9)}.jpg" else null,
            quoteReferences = if (random.nextInt(5) == 0 && i > 0) {
                listOf(QuoteReference(">>x", listOf("${1000 + random.nextInt(i)}")))
            } else emptyList(),
            isContentRedacted = random.nextInt(15) == 0
        )
    }

    @Test
    fun treeDisplayMatchesTheLegacyPerQuoteResolution() = runBlocking {
        val random = Random(1)
        repeat(8) { round ->
            val posts = randomPosts(random, 50 + round * 10)
            val legacyNodes = buildThreadTreeNodes(posts.map { post ->
                val references = if (post.quoteReferences.isNotEmpty() || post.isContentRedacted) post.quoteReferences else {
                    post.messageHtml.toCompatPlainText().lineSequence()
                        .mapNotNull(::compatQuoteQueryForLine)
                        .mapNotNull { query ->
                            resolveCompatQuotePosts(posts, post.position, query).firstOrNull()
                                ?.let { QuoteReference(query, listOf(it.postNo)) }
                        }.firstOrNull()?.let(::listOf).orEmpty()
                }
                Post(id = post.postNo, author = null, subject = null, timestamp = "",
                    messageHtml = "", imageUrl = null, thumbnailUrl = null, quoteReferences = references)
            })
            val display = buildCompatThreadDisplay(posts, tree = true)
            assertEquals(legacyNodes.map { it.post.id }, display.posts.map { it.postNo }, "round $round order")
            assertEquals(legacyNodes.associate { it.post.id to it.depth }, display.depthByPostNo, "round $round depth")
        }
    }

    @Test
    fun relatedPostsMatchTheLegacyFunction() = runBlocking {
        val random = Random(2)
        repeat(6) { round ->
            val posts = randomPosts(random, 40 + round * 8)
            posts.forEach { post ->
                val expected = com.valoser.futacha.shared.ui.board.relatedCompatPosts(post.postNo, posts)
                val actual = relatedCompatPostsIndexed(post.postNo, posts)
                assertEquals(expected.size, actual.size, "round $round post ${post.postNo}")
                expected.indices.forEach { assertSame(expected[it], actual[it]) }
            }
            assertTrue(relatedCompatPostsIndexed("nope", posts).isEmpty())
        }
    }

    private fun rule(id: String, kind: CompatNgKind, value: String, scope: String = "*", memo: String = "", imageUrl: String? = null) =
        CompatNgRule(id, kind, scope, value, createdAtEpochMillis = id.hashCode().toLong(), imageUrl = imageUrl, memo = memo)

    @Test
    fun sortedByPrecomputedKeyIsStableLikeSortedBy() {
        val random = Random(3)
        val items = List(300) { it to listOf("ａ", "A", "あ", "ア", "ｱ", "b", "", "ＢＢ").random(random) }
        assertEquals(
            items.sortedBy { normalizeCompatSearchText(it.second) },
            items.sortedByPrecomputedKey { normalizeCompatSearchText(it.second) }
        )
        assertEquals(emptyList(), emptyList<Pair<Int, String>>().sortedByPrecomputedKey { it.second })
    }

    @Test
    fun managementRuleListsKeepTheirOrder() {
        val random = Random(4)
        val rules = List(200) { i ->
            rule("r$i", listOf(CompatNgKind.CATALOG_IGNORE, CompatNgKind.CATALOG_EXTRACT, CompatNgKind.THREAD_REFUSE, CompatNgKind.THREAD_POST_NO).random(random),
                words.random(random), scope = listOf("*", "b", "tab").random(random), memo = if (random.nextBoolean()) words.random(random) else "")
        }
        for (kind in listOf(CompatNgKind.CATALOG_IGNORE, CompatNgKind.CATALOG_EXTRACT)) {
            val accepted = com.valoser.futacha.shared.compat.compatCatalogManagementKinds(kind)
            val expected = rules.filter { it.kind in accepted && (it.scopeKey == "b" || it.scopeKey == "*") }
                .sortedBy { normalizeCompatSearchText(com.valoser.futacha.shared.compat.compatCatalogManagementDisplayValue(it)) }
            assertEquals(expected, compatCatalogManagementRules(rules, "b", kind))
        }
        val acceptedThread = com.valoser.futacha.shared.compat.compatThreadReferenceKinds(CompatNgKind.THREAD_REFUSE)
        assertEquals(
            rules.filter { it.kind in acceptedThread && (it.scopeKey == "tab" || it.scopeKey == "*") }
                .sortedBy { normalizeCompatSearchText(compatThreadReferenceDisplayValue(it)) },
            compatThreadReferenceRules(rules, "tab", CompatNgKind.THREAD_REFUSE)
        )
    }

    @Test
    fun ruleSearchIndexMatchesTheDirectFilter() {
        val random = Random(5)
        val rules = List(150) { i ->
            rule("r$i", listOf(CompatNgKind.THREAD_REFUSE, CompatNgKind.THREAD_POST_NO, CompatNgKind.CATALOG_IGNORE, CompatNgKind.THREAD_IMAGE).random(random),
                if (random.nextInt(5) == 0) "https://may.2chan.net/b/src/${i}.jpg" else words.random(random),
                memo = if (random.nextBoolean()) words.random(random) else "",
                imageUrl = if (random.nextInt(4) == 0) "https://example.test/img/${words.random(random)}.png" else null)
        }
        val queries = words + listOf("", "  ", "ABC", "ａｂｃ", "src", "png", "no.", "存在しない")
        for (image in listOf(false, true)) for (thread in listOf(false, true)) {
            val index = CompatNgRuleSearchIndex(rules, image, thread)
            queries.forEach { q ->
                assertEquals(filterCompatNgRulesBySearch(rules, q, image, thread), index.filter(q), "image=$image thread=$thread '$q'")
            }
        }
    }

    @Test
    fun cachedThreadNgFilterMatchesTheDirectFilterAcrossAppendsAndRuleChanges() {
        val random = Random(6)
        val all = randomPosts(random, 120)
        var cache: CompatThreadNgFilterCache? = null
        var wordsInUse = listOf("おやつ")
        for (step in 1..12) {
            // Mostly append-only; sometimes the rules or an already known post's text change.
            var posts = all.take(20 + step * 8)
            if (step % 5 == 0) wordsInUse = listOf(words.random(random), words.random(random))
            if (step % 4 == 0) posts = posts.mapIndexed { i, p -> if (i == 3) p.copy(messageHtml = "おやつ changed") else p }
            val rules = wordsInUse.mapIndexed { i, w ->
                rule("w$i", listOf(CompatNgKind.THREAD_IGNORE, CompatNgKind.THREAD_REFUSE, CompatNgKind.THREAD_WORD).random(random), w, scope = "tab")
            } + rule("n", CompatNgKind.THREAD_POST_NO, "${1000 + step}", scope = "tab") +
                rule("i", CompatNgKind.THREAD_POSTER_ID, "ID:id1", scope = "tab")
            val index = buildCompatThreadNgRuleIndex(rules, "tab")
            val hidden = setOf("${1000 + step * 2}")
            val expected = filterCompatThreadPosts(posts, true, index, hidden)
            val result = filterCompatThreadPostsCached(posts, true, index, hidden, cache)
            cache = result.cache
            assertEquals(expected.size, result.posts.size, "step $step")
            expected.indices.forEach { assertSame(expected[it], result.posts[it], "step $step #$it") }
        }
        // Disabled NG returns the posts untouched and keeps the cache.
        val untouched = filterCompatThreadPostsCached(all, false, buildCompatThreadNgRuleIndex(emptyList(), "tab"), emptySet(), cache)
        assertSame(all, untouched.posts)
        assertSame(cache, untouched.cache)
    }

    private fun snapshot(posts: List<CompatPostSnapshot>) =
        CompatThreadSnapshot(tabKey = "t", revision = 1, fetchedAtEpochMillis = 0, posts = posts)

    @Test
    fun cacheSearchTermMatchesEqualTheBodyBasedSearch() {
        val random = Random(7)
        val history = (0 until 30).map { i ->
            CompatHistoryEntry("u$i", "https://may.2chan.net/b/res/${100 + i}.htm", "b", "板", "${100 + i}", "題名${words.random(random)}",
                contentUpdatedAtEpochMillis = 1L)
        }
        val bodies = history.filter { it.threadNo.toInt() % 4 != 0 }.associate { entry ->
            entry.threadNo to snapshot(randomPosts(random, random.nextInt(0, 6)))
        }
        val remote = (0 until 5).map { CatalogItem("${100 + it * 3}", "https://may.2chan.net/b/res/${100 + it * 3}.htm", "リモート${words.random(random)}", null, null, replyCount = 0) }
        val queries = listOf("おやつ", "猫 ふたば", "ＡＢＣ", "ｱｲｳ", "ID:id1", "103", "題名猫", "存在しない語", "おやつ なるほど テスト")
        val normalizedBodies = bodies.mapValues { normalizeCompatSearchText(compatCacheSearchBodyText(it.value)) }
        for (query in queries) {
            val terms = compatCacheSearchTerms(query)
            val matches = bodies.mapNotNull { (no, snap) ->
                compatCacheSearchTermMatchIndices(snap, terms).takeIf { it.isNotEmpty() }?.let { no to it }
            }.toMap()
            val expectedMerged = mergeCompatCacheSearchResultsNormalized(remote, history, "b", query, normalizedBodies)
            val merged = mergeCompatCacheSearchResultsByTermMatches(remote, history, "b", terms, matches)
            assertEquals(expectedMerged, merged, "merge '$query'")
            for (mode in CompatCatalogCacheSearchMode.entries) {
                assertEquals(
                    filterLegacyCompatCatalogCacheNormalized(expectedMerged, query, mode, normalizedBodies),
                    filterLegacyCompatCatalogCacheByTermMatches(merged, terms, mode, matches),
                    "filter '$query' $mode"
                )
            }
        }
    }
}
