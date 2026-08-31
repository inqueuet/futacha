package com.valoser.futacha.shared.compat

enum class CompatHeaderExtractionKind { QUOTE, ID, IP }

sealed interface CompatHeaderTapTarget {
    data class Url(val value: String) : CompatHeaderTapTarget
    data class Email(val value: String) : CompatHeaderTapTarget
}

data class CompatPosterIdentity(val kind: CompatHeaderExtractionKind, val value: String) {
    val display: String get() = "${kind.name}:$value"
}

/** The ordinal and total count shown after an ID/IP in the reference header. */
data class CompatPosterIdentityProgress(
    val identity: CompatPosterIdentity,
    val current: Int,
    val total: Int
) {
    val label: String get() = "$current/$total"
}

fun parseCompatPosterIdentity(raw: String?): CompatPosterIdentity? {
    val value = raw?.trim().orEmpty()
    if (value.isBlank()) return null
    return when {
        value.startsWith("IP:", ignoreCase = true) -> CompatPosterIdentity(
            CompatHeaderExtractionKind.IP,
            value.substringAfter(':').trim()
        )
        value.startsWith("ID:", ignoreCase = true) -> CompatPosterIdentity(
            CompatHeaderExtractionKind.ID,
            value.substringAfter(':').trim()
        )
        else -> CompatPosterIdentity(CompatHeaderExtractionKind.ID, value)
    }.takeIf { it.value.isNotBlank() }
}

private val compatHeaderIdentityPattern = Regex("(?:ID|IP):[^\\s<]+", RegexOption.IGNORE_CASE)

/**
 * Returns every visible ID/IP token in source order.  New parser snapshots normally keep
 * an ID in posterId, while IP (and older cached IDs) can still be present in timestamp.
 */
fun compatPosterIdentities(post: CompatPostSnapshot): List<CompatPosterIdentity> {
    val fromTimestamp = compatHeaderIdentityPattern.findAll(post.timestamp)
        .mapNotNull { parseCompatPosterIdentity(it.value) }
        .toList()
    return (fromTimestamp + listOfNotNull(parseCompatPosterIdentity(post.posterId)))
        .distinct()
}

/**
 * Futaba normally exposes an ID/IP inside the timestamp span. The shared parser currently
 * promotes IDs to [CompatPostSnapshot.posterId], while legacy IP headers can remain only in
 * [CompatPostSnapshot.timestamp]. Keep the compatibility layer tolerant of both shapes so that
 * cached snapshots and boards using IP display behave like the original application.
 */
fun compatPosterIdentity(post: CompatPostSnapshot): CompatPosterIdentity? =
    parseCompatPosterIdentity(post.posterId)
        ?: compatPosterIdentities(post).firstOrNull()

/**
 * Builds the exact current/total values used by sample/1.apk.  Counts are calculated from
 * the complete raw snapshot, not the currently filtered rows, so NG and visibility changes
 * cannot change an ID's displayed total.
 */
fun compatPosterIdentityProgress(
    post: CompatPostSnapshot,
    posts: List<CompatPostSnapshot>
): List<CompatPosterIdentityProgress> {
    val identities = compatPosterIdentities(post)
    if (identities.isEmpty()) return emptyList()
    return identities.map { identity ->
        val matching = posts.filter { candidate ->
            identity in compatPosterIdentities(candidate)
        }
        CompatPosterIdentityProgress(
            identity = identity,
            current = matching.count { it.position <= post.position }.coerceAtLeast(1),
            total = matching.size.coerceAtLeast(1)
        )
    }
}

/**
 * Builds identity progress for an entire snapshot in one pass.
 *
 * The straightforward implementation above is useful for an individual post,
 * but calling it for every post makes thread rendering O(n²) and reparses the
 * ID/IP regexes repeatedly. A live Futaba thread can contain thousands of
 * responses, so the UI must precompute the per-identity totals and ordinals
 * once before composing the LazyColumn.
 */
fun compatPosterIdentityProgressByPost(
    posts: List<CompatPostSnapshot>
): Map<String, List<CompatPosterIdentityProgress>> {
    if (posts.isEmpty()) return emptyMap()
    val identitiesByPostNo = posts.associate { post ->
        post.postNo to compatPosterIdentities(post)
    }
    val totals = identitiesByPostNo.values
        .flatten()
        .groupingBy { it }
        .eachCount()
    val currentByIdentity = mutableMapOf<CompatPosterIdentity, Int>()
    val result = mutableMapOf<String, List<CompatPosterIdentityProgress>>()
    posts.sortedBy(CompatPostSnapshot::position).forEach { post ->
        result[post.postNo] = identitiesByPostNo[post.postNo].orEmpty().map { identity ->
            val current = (currentByIdentity[identity] ?: 0) + 1
            currentByIdentity[identity] = current
            CompatPosterIdentityProgress(
                identity = identity,
                current = current.coerceAtLeast(1),
                total = totals[identity].orZero().coerceAtLeast(1)
            )
        }
    }
    return result
}

