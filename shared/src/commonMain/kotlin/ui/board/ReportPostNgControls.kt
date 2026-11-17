package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.compat.*
import kotlinx.coroutines.launch

/** The del confirmation's check in ふたばー風 and ふたったー風; the operation guide names it (L26). */
internal const val REPORT_POST_NG_CHECK_LABEL = "このスレッドのこのレスをNGにも登録"

@Composable
internal fun ReportPostNgCheckRow(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 8.dp)
        .testTag("report-post-ng-option")) {
        Checkbox(checked, onCheckedChange = onChange)
        Text(REPORT_POST_NG_CHECK_LABEL, Modifier.weight(1f).padding(top = 12.dp))
    }
}

@Composable
internal fun ReportPostConfirmation(postNo: String, offerNg: Boolean, onDismiss: () -> Unit, onConfirm: (Boolean) -> Unit) {
    var alsoNg by remember(postNo) { mutableStateOf(false) }
    com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow {
        AlertDialog(onDismissRequest = onDismiss, title = { Text("delを送りますか？") },
            text = { Column {
                Text("No.$postNo\n掲示板の管理側へ削除依頼（del）を送ります。")
                if (offerNg) ReportPostNgCheckRow(alsoNg) { alsoNg = it }
            } },
            confirmButton = { TextButton(onClick = { onConfirm(offerNg && alsoNg) }, Modifier.testTag("report-post-confirm")) { Text("送る") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } })
    }
}

/** These post-only rules survive closing the tab, and can be removed in the mode that created them. */
@Composable
internal fun ReportedPostNgSettings(store: CompatibilityStore?) {
    if (store == null) return
    val rules by store.ngRules.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    val ownRules = rules.filter { it.kind == CompatNgKind.THREAD_POST_NO && compatPostNgScopeBoardKey(it.scopeKey) != null }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text("delと一緒に登録したレスNG：${ownRules.size}件")
        ownRules.forEach { rule ->
            Row(Modifier.fillMaxWidth()) {
                Text("No.${rule.normalizedValue}\n${rule.scopeKey}", Modifier.weight(1f))
                TextButton(onClick = { scope.launch {
                    try { store.deleteNgRule(rule.id) }
                    catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (_: Exception) { error = "NGを解除できませんでした" }
                } }) { Text("解除") }
            }
        }
        error?.let { Text(it) }
    }
}
