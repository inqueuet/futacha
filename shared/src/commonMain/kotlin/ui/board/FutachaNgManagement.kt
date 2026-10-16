package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import com.valoser.futacha.shared.ui.compat.CompatNgRuleManagementDialog
import com.valoser.futacha.shared.ui.compat.compatPreferenceStorageKey
import com.valoser.futacha.shared.ui.compat.CompatImageNgRegistrationDialog
import com.valoser.futacha.shared.ui.compat.fetchCompatImagePhash
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.time.Clock

@Composable
internal fun FutachaImageNgRegistration(features: FutachaSharedFeatures, boardKey: String,
    source: CompatImageNgSource, imageUrl: String, initialMemo: String, onDismiss: () -> Unit,
    /**
     * Run before a "この板のみ" rule is written, for a host whose boards the shared store may not know yet (ふたばー風
     * shares its boards here). Its failure ends the registration with its message. Null (the default) as before.
     */
    prepareBoardScope: (suspend () -> Unit)? = null) {
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    if (!busy) FutachaAppLockAwareWindow { CompatImageNgRegistrationDialog(imageUrl, initialMemo, onDismiss) { memo, localOnly ->
        busy = true
        scope.launch {
            try {
                registerFutachaImageNgRule(features.store, source, boardKey, imageUrl, memo, localOnly,
                    fetchHash = {
                        fetchCompatImagePhash(requireNotNull(features.httpClient) { "通信機能を利用できません" }, imageUrl).getOrThrow()
                    },
                    prepareBoardScope = prepareBoardScope)
                onDismiss()
            } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
                // A time-out is a kind of cancellation, but this registration was not cancelled: it is said (re-audit L34:
                // the dialog came back without a word).
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                error = "画像の取得が時間切れになりました。通信を確認してもう一度お試しください"
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "画像を登録できませんでした" }
            finally { busy = false }
        }
    } }
    if (busy) FutachaAppLockAwareWindow {
        AlertDialog(onDismissRequest = {}, text = { Text("画像の類似判定を準備しています…") }, confirmButton = {})
    }
    error?.let { text -> FutachaAppLockAwareWindow { AlertDialog(onDismissRequest = { error = null }, text = { Text(text) },
        confirmButton = { TextButton(onClick = { error = null }) { Text("閉じる") } }) } }
}

/**
 * Writes the image NG rule of [imageUrl]: for every board ("*") or, with [localOnly], for [boardKey]. Throws with the
 * message shown to the user when it cannot be written.
 */
internal suspend fun registerFutachaImageNgRule(
    store: CompatibilityStore,
    source: CompatImageNgSource,
    boardKey: String,
    imageUrl: String,
    memo: String,
    localOnly: Boolean,
    fetchHash: suspend () -> String,
    prepareBoardScope: (suspend () -> Unit)? = null
) {
    val kind = if (source == CompatImageNgSource.CATALOG) CompatNgKind.CATALOG_IMAGE_PHASH else CompatNgKind.THREAD_IMAGE_PHASH
    val targetScope = if (localOnly) boardKey else "*"
    // The board must be in the shared store before a board-only rule is written; waited for here, before the picture
    // is fetched, so a failure is reported as such and nothing is fetched or written.
    if (localOnly) prepareBoardScope?.invoke()
    val hash = fetchHash()
    check(store.upsertNgRule(CompatNgRule(compatNgRuleId(kind, targetScope, hash), kind,
        targetScope, hash, Clock.System.now().toEpochMilliseconds(), imageUrl, memo.take(300)))) {
        "適用先が削除されたため登録できませんでした"
    }
}

/**
 * The scope a new rule of the detailed NG dialog is written under: every board / thread ("*"), else the board (catalog and
 * image rules) or the thread. With [textGlobalOnly] a thread's text rule is always for every thread.
 */
internal fun futachaNgRuleAddScope(global: Boolean, image: Boolean, boardKey: String, tabKey: String?, textGlobalOnly: Boolean): String =
    when {
        global || textGlobalOnly -> "*"
        image || tabKey == null -> boardKey
        else -> tabKey
    }

/** The scope an edited rule is written under; with [textGlobalOnly] a rule that is not made global keeps its own scope. */
internal fun futachaNgRuleEditScope(
    current: String, global: Boolean, image: Boolean, boardKey: String, tabKey: String?, textGlobalOnly: Boolean
): String = if (textGlobalOnly && !global) current else futachaNgRuleAddScope(global, image, boardKey, tabKey, false)

