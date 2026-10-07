package com.valoser.futacha.shared.compat

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * [CompatQuoteIndex] must return exactly what the former per-call resolver returned. The legacy
 * implementation is copied here (it re-filters and re-sorts every post per query) and the two are
 * compared on hand-made and random threads.
 */
class CompatQuoteIndexEquivalenceTest {
    private fun legacyResolve(
        posts: List<CompatPostSnapshot>,
        sourcePosition: Int,
        query: String
    ): List<CompatPostSnapshot> {
        val candidates = posts.asSequence()
            .filter { it.position < sourcePosition }
            .sortedByDescending { it.position }
            .toList()
        when {
            query.startsWith("no:", ignoreCase = true) -> {
                val postNo = query.substringAfter(':').substringBefore('\n').trim()
                candidates.firstOrNull { it.postNo == postNo }?.let { return listOf(it) }
                val fallback = query.substringAfter("\ntext:", "").normalizeCompatQuoteText()
                if (fallback.isBlank()) return emptyList()
                return candidates.filter { legacyMatches(it, fallback) }
            }
            query.startsWith("id:", ignoreCase = true) -> {
                val id = query.substringAfter(':').trim()
                return candidates.firstOrNull { post ->
                    compatPosterIdentities(post).any { identity ->
                        identity.kind == CompatHeaderExtractionKind.ID && identity.value == id
                    }
                }?.let(::listOf).orEmpty()
            }
            query.startsWith("ip:", ignoreCase = true) -> {
                val ip = query.substringAfter(':').trim()
                return candidates.firstOrNull { post ->
                    compatPosterIdentities(post).any { identity ->
                        identity.kind == CompatHeaderExtractionKind.IP && identity.value == ip
                    }
                }?.let(::listOf).orEmpty()
            }
            query.startsWith("file:", ignoreCase = true) -> {
                val fileName = query.substringAfter(':').trim()
                return candidates.firstOrNull { post ->
                    compatPostMediaFileNames(post).any { it.equals(fileName, ignoreCase = true) }
                }?.let(::listOf).orEmpty()
            }
        }
        val normalizedQuery = query.removePrefix("text:").normalizeCompatQuoteText()
        if (normalizedQuery.isBlank()) return emptyList()
        return candidates.filter { legacyMatches(it, normalizedQuery) }
    }

    private fun legacyMatches(post: CompatPostSnapshot, query: String): Boolean {
        val messageLines = post.messageHtml.toCompatPlainText()
            .lineSequence()
            .map(String::normalizeCompatQuoteText)
        if (messageLines.any { it == query || it.contains(query) }) return true
        val identityHeaders = compatPosterIdentities(post).map(CompatPosterIdentity::display)
        val header = listOfNotNull(post.subject, post.author, post.mail, post.posterId, post.postNo)
            .plus(identityHeaders)
            .plus(compatPostMediaFileNames(post))
            .joinToString(" ")
            .normalizeCompatQuoteText()
        return header.contains(query)
    }

    private fun assertSameResults(expected: List<CompatPostSnapshot>, actual: List<CompatPostSnapshot>, label: String) {
        assertEquals(expected.size, actual.size, label)
        expected.indices.forEach { assertSame(expected[it], actual[it], "$label #$it") }
    }

    private val words = listOf("おやつ", "猫", "ふたば", "テスト", "ＡＢＣ", "abc", "3時のおやつ", "100円ショップ", "いいね", "なるほど")

    private fun randomPosts(random: Random, count: Int, shuffled: Boolean, duplicateNumbers: Boolean): List<CompatPostSnapshot> {
        val posts = (0 until count).map { i ->
            val lines = buildList {
                repeat(random.nextInt(0, 4)) {
                    when (random.nextInt(8)) {
                        0 -> add("&gt;" + words.random(random))
                        1 -> add("&gt;No.${1000 + random.nextInt(count + 3)}")
                        2 -> add("&gt;ID:id${random.nextInt(6)}")
                        3 -> add("&gt;IP:10.0.0.${random.nextInt(4)}")
                        4 -> add("fu${random.nextInt(5)}.jpg")
                        5 -> add("&gt;fu${random.nextInt(5)}.JPG")
                        else -> add(words.random(random) + " " + words.random(random))
                    }
                }
            }
            CompatPostSnapshot(
                position = if (duplicateNumbers && i % 7 == 0 && i > 0) i - 1 else i,
                postNo = if (duplicateNumbers && i % 5 == 0 && i > 0) "${1000 + i - 1}" else "${1000 + i}",
                author = if (random.nextInt(4) == 0) words.random(random) else null,
                subject = if (random.nextInt(4) == 0) words.random(random) else null,
                mail = if (random.nextInt(6) == 0) "sage" else null,
                timestamp = when (random.nextInt(4)) {
                    0 -> "25/10/07(火)10:00:00 ID:id${random.nextInt(6)}"
                    1 -> "25/10/07(火)10:00:00 IP:10.0.0.${random.nextInt(4)}"
                    else -> "25/10/07(火)10:00:00"
                },
                posterId = if (random.nextInt(5) == 0) "id${random.nextInt(6)}" else null,
                messageHtml = lines.joinToString("<br>"),
                imageUrl = if (random.nextInt(3) == 0) "https://may.2chan.net/b/src/${1700000000000 + random.nextInt(8)}.jpg" else null,
                thumbnailUrl = if (random.nextInt(4) == 0) "https://may.2chan.net/b/thumb/${1700000000000 + random.nextInt(8)}s.jpg" else null
            )
        }
        return if (shuffled) posts.shuffled(random) else posts
    }

