package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.mix.MixEntry
import com.valoser.futacha.shared.model.normalizeCatalogSearchText
import com.valoser.futacha.shared.model.normalizeWatchSearchText
import com.valoser.futacha.shared.ui.compat.compatCommonNgApplyEnabled
import com.valoser.futacha.shared.ui.compat.compatCommonNgFtutterRules
import com.valoser.futacha.shared.ui.compat.compatPreferenceValue
import com.valoser.futacha.shared.ui.ftutter.FtutterKeys
import com.valoser.futacha.shared.ui.futaber.futaberSharedNgMatcher
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Runs all title/thread NG work, including regex preparation, away from the UI. */
internal suspend fun calculateMixCatalogProjection(
    entries: List<MixEntry>,
    rules: List<CompatNgRule>,
    words: List<String>,
    preferences: Map<String, String>,
    policy: MixModePolicy,
    catalogWordNgEnabled: Boolean = true,
    projectEntries: suspend (List<MixEntry>) -> List<MixEntry> = { it },
    dispatcher: CoroutineDispatcher = AppDispatchers.parsing,
    resolvedTitles: Map<String, String> = emptyMap()
): List<MixEntry> = withContext(dispatcher) {
    val context = currentCoroutineContext()
    val checkpoint = { context.ensureActive() }
    val titled = entries.map { entry -> checkpoint(); mixEntryWithHeadTitle(entry, resolvedTitles) }
    val projected = projectEntries(titled)
    checkpoint()
    if (!mixCatalogNgEnabled(preferences, policy)) return@withContext projected
    val commonOn = compatCommonNgApplyEnabled(preferences)
    fun normalizeWord(text: String): String = when (policy) {
        MixModePolicy.FUTACHA -> normalizeWatchSearchText(text)
        MixModePolicy.COMPAT -> normalizeCompatSearchText(text)
        else -> normalizeCatalogSearchText(text)
    }
    val effectiveWords = when (policy) {
        MixModePolicy.FUTACHA, MixModePolicy.FUTABER -> words
        MixModePolicy.COMPAT -> words.takeIf { commonOn }.orEmpty()
        // This mode's adapter already applies its own NG and the common-word switch.
        MixModePolicy.FTUTTER -> emptyList()
    }.map(::normalizeWord).filter(String::isNotEmpty)
    val extraRules = if (policy == MixModePolicy.COMPAT && commonOn)
        compatCommonNgFtutterRules(preferences[FtutterKeys.NG_LIST]) else emptyList()
    val byRules = mixEntriesWithoutNg(projected, rules + extraRules, catalogWordNgEnabled, checkpoint)
    val matchers = HashMap<String, com.valoser.futacha.shared.ui.ftutter.FtutterNgMatcher?>()
    byRules.filter { entry ->
        checkpoint()
        val title = entry.item.title.orEmpty()
        val normalized = if (effectiveWords.isEmpty()) "" else normalizeWord(title)
        val matcher = if (policy == MixModePolicy.FUTABER) matchers.getOrPut(entry.key.boardUrl) {
            futaberSharedNgMatcher(preferences[FtutterKeys.NG_LIST], commonOn, entry.board.url)
        } else null
        effectiveWords.none(normalized::contains) && matcher?.hidesTitle(title, checkpoint) != true
    }.also { checkpoint() }
}

/** Read each mode's existing sensitivity with the same legacy-key handling as its normal catalog. */
internal fun mixImageNgThreshold(preferences: Map<String, String>, policy: MixModePolicy): Int {
    val raw = preferences.compatPreferenceValue("thread", "threadImageNgPhashThreshold",
        *if (policy == MixModePolicy.COMPAT) arrayOf("画像NG類似度閾値") else emptyArray())
    return when (policy) {
        MixModePolicy.COMPAT -> raw?.filter(Char::isDigit)?.toIntOrNull()
            ?.coerceIn(CompatImagePhash.MIN_THRESHOLD, CompatImagePhash.MAX_THRESHOLD)
        MixModePolicy.FUTACHA, MixModePolicy.FUTABER -> raw?.toIntOrNull()
        MixModePolicy.FTUTTER -> null
    } ?: CompatImagePhash.DEFAULT_THRESHOLD
}

internal fun mixCatalogNgEnabled(preferences: Map<String, String>, policy: MixModePolicy): Boolean =
    policy != MixModePolicy.COMPAT || preferences["compat.catalog.NG機能"] != "OFF"
