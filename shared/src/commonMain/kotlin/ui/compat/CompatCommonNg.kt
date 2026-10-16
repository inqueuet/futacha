package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import com.valoser.futacha.shared.compat.CompatNgKind
import com.valoser.futacha.shared.compat.CompatNgRule
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.ui.board.NgManagementSheet
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/*
 * The common NG lists (feature-parity batch 7): the AppState NG headers / words / catalog words that ふたちゃ and
 * ふたばー風 use.
 *  - Editing: "共通NG（ふたちゃ・ふたばー風と共有）" at the end of the NG menus opens ふたちゃ's NG sheet on the shared lists.
 *  - Applying (switch "共通NGをこのモードでも適用", off by default): the lists become rules of this mode's filters only.
 *    They are never saved and never handed to the rule management dialogs or the NG counts (they could not be deleted
 *    there): the screens get them as a separate list next to their own rules.
 * Nothing exists without a shared AppStateStore.
 */

/** The NG menus' item ("ng_common", added by compatCatalogOtherMenu / compatThreadOtherMenu with commonNgEnabled). */
internal const val COMPAT_COMMON_NG_MENU_KEY = "ng_common"

/** Id prefix of the rules made from the common lists; such a rule is never stored. */
internal const val COMPAT_COMMON_NG_RULE_ID_PREFIX = "appstate-ng:"

internal enum class CompatCommonNgTarget { CATALOG, THREAD }

internal fun isCompatCommonNgRule(rule: CompatNgRule): Boolean = rule.id.startsWith(COMPAT_COMMON_NG_RULE_ID_PREFIX)

/** The rules of [rules] that may be listed, counted or edited (the common ones are not). */
internal fun List<CompatNgRule>.withoutCompatCommonNgRules(): List<CompatNgRule> = filterNot(::isCompatCommonNgRule)

/**
 * The common lists as rules for every board and thread ("*"): NG headers hide by this mode's header text (subject,
 * name, mail, date) and, written as "ID:…" / "IP:…", by that poster; NG words by the body; catalog NG words by the
 * thread title. ふたちゃ also finds a part of an ID inside its header line; that partial ID match is not reproduced.
 * Blank values are dropped.
 */
internal fun compatCommonNgFilterRules(
    ngHeaders: List<String>,
    ngWords: List<String>,
    catalogNgWords: List<String>
): List<CompatNgRule> {
    fun rules(kind: CompatNgKind, values: List<String>): List<CompatNgRule> = values
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .map { value ->
            CompatNgRule(
                id = "$COMPAT_COMMON_NG_RULE_ID_PREFIX${kind.name}:$value",
                kind = kind,
                scopeKey = "*",
                normalizedValue = value,
                createdAtEpochMillis = 0L
            )
        }
    // This mode's header text (subject, name, mail, date) has no ID; an "ID:…" / "IP:…" header also hides that poster.
    val identities = ngHeaders.map(String::trim).filter { value ->
        value.length > 3 && (value.startsWith("ID:", ignoreCase = true) || value.startsWith("IP:", ignoreCase = true))
    }
    return rules(CompatNgKind.THREAD_REFUSE, ngHeaders) +
        rules(CompatNgKind.THREAD_POSTER_ID, identities) +
        rules(CompatNgKind.THREAD_IGNORE, ngWords) +
        rules(CompatNgKind.CATALOG_IGNORE, catalogNgWords)
}

/** [list] with [value] added at the end, unless it is blank or already there (case-insensitively, as ふたちゃ does). */
internal fun compatCommonNgAdd(list: List<String>, value: String): List<String> {
    val trimmed = value.trim()
    if (trimmed.isEmpty() || list.any { it.equals(trimmed, ignoreCase = true) }) return list
    return list + trimmed
}

