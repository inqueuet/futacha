package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
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
import com.valoser.futacha.shared.util.resolveUrlLaunchRequest
import io.ktor.http.encodeURLParameter

/** The app's version, known at the top of the app, for a report opened where no version is passed (L35). */
internal val LocalFutachaAppVersion = androidx.compose.runtime.staticCompositionLocalOf { "" }

/** The colour scheme of the mode on screen, set by each mode's theme; null in としあき(仮), which names its own (L35). */
internal val LocalBugReportThemeName = androidx.compose.runtime.staticCompositionLocalOf<String?> { null }

internal fun futachaBugReportThemeName(palette: com.valoser.futacha.shared.model.ThemePalette, mode: com.valoser.futacha.shared.model.ThemeMode): String =
    "${palette.label}・${mode.label}"

internal fun buildBugReport(version: String, mode: String, device: String, theme: String, steps: String): String =
    "ふたちゃ 不具合報告\n版: ${version.ifBlank { "未確認" }}\nモード: $mode\n端末: $device\n配色: $theme\n\n再現手順・起きたこと:\n$steps"

internal const val BUG_REPORT_EMAIL = "admin@valoser.com"

internal fun bugReportMailto(report: String? = null): String =
    "mailto:$BUG_REPORT_EMAIL?subject=${"ふたちゃ 不具合報告".encodeURLParameter()}" +
        (report?.let { "&body=${it.encodeURLParameter()}" } ?: "")

internal data class BugReportMailPlan(val url: String, val reportToCopy: String? = null)

/** Reuse the launcher's local URL limit; the report itself is never shortened. */
internal fun bugReportMailPlan(report: String): BugReportMailPlan {
    val url = bugReportMailto(report)
    return if (resolveUrlLaunchRequest(url) != null) BugReportMailPlan(url)
    else BugReportMailPlan(bugReportMailto(), report)
}

internal fun shouldConfirmBugReportDiscard(steps: String): Boolean = steps.isNotEmpty()

/** The user reviews/copies the text; no cookies, drafts, keys or attachments are collected. */
@Composable
internal fun BugReportDialog(version: String, theme: String, onDismiss: () -> Unit) {
    // A help screen opened without a version (the drawing screen's help, the viewer's settings) still reports it.
    @Suppress("NAME_SHADOWING")
    val version = version.ifBlank { LocalFutachaAppVersion.current }
    val mode = LocalExperienceProfileUiController.current.activeProfile.displayName
    val clipboard = LocalClipboardManager.current
    val openUrl = rememberUrlLauncher()
    var steps by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }
    var discardConfirm by remember { mutableStateOf(false) }
    var oversizedMailPlan by remember { mutableStateOf<BugReportMailPlan?>(null) }
    val device = remember { compatPostDeviceInfo("").removePrefix("ふたば＠アプリ としあき(仮)").trim() }
    val report = buildBugReport(version, mode, device, theme, steps)
    fun requestDismiss() {
        if (shouldConfirmBugReportDiscard(steps)) discardConfirm = true else onDismiss()
    }
    FutachaAppLockAwareWindow { AlertDialog(
        onDismissRequest = ::requestDismiss,
        title = { Text("不具合を報告") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("版: ${version.ifBlank { "未確認" }}\nモード: $mode\n端末: $device\n配色: $theme")
            OutlinedTextField(steps, { steps = it; copied = false }, label = { Text("再現手順・起きたこと") },
                minLines = 3, maxLines = 6,
                modifier = Modifier.fillMaxWidth().testTag("bug-report-steps"))
            Text("Cookie・削除キー・投稿本文・画像は自動で含めません。内容を確認してから送ってください。")
        } },
        confirmButton = { Column(horizontalAlignment = Alignment.End) {
            TextButton(onClick = { clipboard.setText(AnnotatedString(report)); copied = true }, modifier = Modifier.testTag("bug-report-copy")) {
                Text(if (copied) "コピーしました" else "報告文をコピー")
            }
            TextButton(onClick = {
                val plan = bugReportMailPlan(report)
                if (plan.reportToCopy != null) {
                    clipboard.setText(AnnotatedString(plan.reportToCopy))
                    copied = true
                    oversizedMailPlan = plan
                } else openUrl(plan.url)
            }) { Text("メールで報告") }
        } },
        dismissButton = { TextButton(onClick = ::requestDismiss) { Text("閉じる") } }
    ) }
    oversizedMailPlan?.let { plan ->
        FutachaAppLockAwareWindow { AlertDialog(
            onDismissRequest = { oversizedMailPlan = null },
            title = { Text("報告文をコピーしました") },
            text = { Text("本文が長いため、メールへ直接渡せません。メールを開いて、コピーした報告文を本文に貼り付けてください。\n宛先: $BUG_REPORT_EMAIL") },
            confirmButton = { TextButton(onClick = {
                plan.reportToCopy?.let { clipboard.setText(AnnotatedString(it)) }
                oversizedMailPlan = null
                openUrl(plan.url)
            }) { Text("メールを開く") } },
            dismissButton = { TextButton(onClick = { oversizedMailPlan = null }) { Text("入力に戻る") } }
        ) }
    }
    if (discardConfirm) FutachaAppLockAwareWindow { AlertDialog(
        onDismissRequest = { discardConfirm = false },
        title = { Text("入力を破棄しますか？") },
        text = { Text("再現手順・起きたことの入力は保存されません。") },
        confirmButton = { TextButton(onClick = { discardConfirm = false; onDismiss() }) { Text("破棄して閉じる") } },
        dismissButton = { TextButton(onClick = { discardConfirm = false }) { Text("入力を続ける") } }
    ) }
}
