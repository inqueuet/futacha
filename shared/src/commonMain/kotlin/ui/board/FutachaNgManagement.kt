package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.ui.compat.CompatNgRuleManagementDialog
import com.valoser.futacha.shared.ui.compat.compatPreferenceStorageKey
import com.valoser.futacha.shared.ui.compat.CompatImageNgRegistrationDialog
import com.valoser.futacha.shared.ui.compat.fetchCompatImagePhash
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.time.Clock

@Composable
internal fun FutachaImageNgRegistration(features: FutachaSharedFeatures, boardKey: String,
    source: CompatImageNgSource, imageUrl: String, initialMemo: String, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    if (!busy) CompatImageNgRegistrationDialog(imageUrl, initialMemo, onDismiss) { memo, localOnly ->
        busy = true
        scope.launch {
            try {
                val kind = if (source == CompatImageNgSource.CATALOG) CompatNgKind.CATALOG_IMAGE_PHASH else CompatNgKind.THREAD_IMAGE_PHASH
                val targetScope = if (localOnly) boardKey else "*"
                val hash = fetchCompatImagePhash(requireNotNull(features.httpClient) { "通信機能を利用できません" }, imageUrl).getOrThrow()
                check(features.store.upsertNgRule(CompatNgRule(compatNgRuleId(kind, targetScope, hash), kind,
                    targetScope, hash, Clock.System.now().toEpochMilliseconds(), imageUrl, memo.take(300)))) {
                    "適用先が削除されたため登録できませんでした"
                }
                onDismiss()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "画像を登録できませんでした" }
            finally { busy = false }
        }
    }
    if (busy) AlertDialog(onDismissRequest = {}, text = { Text("画像の類似判定を準備しています…") }, confirmButton = {})
    error?.let { text -> AlertDialog(onDismissRequest = { error = null }, text = { Text(text) },
        confirmButton = { TextButton(onClick = { error = null }) { Text("閉じる") } }) }
}

@Composable
internal fun FutachaNgManagementDialog(
    features: FutachaSharedFeatures,
    boardKey: String,
    tabKey: String?,
    boardName: String,
    onDismiss: () -> Unit
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
    if (selectedKind == null) {
        AlertDialog(onDismissRequest = onDismiss, title = { Text("NG・監視の詳細管理") }, text = {
            Column { choices.forEach { (label, value) -> TextButton(onClick = { kind = value }) { Text(label) } } }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } })
    } else {
        val image = selectedKind in setOf(CompatNgKind.CATALOG_IMAGE, CompatNgKind.THREAD_IMAGE)
        val kinds = when {
            image -> compatImageNgKinds(if (tabKey == null) CompatImageNgSource.CATALOG else CompatImageNgSource.THREAD)
            tabKey == null -> compatCatalogManagementKinds(selectedKind)
            else -> compatThreadReferenceKinds(selectedKind)
        }
        fun ruleScope(global: Boolean) = if (global) "*" else if (image || tabKey == null) boardKey else tabKey
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
                val targetScope = ruleScope(global)
                check(features.store.upsertNgRule(CompatNgRule(compatNgRuleId(selectedKind, targetScope, normalized),
                    selectedKind, targetScope, normalized, Clock.System.now().toEpochMilliseconds()))) { "対象の板またはスレッドがありません" }
            } },
            onEdit = { rule, value, global, memo -> perform {
                // Preserve the stable identity while changing value/scope, as the shared store expects.
                check(features.store.upsertNgRule(rule.copy(scopeKey = ruleScope(global), normalizedValue = value,
                    memo = memo))) { "対象の板またはスレッドがありません" }
            } },
            onDelete = { rule -> perform { features.store.deleteNgRule(rule.id) } },
            onDeleteAll = { selected -> perform { features.store.deleteNgRules(selected.map { it.id }) } },
            onDismiss = { kind = null }
        )
    }
    message?.let { text -> AlertDialog(onDismissRequest = { message = null }, text = { Text(text) },
        confirmButton = { TextButton(onClick = { message = null }) { Text("閉じる") } }) }
}
