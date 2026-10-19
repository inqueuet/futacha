package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import com.valoser.futacha.shared.compat.LocalExperienceProfileUiController
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import com.valoser.futacha.shared.ui.compat.compatPostDeviceInfo
import com.valoser.futacha.shared.util.rememberUrlLauncher
import io.ktor.http.encodeURLParameter

internal fun buildBugReport(version: String, mode: String, device: String, theme: String, steps: String): String =
    "ふたちゃ 不具合報告\n版: ${version.ifBlank { "未確認" }}\nモード: $mode\n端末: $device\n配色: $theme\n\n再現手順・起きたこと:\n$steps"

/** The user reviews/copies the text; no cookies, drafts, keys or attachments are collected. */
@Composable
internal fun BugReportDialog(version: String, theme: String, onDismiss: () -> Unit) {
    val mode = LocalExperienceProfileUiController.current.activeProfile.displayName
    val clipboard = LocalClipboardManager.current
    val openUrl = rememberUrlLauncher()
    var steps by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }
    val device = remember { compatPostDeviceInfo("").removePrefix("ふたば＠アプリ としあき(仮)").trim() }
    val report = buildBugReport(version, mode, device, theme, steps)
    FutachaAppLockAwareWindow { AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("不具合を報告") },
        text = { Column {
            Text("版: ${version.ifBlank { "未確認" }}\nモード: $mode\n端末: $device\n配色: $theme")
            OutlinedTextField(steps, { steps = it.take(4000) }, label = { Text("再現手順・起きたこと") },
                modifier = Modifier.fillMaxWidth().testTag("bug-report-steps"))
            Text("Cookie・削除キー・投稿本文・画像は自動で含めません。内容を確認してから送ってください。")
            TextButton(onClick = { clipboard.setText(AnnotatedString(report)); copied = true }, modifier = Modifier.testTag("bug-report-copy")) {
                Text(if (copied) "コピーしました" else "報告文をコピー")
            }
        } },
        confirmButton = { TextButton(onClick = {
            openUrl("mailto:admin@valoser.com?subject=${"ふたちゃ 不具合報告".encodeURLParameter()}&body=${report.encodeURLParameter()}")
        }) { Text("メールで報告") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("閉じる") } }
    ) }
}