@Composable
internal fun FutachaNgManagementDialog(
    features: FutachaSharedFeatures,
    boardKey: String,
    tabKey: String?,
    boardName: String,
    onDismiss: () -> Unit,
    /**
     * True for a host that keeps no tab of its own (ふたばー風): the thread's text rules are made for every thread only
     * ("このスレッドのみ" is not offered, an edit keeps the rule's scope), and image rules keep "この板のみ". [tabKey] is
     * then only used to list the rules other modes made for this thread. False (the default) is the usual dialog.
     */
    threadTextRulesGlobalOnly: Boolean = false
) {
    val rules by features.store.ngRules.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf<CompatNgKind?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val choices = if (tabKey == null) listOf("NGスレッド" to CompatNgKind.CATALOG_REFUSE, "NGワード" to CompatNgKind.CATALOG_IGNORE,
        "NG画像" to CompatNgKind.CATALOG_IMAGE, "監視ワード" to CompatNgKind.CATALOG_EXTRACT)
    else listOf("NGヘッダー" to CompatNgKind.THREAD_REFUSE, "NGワード" to CompatNgKind.THREAD_IGNORE, "NG画像" to CompatNgKind.THREAD_IMAGE)
    fun perform(block: suspend () -> Unit) { scope.launch {
        try { block() } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { message = failure.message ?: "NG設定を保存できませんでした" }
    } }
    val selectedKind = kind
    if (selectedKind == null) FutachaAppLockAwareWindow {
        AlertDialog(onDismissRequest = onDismiss, title = { Text("NG・監視の詳細管理") }, text = {
            Column { choices.forEach { (label, value) -> TextButton(onClick = { kind = value }) { Text(label) } } }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } })
    } else FutachaAppLockAwareWindow {
        val image = selectedKind in setOf(CompatNgKind.CATALOG_IMAGE, CompatNgKind.THREAD_IMAGE)
        val kinds = when {
            image -> compatImageNgKinds(if (tabKey == null) CompatImageNgSource.CATALOG else CompatImageNgSource.THREAD)
            tabKey == null -> compatCatalogManagementKinds(selectedKind)
            else -> compatThreadReferenceKinds(selectedKind)
        }
        val textGlobalOnly = threadTextRulesGlobalOnly && tabKey != null && !image
        val threshold = features.value("thread", "threadImageNgPhashThreshold", "画像NG類似度閾値")?.toIntOrNull() ?: CompatImagePhash.DEFAULT_THRESHOLD
        CompatNgRuleManagementDialog(
            title = choices.first { it.second == selectedKind }.first,
            rules = rules.filter { it.kind in kinds && (it.scopeKey == "*" || it.scopeKey == boardKey || it.scopeKey == tabKey) },
            referenceKind = if (image) null else selectedKind,
            imageReferenceBoardName = if (image) boardName else null,
            phashThreshold = if (image) threshold else null,
            onPhashThresholdChange = if (image) { value -> perform {
                features.store.savePreference(compatPreferenceStorageKey("thread", "threadImageNgPhashThreshold"), value.toString())
            } } else null,
            addScopeLabel = if (tabKey == null) "全ての板" else "全てのスレッド",
            onAdd = if (image || selectedKind == CompatNgKind.CATALOG_REFUSE) null else { value, global -> perform {
                val normalized = value.trim()
                require(normalized.isNotEmpty()) { "入力してください" }
                val targetScope = futachaNgRuleAddScope(global, image, boardKey, tabKey, textGlobalOnly)
                check(features.store.upsertNgRule(CompatNgRule(compatNgRuleId(selectedKind, targetScope, normalized),
                    selectedKind, targetScope, normalized, Clock.System.now().toEpochMilliseconds()))) { "対象の板またはスレッドがありません" }
            } },
            onEdit = { rule, value, global, memo -> perform {
                // Preserve the stable identity while changing value/scope, as the shared store expects.
                val editedScope = futachaNgRuleEditScope(rule.scopeKey, global, image, boardKey, tabKey, textGlobalOnly)
                check(features.store.upsertNgRule(rule.copy(scopeKey = editedScope, normalizedValue = value,
                    memo = memo))) { "対象の板またはスレッドがありません" }
            } },
            onDelete = { rule -> perform { features.store.deleteNgRule(rule.id) } },
            onDeleteAll = { selected -> perform { features.store.deleteNgRules(selected.map { it.id }) } },
            offerLocalScope = !textGlobalOnly,
            onDismiss = { kind = null }
        )
    }
    message?.let { text -> FutachaAppLockAwareWindow { AlertDialog(onDismissRequest = { message = null }, text = { Text(text) },
        confirmButton = { TextButton(onClick = { message = null }) { Text("閉じる") } }) } }
}
