package com.valoser.futacha.shared.compat

/**
 * The reference APK keeps ThreadRefuse (header words) and ThreadIgnore (body
 * words) in separate tables. Earlier Futacha builds also wrote post numbers
 * and poster identities to dedicated kinds, so the ThreadRefuse manager must
 * keep those entries visible and editable instead of leaving active rules
 * that the user cannot remove.
 */
fun compatThreadReferenceKinds(kind: CompatNgKind): Set<CompatNgKind> = when (kind) {
    CompatNgKind.THREAD_REFUSE -> setOf(
        CompatNgKind.THREAD_REFUSE,
        CompatNgKind.THREAD_POST_NO,
        CompatNgKind.THREAD_POSTER_ID
    )
    CompatNgKind.THREAD_IGNORE -> setOf(CompatNgKind.THREAD_IGNORE)
    else -> setOf(kind)
}

fun compatThreadReferenceRules(
    rules: List<CompatNgRule>,
    threadKey: String,
    kind: CompatNgKind
): List<CompatNgRule> {
    val acceptedKinds = compatThreadReferenceKinds(kind)
    return rules.asSequence()
        .filter { it.kind in acceptedKinds && (it.scopeKey == threadKey || it.scopeKey == "*") }
        .sortedBy { normalizeCompatSearchText(compatThreadReferenceDisplayValue(it)) }
        .toList()
}

/** Preserve the exact word typed in the reference editor while matching NFKC/case-insensitively. */
fun compatThreadReferenceDisplayValue(rule: CompatNgRule): String =
    rule.memo.takeIf(String::isNotBlank) ?: when (rule.kind) {
        CompatNgKind.THREAD_POST_NO -> rule.normalizedValue
            .takeIf { it.all(Char::isDigit) }
            ?.let { "No.$it" }
            ?: rule.normalizedValue
        CompatNgKind.THREAD_POSTER_ID ->
            parseCompatPosterIdentity(rule.normalizedValue)?.display ?: rule.normalizedValue
        else -> rule.normalizedValue
    }

/** Equivalent to ThreadNgWordUtil.cleanInput(word, 20) in 1.apk. */
fun cleanCompatThreadReferenceWord(value: String, maxLength: Int = 20): String {
    fun String.trimReferenceEdges(): String = trim { it <= ' ' || it == '\u3000' || it.isWhitespace() }
    val trimmed = value.trimReferenceEdges()
    return if (maxLength > 0 && trimmed.length > maxLength) {
        trimmed.take(maxLength).trimReferenceEdges()
    } else {
        trimmed
    }
}

fun isCompatThreadRefuseForbidden(value: String): Boolean =
    cleanCompatThreadReferenceWord(value) in setOf("無念", "無題", "としあき", "名無し")

/**
 * Manual ThreadRefuse additions in 1.apk reject a duplicate regardless of
 * scope. ThreadIgnore, and both edit dialogs, compare the target scope too.
 */
fun hasCompatThreadReferenceDuplicate(
    rules: List<CompatNgRule>,
    kind: CompatNgKind,
    value: String,
    globalScope: Boolean,
    excludingRuleId: String? = null,
    editing: Boolean = false
): Boolean {
    val normalized = normalizeCompatSearchText(cleanCompatThreadReferenceWord(value))
    if (normalized.isBlank()) return false
    return rules.any { rule ->
        if (rule.id == excludingRuleId) return@any false
        val sameWord = normalizeCompatSearchText(compatThreadReferenceDisplayValue(rule)) == normalized
        val sameScope = (rule.scopeKey == "*") == globalScope
        sameWord && if (kind == CompatNgKind.THREAD_REFUSE && !editing) true else sameScope
    }
}

data class CompatThreadNgRegistrationCandidate(
    val kind: CompatNgKind,
    val value: String
)

/** Exact raw-value list built by ThreadNgRegisterDialogFragment in 1.apk. */
fun compatReferenceThreadNgCandidates(post: CompatPostSnapshot): List<CompatThreadNgRegistrationCandidate> =
    buildList {
        post.subject
            ?.let { cleanCompatThreadReferenceWord(it, maxLength = 0) }
            ?.takeIf { it.isNotBlank() && it !in setOf("無念", "無題") }
            ?.let { add(CompatThreadNgRegistrationCandidate(CompatNgKind.THREAD_REFUSE, it)) }
        post.author
            ?.let { cleanCompatThreadReferenceWord(it, maxLength = 0) }
            ?.takeIf {
                it.isNotBlank() && it !in setOf("としあき", "名無し") && !it.contains("href")
            }
            ?.let { add(CompatThreadNgRegistrationCandidate(CompatNgKind.THREAD_REFUSE, it)) }
        compatPosterIdentities(post).sortedBy { identity ->
            when (identity.kind) {
                CompatHeaderExtractionKind.ID -> 0
                CompatHeaderExtractionKind.IP -> 1
                CompatHeaderExtractionKind.QUOTE -> 2
            }
        }.forEach { identity ->
            add(CompatThreadNgRegistrationCandidate(CompatNgKind.THREAD_REFUSE, identity.display))
        }
        post.postNo.takeIf(String::isNotBlank)?.let {
            add(CompatThreadNgRegistrationCandidate(CompatNgKind.THREAD_REFUSE, "No.$it"))
        }
        compatSelectablePostBodyLines(post)
            .asSequence()
            .filterNot { it.trimStart().startsWith(">") }
            .map { cleanCompatThreadReferenceWord(it) }
            .filter(String::isNotBlank)
            .forEach {
                add(CompatThreadNgRegistrationCandidate(CompatNgKind.THREAD_IGNORE, it))
            }
    }