    private fun queriesFor(random: Random, posts: List<CompatPostSnapshot>): List<String> = buildList {
        posts.forEach { post ->
            post.messageHtml.toCompatPlainText().lineSequence()
                .mapNotNull { compatQuoteQueryForLine(it.trimStart()) }
                .forEach(::add)
        }
        add("no:3\ntext:3時のおやつ"); add("no:1003\ntext:3時のおやつ"); add("no:100\ntext:100円ショップ")
        add("no:1003"); add("NO:1004"); add("no: 1002 "); add("no:9999")
        add("id:id1"); add("ID:id2"); add("id:missing")
        add("ip:10.0.0.1"); add("IP:10.0.0.2"); add("ip:10.9.9.9")
        add("file:fu1.jpg"); add("FILE:FU2.JPG"); add("file:1700000000003.jpg"); add("file:none.png")
        add("text:おやつ"); add("TEXT:おやつ"); add("text:  猫   ふたば "); add("text:"); add("text:   "); add("おやつ")
        add("text:ID:id1"); add("text:sage"); add("text:No.1002")
        repeat(20) { add("text:" + words.random(random)) }
    }

    @Test
    fun indexMatchesTheLegacyResolverOnRandomThreads() {
        val random = Random(20261007)
        repeat(12) { round ->
            val posts = randomPosts(random, count = 40 + round * 5, shuffled = round % 3 == 1, duplicateNumbers = round % 4 == 3)
            val index = CompatQuoteIndex(posts)
            val queries = queriesFor(random, posts)
            queries.forEach { query ->
                val sources = listOf(-1, 0, 1, posts.size / 2, posts.size - 1, posts.size, posts.size + 5) +
                    List(4) { random.nextInt(0, posts.size + 2) }
                sources.forEach { source ->
                    val expected = legacyResolve(posts, source, query)
                    val label = "round $round source $source query '$query'"
                    assertSameResults(expected, index.resolve(source, query), label)
                    assertSame(expected.firstOrNull(), index.resolveFirst(source, query), "first of $label")
                    // The public function keeps working per call, too.
                    assertSameResults(expected, resolveCompatQuotePosts(posts, source, query), "public $label")
                }
            }
        }
    }

    @Test
    fun numberLedBodyQuotesFallBackToTextWhenNoPostHasThatNumber() {
        fun post(position: Int, postNo: String, html: String) = CompatPostSnapshot(
            position = position, postNo = postNo, timestamp = "", messageHtml = html
        )
        val posts = listOf(
            post(0, "100", "本文A"),
            post(1, "101", "3時のおやつ<br>100円ショップ行った"),
            post(2, "102", "&gt;3時のおやつ<br>&gt;100円ショップ<br>&gt;&gt;100<br>&gt;5時の電車")
        )
        // Post 100 exists: the number wins and the text is not consulted.
        assertEquals("no:100\ntext:100円ショップ", compatQuoteQueryForLine(">100円ショップ"))
        // No post 3: the text is matched against bodies and headers, newest first.
        assertEquals("no:3\ntext:3時のおやつ", compatQuoteQueryForLine(">3時のおやつ"))
        // `>>` and `No.` stay plain number references.
        assertEquals("no:100", compatQuoteQueryForLine(">>100"))
        assertEquals("no:3", compatQuoteQueryForLine(">No.3"))
        assertEquals("no:3", compatQuoteQueryForLine(">3"))
        assertEquals("no:5\ntext:5時の電車", compatQuoteQueryForLine(">5時の電車"))
        assertEquals("100", compatQuoteQueryPostNo("no:100\ntext:100円ショップ"))
        assertEquals("100", compatQuoteQueryPostNo("NO: 100 "))
        assertEquals(null, compatQuoteQueryPostNo("text:100"))

        val index = CompatQuoteIndex(posts)
        listOf(
            ">3時のおやつ" to listOf("101"),
            ">100円ショップ" to listOf("100"),
            ">>100" to listOf("100"),
            ">5時の電車" to emptyList()
        ).forEach { (line, expected) ->
            val query = compatQuoteQueryForLine(line)!!
            assertEquals(expected, resolveCompatQuotePosts(posts, 2, query).map { it.postNo }, line)
            assertEquals(expected, index.resolve(2, query).map { it.postNo }, "index $line")
            assertEquals(expected.firstOrNull(), index.resolveFirst(2, query)?.postNo, "first $line")
        }
        // The numbered post is not before the source: the number does not resolve, the text does.
        assertEquals(listOf("100"), resolveCompatQuotePosts(posts, 1, "no:101\ntext:本文A").map { it.postNo })
        assertEquals(listOf("100"), index.resolve(1, "no:101\ntext:本文A").map { it.postNo })
        assertEquals(emptyList(), resolveCompatQuotePosts(posts, 1, "no:102").map { it.postNo })
        assertEquals(emptyList(), index.resolve(1, "no:102").map { it.postNo })
    }

