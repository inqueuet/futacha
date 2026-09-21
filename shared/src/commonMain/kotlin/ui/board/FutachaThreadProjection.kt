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

internal fun projectFutachaThread(
    source: ThreadPage,
    normallyFiltered: ThreadPage,
    context: FutachaThreadProjection,
    rules: List<CompatNgRule>,
    preferences: Map<String, String>,
    phashHidden: Set<String> = emptySet(),
    modernNgHidden: Set<String> = emptySet()
): ThreadPage {
    val snapshots = source.toCompatThreadSnapshot(context.tabKey, 0).posts
    val index = buildCompatThreadNgRuleIndex(rules, context.tabKey, context.boardKey)
    val hidden = snapshots.filter { it.postNo in phashHidden || it.matchesCompatThreadNg(index) }
        .mapTo(mutableSetOf(), CompatPostSnapshot::postNo).apply { addAll(modernNgHidden) }
    val selected = context.extraction?.let { kind ->
        if (kind == CompatExtractionKind.NG) hidden else extractCompatPosts(
            snapshots, kind, context.tabKey, rules, context.boardKey,
            saidaneThreshold = preferences.compatPreferenceValue("thread", "threadExtractSoudaneNum", "そうだねが多いレス")?.toIntOrNull() ?: 3,
            quoteThreshold = preferences.compatPreferenceValue("thread", "threadExtractQuoteNum", "返信が多いレス")?.toIntOrNull() ?: 3
        ).mapTo(mutableSetOf(), CompatPostSnapshot::postNo)
    }
    val candidates = if (selected != null) source else normallyFiltered
    val ngEnabled = preferences.compatPreferenceValue("thread", "threadNg", "NG機能") != "OFF"
    return candidates.copy(posts = candidates.posts.filter { post ->
        (selected == null || post.id in selected) &&
            (context.extraction == CompatExtractionKind.NG || !ngEnabled || post.id !in hidden)
    })
}

@Composable
internal fun rememberFutachaFilteredThreadPage(
    source: ThreadPage,
    normallyFiltered: ThreadPage,
    ngHeaders: List<String>,
    ngWords: List<String>
): ThreadPage {
    val features = LocalFutachaSharedFeatures.current ?: return normallyFiltered
    val context = LocalFutachaThreadProjection.current ?: return normallyFiltered
    val rules by features.store.ngRules.collectAsState(emptyList())
    val phashRules = remember(rules, context) { rules.filter {
        it.kind == CompatNgKind.THREAD_IMAGE_PHASH && it.appliesToThreadImage(context.boardKey, context.tabKey)
    } }
    val threshold = features.preferences.compatPreferenceValue("thread", "threadImageNgPhashThreshold")
        ?.toIntOrNull() ?: CompatImagePhash.DEFAULT_THRESHOLD
    val phashHidden by produceState(emptySet<String>(), source, phashRules, threshold) {
        value = compatImagePhashHiddenPostNos(features.httpClient,
            source.toCompatThreadSnapshot(context.tabKey, 0).posts, phashRules, threshold)
    }
    val result by produceState(normallyFiltered, source, normallyFiltered, context, rules,
        features.preferences, phashHidden, ngHeaders, ngWords) {
        value = withContext(AppDispatchers.parsing) {
            val modernVisible = applyNgFilters(source, ngHeaders, ngWords, true).posts.mapTo(hashSetOf()) { it.id }
            projectFutachaThread(source, normallyFiltered, context, rules, features.preferences,
                phashHidden, source.posts.filter { it.id !in modernVisible }.mapTo(hashSetOf()) { it.id })
        }
    }
    return result
}