@Stable
internal class CompatCommonNg(
    private val stateStore: AppStateStore,
    private val store: CompatibilityStore,
    private val scope: CoroutineScope
) {
    var ngHeaders by mutableStateOf<List<String>>(emptyList())
        internal set
    var ngWords by mutableStateOf<List<String>>(emptyList())
        internal set
    var catalogNgWords by mutableStateOf<List<String>>(emptyList())
        internal set
    var applyEnabled by mutableStateOf(false)
        internal set
    /** Told when a change could not be stored (shown by the NG sheet; audit L35). */
    var failureNotice by mutableStateOf<String?>(null)
    var sheet by mutableStateOf<CompatCommonNgTarget?>(null)

    private val filterRulesState = derivedStateOf {
        if (applyEnabled) compatCommonNgFilterRules(ngHeaders, ngWords, catalogNgWords) else emptyList()
    }

    /** What the filters add to this mode's own rules: empty while the switch is off. */
    val filterRules: List<CompatNgRule> get() = filterRulesState.value

    fun open(target: CompatCommonNgTarget) { sheet = target }

    fun addHeader(value: String) = edit("NG header") { stateStore.updateNgHeaders { compatCommonNgAdd(it, value) } }
    fun removeHeader(value: String) = edit("NG header") { stateStore.updateNgHeaders { it - value } }
    fun addWord(target: CompatCommonNgTarget, value: String) = edit("NG word") {
        if (target == CompatCommonNgTarget.CATALOG) {
            stateStore.updateCatalogNgWords { compatCommonNgAdd(it, value) }
        } else {
            stateStore.updateNgWords { compatCommonNgAdd(it, value) }
        }
    }
    fun removeWord(target: CompatCommonNgTarget, value: String) = edit("NG word") {
        if (target == CompatCommonNgTarget.CATALOG) {
            stateStore.updateCatalogNgWords { it - value }
        } else {
            stateStore.updateNgWords { it - value }
        }
    }

    /** The sheet's "NGを有効／無効にする" is this mode's switch for applying the common lists. */
    fun toggleApply() {
        val next = !applyEnabled
        applyEnabled = next
        // A switch that could not be stored goes back and says so (audit L35: it stayed flipped on screen only, and the
        // next change of the settings brought the old value back without a word).
        edit("common NG switch", onFailure = {
            applyEnabled = !next
            failureNotice = "NGの有効・無効を保存できませんでした"
        }) { store.savePreference(COMPAT_COMMON_NG_APPLY_STORAGE_KEY, if (next) "ON" else "OFF") }
    }

    private fun edit(operation: String, onFailure: () -> Unit = { failureNotice = "共通NGを保存できませんでした" }, block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                Logger.e("CompatCommonNg", "$operation failed", failure)
                onFailure()
            }
        }
    }
}

/** The common NG lists, or null without a shared store. */
@Composable
internal fun rememberCompatCommonNg(
    stateStore: AppStateStore?,
    store: CompatibilityStore,
    preferences: Map<String, String>
): CompatCommonNg? {
    val scope = rememberCoroutineScope()
    val commonNg = remember(stateStore, store, scope) { stateStore?.let { CompatCommonNg(it, store, scope) } }
    val applyEnabled = compatCommonNgApplyEnabled(preferences)
    LaunchedEffect(commonNg, applyEnabled) { commonNg?.applyEnabled = applyEnabled }
    LaunchedEffect(commonNg) {
        val target = commonNg ?: return@LaunchedEffect
        launch { stateStore?.ngHeaders?.collect { target.ngHeaders = it } }
        launch { stateStore?.ngWords?.collect { target.ngWords = it } }
        launch { stateStore?.catalogNgWords?.collect { target.catalogNgWords = it } }
    }
    return commonNg
}

/** ふたちゃ's NG sheet on the shared lists; the catalog one has no header section (as in ふたちゃ). Drawn at the app level. */
@Composable
internal fun CompatCommonNgSheetHost(commonNg: CompatCommonNg) {
    val target = commonNg.sheet ?: return
    val catalog = target == CompatCommonNgTarget.CATALOG
    NgManagementSheet(
        onDismiss = { commonNg.sheet = null },
        ngHeaders = commonNg.ngHeaders,
        ngWords = if (catalog) commonNg.catalogNgWords else commonNg.ngWords,
        ngFilteringEnabled = commonNg.applyEnabled,
        onAddHeader = commonNg::addHeader,
        onAddWord = { commonNg.addWord(target, it) },
        onRemoveHeader = commonNg::removeHeader,
        onRemoveWord = { commonNg.removeWord(target, it) },
        onToggleFiltering = commonNg::toggleApply,
        includeHeaderSection = !catalog
    )
    // Shown over the sheet (a later window), not under it (re-audit M15).
    commonNg.failureNotice?.let { notice ->
        com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { commonNg.failureNotice = null },
                text = { androidx.compose.material3.Text(notice) },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { commonNg.failureNotice = null }) { androidx.compose.material3.Text("OK") }
                },
                modifier = androidx.compose.ui.Modifier.testTag("compat-common-ng-failure")
            )
        }
    }
}

/**
 * How long the bottom notice of としあき(仮) stays (re-audit M14): 4 seconds for a short one, longer for a long one (a saved
 * file's path, a reason), up to 15 seconds.
 */
internal fun compatAppNoticeMillis(message: String): Long =
    (4_000L + (message.length - 30).coerceAtLeast(0) * 80L).coerceAtMost(15_000L)
