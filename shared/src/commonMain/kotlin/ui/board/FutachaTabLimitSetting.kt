package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.compat.COMPAT_SHARED_TAB_LIMIT_KEY
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import com.valoser.futacha.shared.compat.COMPAT_TAB_LIMIT_NOTE
import com.valoser.futacha.shared.compat.CompatWorkspaceRecord
import com.valoser.futacha.shared.compat.compatSharedTabLimit
import com.valoser.futacha.shared.compat.compatTabLimitQuestion
import com.valoser.futacha.shared.compat.compatTabLimitRefusal
import com.valoser.futacha.shared.compat.compatTabLimitTrimCount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Plan B9: ふたちゃ's setting of the 4 modes' one tab limit. A lower value says how many tabs the next trim closes and
 * asks first; a value below ★ + 10 is refused (the ★ are never trimmed).
 */
@Composable
internal fun FutachaTabLimitSetting(features: FutachaSharedFeatures) {
    val store = features.store
    val tabs by store.tabs.collectAsState(emptyList())
    val boards by store.boards.collectAsState(emptyList())
    val workspace by store.workspace.collectAsState(CompatWorkspaceRecord())
    val views by store.tabViews.collectAsState(emptyList())
    val stored = features.rawValue(COMPAT_SHARED_TAB_LIMIT_KEY)
    val current = compatSharedTabLimit(stored?.let { mapOf(COMPAT_SHARED_TAB_LIMIT_KEY to it) }.orEmpty())
    var text by remember { mutableStateOf(current.toString()) }
    LaunchedEffect(current) { text = current.toString() }
    var error by remember { mutableStateOf<String?>(null) }
    var question by remember { mutableStateOf<Pair<Int, String>?>(null) }
    val scope = rememberCoroutineScope()
    fun save(limit: Int) {
        scope.launch {
            try {
                store.savePreference(COMPAT_SHARED_TAB_LIMIT_KEY, limit.toString())
                error = null
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = "タブの上限を保存できませんでした" }
        }
    }
    Text("タブの上限（4モード共通）: ${current}件", style = MaterialTheme.typography.bodyLarge)
    Text(COMPAT_TAB_LIMIT_NOTE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { next -> text = next.filter(Char::isDigit).take(4); error = null },
            label = { Text("件数") },
            singleLine = true,
            isError = error != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f).testTag("futacha-tab-limit-field")
        )
        TextButton(onClick = {
            val limit = text.toIntOrNull()
            val refusal = if (limit == null) "数を入れてください" else compatTabLimitRefusal(limit, tabs)
            if (refusal != null || limit == null) { error = refusal; return@TextButton }
            if (limit == current) return@TextButton
            val ask = if (limit < current) {
                compatTabLimitQuestion(limit, compatTabLimitTrimCount(tabs, limit, workspace.activeTabKey, features.preferences, boards, views))
            } else null
            if (ask != null) question = limit to ask else save(limit)
        }, modifier = Modifier.testTag("futacha-tab-limit-save")) { Text("保存") }
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.testTag("futacha-tab-limit-error")) }
    question?.let { (limit, ask) ->
        FutachaAppLockAwareWindow { AlertDialog(
            onDismissRequest = { question = null },
            title = { Text("タブの上限を下げる") },
            text = { Text(ask) },
            confirmButton = { TextButton(onClick = { question = null; save(limit) }, modifier = Modifier.testTag("futacha-tab-limit-confirm")) { Text("下げる") } },
            dismissButton = { TextButton(onClick = { question = null; text = current.toString() }) { Text("キャンセル") } }
        ) }
    }
}

