package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.compat.storeCanonicalBoardUrlOf
import com.valoser.futacha.shared.mix.*
import com.valoser.futacha.shared.model.BoardSummary
import kotlinx.coroutines.launch

/** Edit one MIX: name and member boards (max 10). Shared by every mode; a mode only chooses where to show it. */
@Composable
fun MixEditDialog(
    definition: MixDefinition,
    registeredBoards: List<BoardSummary>,
    store: CompatibilityStore,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val confirmMembers = rememberMixR18Confirmation(store)
    var saving by remember { mutableStateOf(false) }
    var name by remember(definition.id) { mutableStateOf(definition.name) }
    val candidates = remember(definition.members, registeredBoards) {
        mixEditCandidates(definition, registeredBoards)
    }
    var selected by remember(definition.id) {
        mutableStateOf(definition.members.mapNotNull { storeCanonicalBoardUrlOf(it) ?: it }.toSet())
    }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow { AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("まとめカタログを編集") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("名前") })
                Text("板（${selected.size}/$MAX_MIX_MEMBERS）", modifier = Modifier.padding(top = 8.dp))
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    items(candidates, key = { it.first }) { (canonical, board) ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                modifier = Modifier.testTag("mix-member-$canonical"),
                                checked = canonical in selected,
                                onCheckedChange = { on ->
                                    selected = if (on) {
                                        if (selected.size >= MAX_MIX_MEMBERS) { error = "板は${MAX_MIX_MEMBERS}個までです"; selected } else selected + canonical
                                    } else selected - canonical
                                }
                            )
                            Text(board.name)
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving, onClick = {
                scope.launch {
                    saving = true
                    try {
                        // Keep members that are not in the registered list (paused or removed boards) in their place.
                        val kept = definition.members.filter { m -> (storeCanonicalBoardUrlOf(m) ?: m) in selected }
                        val added = candidates.map { it.first }.filter { it in selected && kept.none { m -> (storeCanonicalBoardUrlOf(m) ?: m) == it } }
                        if (!confirmMembers(added)) return@launch
                        when (val r = MixStore(store).update(definition.id) { it.copy(name = name, members = kept + added) }) {
                            MixWriteResult.Ok -> onDismiss()
                            is MixWriteResult.Refused -> error = r.message
                        }
                    } finally { saving = false }
                }
            }) { Text("保存") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { confirmDelete = true }) { Text("削除") }
                TextButton(onClick = onDismiss) { Text("キャンセル") }
            }
        }
    ) }
    if (confirmDelete) {
        com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow { AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("「${definition.name}」を削除しますか？") },
            text = { Text("板やスレッドは消えません。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        when (val r = MixStore(store).delete(definition.id)) {
                            MixWriteResult.Ok -> { confirmDelete = false; onDeleted() }
                            is MixWriteResult.Refused -> { confirmDelete = false; error = r.message }
                        }
                    }
                }) { Text("削除") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("キャンセル") } }
        ) }
    }
}

/** Keep missing members visible and removable, without dropping them just by opening the editor. */
internal fun mixEditCandidates(definition: MixDefinition, registeredBoards: List<BoardSummary>): List<Pair<String, BoardSummary>> {
    val registered = registeredBoards.mapNotNull { board -> storeCanonicalBoardUrlOf(board.url)?.let { it to board } }.distinctBy { it.first }.toMap()
    val members = definition.members.map { storeCanonicalBoardUrlOf(it) ?: it }
    return (members + registered.keys).distinct().map { url ->
        val reason = if (com.valoser.futacha.shared.compat.aimogeBoardShapeOf(url) != null &&
            !com.valoser.futacha.shared.compat.AimogeAvailability.sitesEnabled) "あいもげ無効" else "未登録"
        url to (registered[url] ?: BoardSummary(url, "$url（$reason）", "", url, ""))
    }
}
