package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.*
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.ui.compat.*
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.withContext

internal data class FutachaThreadProjection(
    val tabKey: String,
    val boardKey: String,
    val extraction: CompatExtractionKind? = null
)
internal val LocalFutachaThreadProjection = staticCompositionLocalOf<FutachaThreadProjection?> { null }

/** The preferences a thread projection depends on; other keys must not restart it. */
internal data class FutachaThreadProjectionSettings(
    val ngEnabled: Boolean,
    val saidaneThreshold: Int,
    val quoteThreshold: Int
) {
    companion object {
        fun from(preferences: Map<String, String>) = FutachaThreadProjectionSettings(
            ngEnabled = preferences.compatPreferenceValue("thread", "threadNg", "NG機能") != "OFF",
            saidaneThreshold = preferences.compatPreferenceValue("thread", "threadExtractSoudaneNum", "そうだねが多いレス")?.toIntOrNull() ?: 3,
            quoteThreshold = preferences.compatPreferenceValue("thread", "threadExtractQuoteNum", "返信が多いレス")?.toIntOrNull() ?: 3
        )
    }
}

internal fun projectFutachaThread(
    source: ThreadPage,
    normallyFiltered: ThreadPage,
    context: FutachaThreadProjection,
    rules: List<CompatNgRule>,
    preferences: Map<String, String>,
    phashHidden: Set<String> = emptySet(),
    modernNgHidden: Set<String> = emptySet()
): ThreadPage = projectFutachaThread(
    source, normallyFiltered, context, rules, FutachaThreadProjectionSettings.from(preferences), phashHidden, modernNgHidden
)

internal fun projectFutachaThread(
    source: ThreadPage,
    normallyFiltered: ThreadPage,
    context: FutachaThreadProjection,
    rules: List<CompatNgRule>,
    settings: FutachaThreadProjectionSettings,
    phashHidden: Set<String> = emptySet(),
    modernNgHidden: Set<String> = emptySet()
): ThreadPage {
    if (context.extraction == null && (!settings.ngEnabled || (rules.isEmpty() && phashHidden.isEmpty()))) {
        return normallyFiltered
    }
    val snapshots = source.toCompatThreadSnapshot(context.tabKey, 0).posts
    val index = buildCompatThreadNgRuleIndex(rules, context.tabKey, context.boardKey)
    val hidden = snapshots.filter { it.postNo in phashHidden || it.matchesCompatThreadNg(index) }
        .mapTo(mutableSetOf(), CompatPostSnapshot::postNo).apply { addAll(modernNgHidden) }
    val selected = context.extraction?.let { kind ->
        if (kind == CompatExtractionKind.NG) hidden else extractCompatPosts(
            snapshots, kind, context.tabKey, rules, context.boardKey,
            saidaneThreshold = settings.saidaneThreshold,
            quoteThreshold = settings.quoteThreshold
        ).mapTo(mutableSetOf(), CompatPostSnapshot::postNo)
    }
    val candidates = if (selected != null) source else normallyFiltered
    val ngEnabled = settings.ngEnabled
    return candidates.copy(posts = candidates.posts.filter { post ->
        (selected == null || post.id in selected) &&
            (context.extraction == CompatExtractionKind.NG || !ngEnabled || post.id !in hidden)
    })
}

/**
 * The Futacha page with the shared (compatibility) NG rules and extraction
 * applied. Returns null while a needed projection is still running, so the
 * initial frame waits instead of exposing posts that NG rules will hide. Later
 * projections keep the last accepted page mounted; when
 * nothing needs hiding or extracting the normal page is returned at once.
 * Image NG needs network hashes and is applied as its results arrive.
 */
@Composable
internal fun rememberFutachaFilteredThreadPage(
    source: ThreadPage,
    normallyFiltered: ThreadPage,
    ngHeaders: List<String>,
    ngWords: List<String>
): ThreadPage? {
    val features = LocalFutachaSharedFeatures.current ?: return normallyFiltered
    val context = LocalFutachaThreadProjection.current ?: return normallyFiltered
    // null until the store delivered its rules; an empty initial list showed NG'd posts first.
    val loadedRules by features.store.ngRules.collectAsState<List<CompatNgRule>, List<CompatNgRule>?>(features.ngRulesState?.value)
    val rules = remember(loadedRules, context.tabKey, context.boardKey) {
        loadedRules?.filter { rule -> when (rule.kind) {
            CompatNgKind.THREAD_IMAGE, CompatNgKind.THREAD_IMAGE_PHASH ->
                rule.appliesToThreadImage(context.boardKey, context.tabKey)
            CompatNgKind.THREAD_POST_NO, CompatNgKind.THREAD_POSTER_ID, CompatNgKind.THREAD_WORD,
            CompatNgKind.THREAD_IGNORE, CompatNgKind.THREAD_REFUSE ->
                rule.scopeKey == "*" || rule.scopeKey == context.tabKey
            else -> false
        } }
    }
    val phashRules = remember(rules, context) { rules.orEmpty().filter {
        it.kind == CompatNgKind.THREAD_IMAGE_PHASH && it.appliesToThreadImage(context.boardKey, context.tabKey)
    } }
    val threshold = features.value("thread", "threadImageNgPhashThreshold")
        ?.toIntOrNull() ?: CompatImagePhash.DEFAULT_THRESHOLD
    val phashHidden by produceState(emptySet<String>(), source, phashRules, threshold) {
        if (phashRules.isEmpty()) {
            value = emptySet()
            return@produceState
        }
        val posts = withContext(AppDispatchers.parsing) { source.toCompatThreadSnapshot(context.tabKey, 0).posts }
        value = compatImagePhashHiddenPostNos(features.httpClient, posts, phashRules, threshold)
    }
    val settings = FutachaThreadProjectionSettings(
        ngEnabled = features.value("thread", "threadNg", "NG機能") != "OFF",
        saidaneThreshold = features.value("thread", "threadExtractSoudaneNum", "そうだねが多いレス")?.toIntOrNull() ?: 3,
        quoteThreshold = features.value("thread", "threadExtractQuoteNum", "返信が多いレス")?.toIntOrNull() ?: 3
    )
    val needsProjection = rules == null || context.extraction != null ||
        (settings.ngEnabled && rules.isNotEmpty())
    val result = key(context.tabKey) {
    val projected by produceState<ThreadPage?>(null, source, normallyFiltered, context, rules,
        settings, phashHidden, ngHeaders, ngWords) {
        val currentRules = rules ?: return@produceState
        value = withContext(AppDispatchers.parsing) {
            val modernVisible = applyNgFilters(source, ngHeaders, ngWords, true).posts.mapTo(hashSetOf()) { it.id }
            projectFutachaThread(source, normallyFiltered, context, currentRules, settings,
                phashHidden, source.posts.filter { it.id !in modernVisible }.mapTo(hashSetOf()) { it.id })
        }
    }
    projected
    }
    if (!needsProjection) return normallyFiltered
    result?.let { return it }
    // Modern NG is already applied to normallyFiltered; only shared rules or an
    // extraction can hide more.
    return null
}
