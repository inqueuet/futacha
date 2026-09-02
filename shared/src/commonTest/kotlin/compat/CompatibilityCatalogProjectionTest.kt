package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.CatalogItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompatibilityCatalogProjectionTest {
    private val source = listOf(
        item("a", 10),
        item("b", 2),
        item("c", 5),
        item("d", 8),
        item("e", 1)
    )

    @Test
    fun referencePriorityKeepsSelectedSortOrderAndAppendsFewReplies() {
        val projected = projectCompatCatalogItems(
            items = source,
            replyPriorityEnabled = true,
            replyThreshold = 5,
            showNonPriority = true,
            isExtracted = { it.id in setOf("b", "c") }
        )

        assertEquals(
            listOf("c", "a", "d", "b", "e"),
            projected.map(CatalogItem::id),
            "1.apk promotes extracted entries only inside the priority group and never reply-sorts that group"
        )
    }

    @Test
    fun hidingNonPriorityDropsOnlyEntriesBelowTheConfiguredThreshold() {
        val projected = projectCompatCatalogItems(
            items = source,
            replyPriorityEnabled = true,
            replyThreshold = 5,
            showNonPriority = false,
            isExtracted = { it.id in setOf("b", "c") }
        )

        assertEquals(listOf("c", "a", "d"), projected.map(CatalogItem::id))
    }

    @Test
    fun disablingReplyPriorityMakesThresholdZeroButKeepsExtractionPriority() {
        val projected = projectCompatCatalogItems(
            items = source,
            replyPriorityEnabled = false,
            replyThreshold = 5,
            showNonPriority = false,
            isExtracted = { it.id in setOf("b", "c") }
        )

        assertEquals(listOf("b", "c", "a", "d", "e"), projected.map(CatalogItem::id))
    }

    @Test
    fun freshCatalogMatchesFinalApkSortFlags() {
        val preference = CompatCatalogPreference(boardKey = "may-b")

        assertTrue(preference.replyPriorityEnabled)
        assertTrue(preference.showNonPriority)
        assertEquals(0, preference.fewRepliesDelay)
    }

    @Test
    fun sourceTitleLimitRunsBeforeIndependentGridOrListLimit() {
        val title = "1234567890abcdefghijABCDEFGHIJ"

        val sourceTen = truncateCompatCatalogSourceTitle(title, sourceLimit = 10)
        assertEquals("1234567890", sourceTen)
        assertEquals(
            "1234567890",
            sourceTen.orEmpty().take(30),
            "A longer grid limit must not recover text discarded by CatalogLoader"
        )

        val sourceThirty = truncateCompatCatalogSourceTitle(title, sourceLimit = 30)
        assertEquals("1234567890", sourceThirty.orEmpty().take(10))
    }

    @Test
    fun sourceTitleLimitMatchesReferenceBoundsAndPreservesMissingTitles() {
        val title = "1234567890abcdefghijABCDEFGHIJ-extra"

        assertEquals("1234567890", truncateCompatCatalogSourceTitle(title, sourceLimit = 0))
        assertEquals(title.take(30), truncateCompatCatalogSourceTitle(title, sourceLimit = 100))
        assertNull(truncateCompatCatalogSourceTitle(null, sourceLimit = 20))
    }

    @Test
    fun fullProjectionMatchesSynchronousGoldenAcrossSupportedCatalogSizes() {
        listOf(0, 50, 300, 1_000, 3_000).forEach { size ->
            val request = projectionRequest(size)

            assertEquals(
                referenceProjection(request),
                buildCompatCatalogProjection(request),
                "projection changed for $size catalog entries"
            )
        }
    }

    @Test
    fun projectionCalculationDispatchesTheWholePipeline() = runBlocking {
        val dispatcher = RecordingDispatcher()

        val projected = calculateCompatCatalogProjection(
            request = projectionRequest(300),
            dispatcher = dispatcher
        )

        assertEquals(referenceProjection(projectionRequest(300)), projected)
        assertTrue(dispatcher.dispatchCount > 0, "projection was not dispatched off its caller context")
    }

    @Test
    fun projectionChecksCancellationThroughoutLargeCatalogs() {
        var checkpoints = 0

        assertFailsWith<CancellationException> {
            buildCompatCatalogProjection(projectionRequest(3_000)) {
                checkpoints += 1
                if (checkpoints == 4) throw CancellationException("superseded search generation")
            }
        }

        assertEquals(4, checkpoints)
    }

    @Test
    fun largeProjectionKeepsDeterministicWorkWithinRegressionBudget() {
        val request = projectionRequest(3_000)

        val elapsed = measureTime {
            repeat(10) {
                assertEquals(referenceProjection(request), buildCompatCatalogProjection(request))
            }
        }

        assertTrue(elapsed < 5.seconds, "3000-item projection regression: $elapsed")
    }

    private fun projectionRequest(size: Int): CompatCatalogProjectionRequest {
        val items = List(size) { index ->
            val marker = when {
                index % 13 == 0 -> "hidden"
                index % 11 == 0 -> "extract"
                index % 7 == 0 -> "watch"
                else -> "ordinary"
            }
            CatalogItem(
                id = index.toString(),
                threadUrl = "https://may.2chan.net/b/res/$index.htm",
                title = "item-$index-$marker-title-that-is-long",
                thumbnailUrl = "https://may.2chan.net/b/thumb/${index}s.jpg",
                fullImageUrl = null,
                replyCount = index % 10
            )
        }
        val rules = listOf(
            CompatNgRule("ignore", CompatNgKind.CATALOG_IGNORE, "board", "hidden", 1L),
            CompatNgRule("refuse", CompatNgKind.CATALOG_REFUSE, "board", "17", 2L),
            CompatNgRule("extract", CompatNgKind.CATALOG_EXTRACT, "board", "extract", 3L),
            CompatNgRule(
                "phash",
                CompatNgKind.CATALOG_IMAGE_PHASH,
                "board",
                "0000000000000000",
                4L
            )
        )
        return CompatCatalogProjectionRequest(
            items = items,
            resolvedTitles = items.asSequence()
                .filter { it.id.toInt() % 19 == 0 }
                .associate { it.id to "item-${it.id}-resolved-watch-title-that-is-long" },
            searchQuery = "item",
            preference = CompatCatalogPreference(
                boardKey = "board",
                replyPriorityEnabled = true,
                showNonPriority = true
            ),
            priorityThreshold = 5,
            catalogNgEnabled = true,
            watchWords = listOf("watch"),
            catalogImagePhashRules = rules.filter {
                it.kind == CompatNgKind.CATALOG_IMAGE_PHASH
            },
            catalogImagePhashes = items.asSequence()
                .filter { it.id.toInt() % 23 == 0 }
                .associate { it.id to "0000000000000000" },
            catalogImageNgPhashThreshold = 0,
            catalogRuleIndex = buildCompatCatalogRuleIndex(rules),
            catalogSourceTitleLength = 20
        )
    }

    private fun referenceProjection(request: CompatCatalogProjectionRequest): List<CatalogItem> {
        val itemsWithResolvedTitles = request.items.map { item ->
            val resolvedTitle = request.resolvedTitles[item.id]
                ?.takeIf(String::isNotBlank)
                ?: item.title
            item.copy(
                title = truncateCompatCatalogSourceTitle(
                    resolvedTitle,
                    request.catalogSourceTitleLength
                )
            )
        }
        val hiddenPhashItems = itemsWithResolvedTitles.filter { item ->
            val phash = request.catalogImagePhashes[item.id]
            phash != null && request.catalogImagePhashRules.any {
                CompatImagePhash.isSimilar(
                    phash,
                    it.normalizedValue,
                    request.catalogImageNgPhashThreshold
                )
            }
        }.mapTo(mutableSetOf(), CatalogItem::id)
        val ngFiltered = if (!request.catalogNgEnabled) {
            itemsWithResolvedTitles
        } else {
            itemsWithResolvedTitles.filterNot { item ->
                request.catalogRuleIndex.hides(item) || item.id in hiddenPhashItems
            }
        }
        val normalizedQuery = com.valoser.futacha.shared.model.normalizeCatalogSearchText(
            request.searchQuery
        )
        val filtered = if (normalizedQuery.isBlank()) {
            ngFiltered
        } else {
            ngFiltered.filter { item ->
                com.valoser.futacha.shared.model.normalizeCatalogSearchText(
                    item.title.orEmpty()
                ).contains(normalizedQuery) ||
                    item.id.contains(request.searchQuery, ignoreCase = true)
            }
        }
        return projectCompatCatalogItems(
            items = filtered,
            replyPriorityEnabled = request.preference.replyPriorityEnabled,
            replyThreshold = request.priorityThreshold,
            showNonPriority = request.preference.showNonPriority,
            isExtracted = { item ->
                request.watchWords.any { word ->
                    item.title.orEmpty().contains(word, ignoreCase = true)
                } || request.catalogRuleIndex.extracts(item)
            }
        )
    }

    private class RecordingDispatcher : CoroutineDispatcher() {
        var dispatchCount: Int = 0

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatchCount += 1
            block.run()
        }
    }

    private fun item(id: String, replies: Int) = CatalogItem(
        id = id,
        threadUrl = "https://may.2chan.net/b/res/$id.htm",
        title = id,
        thumbnailUrl = null,
        fullImageUrl = null,
        replyCount = replies
    )
}
