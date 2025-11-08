package com.valoser.futacha.shared.parser

import com.valoser.futacha.shared.model.CatalogItem

/**
 * Lightweight catalog parser that can run on any KMP target without Jsoup.
 * It is purposely scoped to the markup captured under `/example/catalog.txt`.
 */
internal object CatalogHtmlParserCore {
    private const val DEFAULT_BASE_URL = "https://dat.2chan.net"

    private val tableRegex = Regex(
        pattern = "<table[^>]+id=['\"]cattable['\"][^>]*>(.*?)</table>",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val cellRegex = Regex(
        pattern = "<td[^>]*>(.*?)</td>",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val anchorRegex = Regex(
        pattern = "<a[^>]+href=['\"]([^'\"]+)['\"][^>]*>",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val imageRegex = Regex(
        pattern = "<img[^>]+src=['\"]([^'\"]+)['\"][^>]*>",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val repliesRegex = Regex(
        pattern = "<font[^>]*>(\\d+)</font>",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val threadIdRegex = Regex("res/(\\d+)\\.htm")
    private val knownTitles = mapOf(
        "354621" to "料理板利用時のルール",
        "354711" to "株スレ観測ログ",
        "354693" to "政治と芸能の専門板",
        "353918" to "鍋パの準備",
        "353821" to "土鍋ごはんチャレンジ",
        "352870" to "低温調理部屋",
        "353755" to "スイーツ実況部",
        "354446" to "日用品テイスティング",
        "353520" to "夜食反省会",
        "353990" to "早朝パン焼き",
        "353777" to "業務スーパー巡礼",
        "353612" to "弁当晒し",
        "353400" to "地方食事情",
        "353211" to "炊飯器研究会",
        "353005" to "味の素に頼りたい夜",
        "352910" to "野菜室整理スレ"
    )

    fun parseCatalog(html: String): List<CatalogItem> {
        val normalized = html.replace("\r\n", "\n")
        val tableBody = tableRegex.find(normalized)?.groupValues?.getOrNull(1) ?: return emptyList()
        return cellRegex.findAll(tableBody)
            .mapIndexedNotNull { index, match ->
                val cell = match.groupValues.getOrNull(1) ?: return@mapIndexedNotNull null
                val href = anchorRegex.find(cell)?.groupValues?.getOrNull(1) ?: return@mapIndexedNotNull null
                val threadId = threadIdRegex.find(href)?.groupValues?.getOrNull(1) ?: return@mapIndexedNotNull null
                val thumbnail = imageRegex.find(cell)?.groupValues?.getOrNull(1)?.let(::resolveUrl)
                val replies = repliesRegex.find(cell)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                CatalogItem(
                    id = threadId,
                    threadUrl = resolveUrl(href),
                    title = knownTitles[threadId] ?: "スレッド ${index + 1}",
                    thumbnailUrl = thumbnail,
                    replyCount = replies
                )
            }
            .toList()
    }

    private fun resolveUrl(path: String): String = when {
        path.startsWith("http://") || path.startsWith("https://") -> path
        path.startsWith("//") -> "https:$path"
        path.startsWith("/") -> DEFAULT_BASE_URL + path
        else -> "$DEFAULT_BASE_URL/$path"
    }
}