private fun Int?.orZero(): Int = this ?: 0

/**
 * Counts all posts carrying the same visible ID/IP.  The old viewer shows this
 * next to the identity, and counting the raw snapshot (rather than the NG
 * filtered list) keeps the number stable while the user changes filters.
 */
fun compatPosterReplyCounts(posts: List<CompatPostSnapshot>): Map<CompatPosterIdentity, Int> =
    posts.mapNotNull(::compatPosterIdentity).groupingBy { it }.eachCount()

fun compatPosterReplyCount(
    post: CompatPostSnapshot,
    counts: Map<CompatPosterIdentity, Int>
): Int? = compatPosterIdentity(post)?.let(counts::get)?.takeIf { it > 1 }

fun compatHeaderTapTarget(text: String): CompatHeaderTapTarget? {
    val url = Regex("https?://[^\\s<>]+", RegexOption.IGNORE_CASE).find(text)?.value
    if (url != null) return CompatHeaderTapTarget.Url(url.trimEnd('.', ',', ')', ']', '」', '。'))
    compatInlineLinks(text).firstOrNull()?.let { return CompatHeaderTapTarget.Url(it.url) }
    val email = Regex("[-+.0-9a-z]+@[-a-z0-9]+(?:\\.[-a-z0-9]+)*\\.[a-z]{2,6}", RegexOption.IGNORE_CASE)
        .find(text)?.value
    return email?.let(CompatHeaderTapTarget::Email)
}

fun compatHeaderText(post: CompatPostSnapshot): String = listOfNotNull(
    post.subject?.takeIf(String::isNotBlank),
    post.author?.takeIf(String::isNotBlank),
    post.timestamp.takeIf(String::isNotBlank),
    post.mail?.takeIf(String::isNotBlank),
    compatPosterIdentity(post)?.display
        ?.takeUnless { post.timestamp.contains(it, ignoreCase = true) },
    "No.${post.postNo}"
).joinToString(" ")

fun compatHeaderExtractionKinds(
    post: CompatPostSnapshot,
    posts: List<CompatPostSnapshot>
): List<CompatHeaderExtractionKind> = buildList {
    if (extractCompatHeaderPosts(posts, post, CompatHeaderExtractionKind.QUOTE).isNotEmpty() || post.referencedCount > 0) {
        add(CompatHeaderExtractionKind.QUOTE)
    }
    compatPosterIdentities(post).map(CompatPosterIdentity::kind).distinct().forEach { add(it) }
}

fun extractCompatHeaderPosts(
    posts: List<CompatPostSnapshot>,
    source: CompatPostSnapshot,
    kind: CompatHeaderExtractionKind
): List<CompatPostSnapshot> = when (kind) {
    CompatHeaderExtractionKind.QUOTE -> posts.filter { candidate ->
        candidate.position > source.position && candidate.messageHtml.toCompatPlainText().lineSequence().any { line ->
            val query = compatQuoteQueryForLine(line.trimStart()) ?: return@any false
            when {
                query.startsWith("no:", ignoreCase = true) -> query.substringAfter(':').trim() == source.postNo
                query.startsWith("id:", ignoreCase = true) -> source.compatHasIdentity(
                    CompatHeaderExtractionKind.ID,
                    query.substringAfter(':').trim()
                )
                query.startsWith("ip:", ignoreCase = true) -> source.compatHasIdentity(
                    CompatHeaderExtractionKind.IP,
                    query.substringAfter(':').trim()
                )
                query.startsWith("file:", ignoreCase = true) -> compatPostMediaFileNames(source).any {
                    it.equals(query.substringAfter(':').trim(), ignoreCase = true)
                }
                query.startsWith("text:", ignoreCase = true) -> source.matchesCompatQuote(query.substringAfter(':'))
                else -> false
            }
        }
    }
    CompatHeaderExtractionKind.ID,
    CompatHeaderExtractionKind.IP -> {
        val identities = compatPosterIdentities(source).filter { it.kind == kind }
        if (identities.isEmpty()) emptyList()
        else posts.filter { candidate ->
            compatPosterIdentities(candidate).any { it in identities }
        }
    }
}

private fun CompatPostSnapshot.compatHasIdentity(
    kind: CompatHeaderExtractionKind,
    value: String
): Boolean = compatPosterIdentities(this).any { identity ->
    identity.kind == kind && identity.value == value
}
