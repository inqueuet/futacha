package com.valoser.futacha.shared.ui.aimoge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.Alignment
import com.valoser.futacha.shared.network.aimoge.AimogeDelChoice
import com.valoser.futacha.shared.network.aimoge.AimogeReportReason
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.compat.AimogeAvailability
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.network.aimoge.AimogeNotices
import com.valoser.futacha.shared.network.aimoge.AimogePromptRequest
import com.valoser.futacha.shared.network.aimoge.AimogePrompts
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow

const val AIMOGE_CONSENT_TITLE = "あいもげの削除キー"
const val AIMOGE_CONSENT_TEXT =
    "あいもげのそうだね・del・アンケート・通報・書き込みには、あいもげの削除キーを一緒に送ります。" +
        "キーはこの端末で作り、あいもげのアカウントに結びつきます（ふたばの削除キーとは別です）。送ってよいですか？" +
        "後から設定の「あいもげ」で取り消せます。"

/**
 * Shows, in every mode, what an あいもげ write asks at the moment of the tap ([AimogePrompts]): the delete-key consent,
 * the approval (opens the 「あいもげ」 settings) and confirmations; and the short message after a poll answer.
 */
@Composable
fun AimogeWritePromptHost(store: CompatibilityStore) {
    if (!AimogeAvailability.sitesEnabled) return
    androidx.compose.runtime.LaunchedEffect(store) { AimogeBoardIcons.load(store) }
    DisposableEffect(Unit) {
        AimogePrompts.attachHost()
        onDispose { AimogePrompts.detachHost() }
    }
    // A question left open under the app lock is answered "no", so other writes are not held waiting (audit P2-L3).
    val unlocked = com.valoser.futacha.shared.ui.LocalFutachaAppUnlocked.current
    androidx.compose.runtime.LaunchedEffect(unlocked) { if (!unlocked) AimogePrompts.answerOpenQuestionsNo() }
    val current by AimogePrompts.current.collectAsState()
    val notice by AimogeNotices.message.collectAsState()
    var settingsOpen by remember { mutableStateOf(false) }

    current?.let { (request, answer) ->
        when (request) {
                AimogePromptRequest.DeleteKeyConsent -> FutachaAppLockAwareWindow { AlertDialog(
                    onDismissRequest = { answer.complete(false) },
                    title = { Text(AIMOGE_CONSENT_TITLE) },
                    text = { Text(AIMOGE_CONSENT_TEXT) },
                    confirmButton = { TextButton(onClick = { answer.complete(true) }, modifier = Modifier.testTag("aimoge-consent-yes")) { Text("送る") } },
                    dismissButton = { TextButton(onClick = { answer.complete(false) }, modifier = Modifier.testTag("aimoge-consent-no")) { Text("送らない") } }
                ) }
                AimogePromptRequest.Approval -> FutachaAppLockAwareWindow { AlertDialog(
                    onDismissRequest = { answer.complete(false) },
                    title = { Text("あいもげの承認") },
                    text = { Text("あいもげに書き込むには、ブラウザで承認が要ります。承認した後に、何かを自動で送ることはありません。下書きは残っています。") },
                    confirmButton = {
                        TextButton(onClick = { answer.complete(true); settingsOpen = true }, modifier = Modifier.testTag("aimoge-approval-open")) { Text("承認へ") }
                    },
                    dismissButton = { TextButton(onClick = { answer.complete(false) }) { Text("やめる") } }
                ) }
                is AimogePromptRequest.Confirm -> FutachaAppLockAwareWindow { AlertDialog(
                    onDismissRequest = { answer.complete(false) },
                    title = { Text("確かめ") },
                    text = { Text(request.message) },
                    confirmButton = { TextButton(onClick = { answer.complete(true) }, modifier = Modifier.testTag("aimoge-confirm-yes")) { Text("送る") } },
                    dismissButton = { TextButton(onClick = { answer.complete(false) }) { Text("やめる") } }
                ) }
        }
    }
    val delChoice by AimogePrompts.delChoice.collectAsState()
    delChoice?.let { answer -> AimogeDelOrReportDialog(answer) }
    val choice by AimogePrompts.choice.collectAsState()
    choice?.let { (request, answer) -> AimogeChoiceDialog(request, answer) }
    notice?.takeIf { current == null && delChoice == null && choice == null }?.let { text ->
        FutachaAppLockAwareWindow {
            AlertDialog(
                onDismissRequest = AimogeNotices::clear,
                text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(text, modifier = Modifier.testTag("aimoge-notice")) } },
                confirmButton = { TextButton(onClick = AimogeNotices::clear) { Text("OK") } }
            )
        }
    }
    if (settingsOpen) AimogeSettingsDialog(store) { settingsOpen = false }
}

@Composable
private fun AimogeDelOrReportDialog(answer: kotlinx.coroutines.CompletableDeferred<AimogeDelChoice?>) {
    var reporting by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf(AimogeReportReason.SPAM) }
    var detail by remember { mutableStateOf("") }
    FutachaAppLockAwareWindow {
        AlertDialog(
            onDismissRequest = { answer.complete(null) },
            title = { Text(if (reporting) "通報の理由" else "del・通報") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!reporting) {
                        Text("delはふたばと同じ印です。あいもげの運営へ知らせるときは「通報」を選んでください。")
                    } else {
                        AimogeReportReason.entries.forEach { r ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("aimoge-report-${r.wire}").selectable(selected = reason == r, onClick = { reason = r })) {
                                RadioButton(selected = reason == r, onClick = { reason = r })
                                Text(r.label)
                            }
                        }
                        OutlinedTextField(
                            value = detail,
                            onValueChange = { detail = it.take(com.valoser.futacha.shared.network.aimoge.AIMOGE_REPORT_DETAIL_MAX) },
                            label = { Text("くわしく（任意）") },
                            modifier = Modifier.testTag("aimoge-report-detail")
                        )
                    }
                }
            },
            confirmButton = {
                if (!reporting) TextButton(onClick = { answer.complete(AimogeDelChoice.Del) }, modifier = Modifier.testTag("aimoge-del")) { Text("del") }
                else TextButton(onClick = { answer.complete(AimogeDelChoice.Report(reason, detail)) }, modifier = Modifier.testTag("aimoge-report-send")) { Text("通報する") }
            },
            dismissButton = {
                Row {
                    if (!reporting) TextButton(onClick = { reporting = true }, modifier = Modifier.testTag("aimoge-report")) { Text("通報") }
                    TextButton(onClick = { answer.complete(null) }) { Text("やめる") }
                }
            }
        )
    }
}

@Composable
private fun AimogeChoiceDialog(request: com.valoser.futacha.shared.network.aimoge.AimogeChoiceRequest, answer: kotlinx.coroutines.CompletableDeferred<Int?>) {
    var selected by remember(request) { mutableStateOf(request.initial) }
    FutachaAppLockAwareWindow { AlertDialog(
        onDismissRequest = { answer.complete(null) },
        title = { Text(request.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (request.text.isNotEmpty()) Text(request.text)
                request.options.forEachIndexed { i, label ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("${request.tag}-$i").selectable(selected = selected == i, onClick = { selected = i })) {
                        RadioButton(selected = selected == i, onClick = { selected = i })
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { answer.complete(selected) }, modifier = Modifier.testTag("${request.tag}-ok")) { Text(request.confirmLabel) } },
        dismissButton = { TextButton(onClick = { answer.complete(null) }, modifier = Modifier.testTag("${request.tag}-cancel")) { Text("やめる") } }
    ) }
}