    @Test
    fun matcherAndReplyPredicateKeepTheirResults() {
        val random = Random(77)
        val posts = randomPosts(random, count = 60, shuffled = false, duplicateNumbers = false)
        posts.forEach { source ->
            // matchesCompatQuote is what extractCompatHeaderPosts uses for "text:" lines.
            listOf("おやつ", "猫 ふたば", "sage", "id1", "ID:id2", "fu1.jpg", "存在しない語").forEach { query ->
                assertEquals(legacyMatches(source, query.normalizeCompatQuoteText()), source.matchesCompatQuote(query.normalizeCompatQuoteText()))
            }
        }
        // extractCompatHeaderPosts(QUOTE) against the original per-candidate formulation.
        posts.forEach { source ->
            val expected = posts.filter { candidate ->
                candidate.position > source.position && candidate.messageHtml.toCompatPlainText().lineSequence().any { line ->
                    val query = compatQuoteQueryForLine(line.trimStart()) ?: return@any false
                    when {
                        query.startsWith("no:", ignoreCase = true) -> query.substringAfter(':').trim() == source.postNo
                        query.startsWith("id:", ignoreCase = true) -> compatPosterIdentities(source).any {
                            it.kind == CompatHeaderExtractionKind.ID && it.value == query.substringAfter(':').trim()
                        }
                        query.startsWith("ip:", ignoreCase = true) -> compatPosterIdentities(source).any {
                            it.kind == CompatHeaderExtractionKind.IP && it.value == query.substringAfter(':').trim()
                        }
                        query.startsWith("file:", ignoreCase = true) -> compatPostMediaFileNames(source).any {
                            it.equals(query.substringAfter(':').trim(), ignoreCase = true)
                        }
                        query.startsWith("text:", ignoreCase = true) -> legacyMatches(source, query.substringAfter(':'))
                        else -> false
                    }
                }
            }
            assertSameResults(expected, extractCompatHeaderPosts(posts, source, CompatHeaderExtractionKind.QUOTE), "extract ${source.postNo}")
            val kinds = compatHeaderExtractionKinds(source, posts)
            assertEquals(expected.isNotEmpty() || source.referencedCount > 0, CompatHeaderExtractionKind.QUOTE in kinds)
        }
    }

    @Test
    fun manyUnresolvedTextQuotesDeriveEachPostOnlyOnce() {
        // 2000 posts, 200 of which quote a line that no earlier post contains.
        val posts = (0 until 2000).map { i ->
            CompatPostSnapshot(
                position = i, postNo = "${1000 + i}", timestamp = "",
                messageHtml = if (i % 10 == 5) "&gt;誰も書いていない一行$i<br>返信" else "本文$i"
            )
        }
        val index = CompatQuoteIndex(posts)
        var queries = 0
        posts.filter { it.postNo.takeLast(1) == "5" && it.position % 10 == 5 }.forEach { post ->
            post.messageHtml.toCompatPlainText().lineSequence()
                .mapNotNull { compatQuoteQueryForLine(it.trimStart()) }
                .forEach { query ->
                    queries++
                    // The legacy path re-derived every earlier post here; the index must not.
                    index.resolve(post.position, query)
                }
        }
        assertEquals(200, queries)
        assertTrue(index.matcherCount <= posts.size, "matchers built: ${index.matcherCount}")
    }
}
