package com.valoser.futacha.shared.ui.aimoge

import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.compat.AimogeAvailability
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import com.valoser.futacha.shared.network.aimoge.createAimogeHttpClient
import com.valoser.futacha.shared.repo.aimoge.AimogeBoardInfo
import com.valoser.futacha.shared.repo.aimoge.AimogeBoardRepository
import com.valoser.futacha.shared.repo.aimoge.aimogeBoardChoices
import com.valoser.futacha.shared.repo.aimoge.aimogeInitialChecks
import com.valoser.futacha.shared.repo.aimoge.aimogeR18Checked
import kotlinx.coroutines.CancellationException

/** The title of the board picker opened from a top / overall / mix URL. */
const val AIMOGE_ADD_BOARDS_LABEL = "あいもげの板を追加"

private const val AIMOGE_MULTI_R18_CONFIRM_TEXT =
    "R18のスレを立てられる板が含まれます。成人向けの内容が表示されることがあります。追加しますか？"

/**
 * The list of あいもげ boards from `/boards`: the boards shown at first, minus [registeredUrls], with the site's first
 * checks; a board allowing R18 threads is marked and asks once before adding. Nothing is added until the user taps
 * 追加. The list is read on its own cookie-less client, closed off the UI thread after the read.
 */
@Composable
fun AimogeAddBoardsDialog(
    registeredUrls: Set<String>,
    onAdd: (List<AimogeBoardInfo>) -> Unit,
    onDismiss: () -> Unit,
    loadBoards: (suspend () -> List<AimogeBoardInfo>)? = null,
    store: com.valoser.futacha.shared.compat.CompatibilityStore? = null
) {
    if (!AimogeAvailability.sitesEnabled) return
    var choices by remember { mutableStateOf<List<AimogeBoardInfo>?>(null) }
    var checked by remember { mutableStateOf<Set<String>>(emptySet()) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var confirmR18 by remember { mutableStateOf<List<AimogeBoardInfo>?>(null) }
    LaunchedEffect(attempt, registeredUrls) {
        error = null
        choices = null
        confirmR18 = null
        try {
            val all = loadBoards?.invoke() ?: kotlinx.coroutines.withContext(com.valoser.futacha.shared.util.AppDispatchers.io) {
                val repository = AimogeBoardRepository.owningClient({ createAimogeHttpClient(appVersion = "") })
                try { repository.boards() } finally { repository.close() }
            }
            AimogeBoardIcons.learnAndSave(store, all)
            val offered = aimogeBoardChoices(all, registeredUrls)
            choices = offered.distinctBy { it.url }
            checked = aimogeInitialChecks(offered)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            error = "板の一覧を読み込めませんでした"
        }
    }
    val current = choices
    fun add() {
        val list = current.orEmpty().filter { it.url in checked }
        if (list.isNotEmpty()) onAdd(list)
    }
    confirmR18?.let { r18 ->
        FutachaAppLockAwareWindow { AlertDialog(
            onDismissRequest = { confirmR18 = null },
            title = { Text("R18の板") },
            text = { Text(AIMOGE_MULTI_R18_CONFIRM_TEXT + "\n\n" + r18.joinToString("・") { it.name }) },
            confirmButton = {
                TextButton(onClick = { confirmR18 = null; add() }, modifier = Modifier.testTag("aimoge-r18-confirm")) { Text("追加する") }
            },
            dismissButton = { TextButton(onClick = { confirmR18 = null }, modifier = Modifier.testTag("aimoge-r18-cancel")) { Text("キャンセル") } }
        ) }
    }
    FutachaAppLockAwareWindow { AlertDialog(
        onDismissRequest = { confirmR18 = null; onDismiss() },
        title = { Text(AIMOGE_ADD_BOARDS_LABEL) },
        text = {
            Box(Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 420.dp).testTag("aimoge-add-boards")) {
                when {
                    error != null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(error.orEmpty(), color = MaterialTheme.colorScheme.onSurface)
                        TextButton(onClick = { attempt++ }) { Text("再読み込み") }
                    }
                    current == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    current.isEmpty() -> Text("追加できる板はありません", color = MaterialTheme.colorScheme.onSurface)
                    else -> LazyColumn {
                        items(current, key = { it.url }) { board ->
                            val on = board.url in checked
                            Row(
                                Modifier.fillMaxWidth()
                                    .toggleable(value = on, role = Role.Checkbox) { value -> checked = if (value) checked + board.url else checked - board.url }
                                    .padding(vertical = 4.dp)
                                    .testTag("aimoge-board-${board.slug}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = on, onCheckedChange = null)
                                Column(Modifier.padding(start = 8.dp)) {
                                    Text(
                                        if (board.r18) "${board.name}  R18" else board.name,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        board.url.removePrefix("https://"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = current.orEmpty().any { it.url in checked },
                onClick = {
                    val r18 = aimogeR18Checked(current.orEmpty(), checked)
                    if (r18.isEmpty()) add() else confirmR18 = r18
                },
                modifier = Modifier.testTag("aimoge-add-confirm")
            ) { Text("追加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } }
    ) }
}
