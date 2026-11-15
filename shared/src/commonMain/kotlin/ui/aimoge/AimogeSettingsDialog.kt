package com.valoser.futacha.shared.ui.aimoge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.compat.AimogeAvailability
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.compat.aimogeBoardOnlyShapeOf
import com.valoser.futacha.shared.network.aimoge.AIMOGE_UNSUPPORTED_MESSAGE
import com.valoser.futacha.shared.network.aimoge.AIMOGE_SAVE_FAILED_MESSAGE
import com.valoser.futacha.shared.network.aimoge.AIMOGE_UNREADABLE_MESSAGE
import com.valoser.futacha.shared.network.aimoge.AimogeAuth
import com.valoser.futacha.shared.network.aimoge.AimogeAuthRegistry
import com.valoser.futacha.shared.network.aimoge.AimogeBeginResult
import com.valoser.futacha.shared.network.aimoge.AimogeReturnResult
import com.valoser.futacha.shared.network.aimoge.AimogeRevokeResult
import com.valoser.futacha.shared.network.aimoge.aimogeExpiryText
import com.valoser.futacha.shared.network.aimoge.newAimogeDeleteKey
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import kotlinx.coroutines.launch

/** The entry row's label in the 4 modes' settings. */
const val AIMOGE_SETTINGS_TITLE = "あいもげ"

/** J7: the explanation agreed to before the first write, そうだね or del (one place; AM-B9/B10 point here). */
const val AIMOGE_DELETE_KEY_CONSENT_TEXT =
    "あいもげでは、書き込みに使った削除キーがあいもげのアカウントに結び付きます（取り消せません）。" +
        "キーを変えると別のアカウントになります。" +
        "このキーは端末を替えると引き継がれません（バックアップにも入りません）。アプリを入れ直した時も消えます。" +
        "前のキーで書いたレスを消すには、キーを控えておいてください。"

const val AIMOGE_DELETE_KEY_MAX = 64

/** The slug of the first registered あいもげ board (sent as `X-Board-Key`), or null. */
fun aimogeApprovalSlugOf(boardUrls: List<String>): String? = boardUrls.firstNotNullOfOrNull { url ->
    aimogeBoardOnlyShapeOf(url)?.removeSuffix("/")?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
}

/** Whether the 4 modes show the 「あいもげ」 settings row. */
fun aimogeSettingsVisible(): Boolean = AimogeAvailability.sitesEnabled && AimogeAuthRegistry.auth != null

/**
 * The 「あいもげ」 settings page (AM-B8): approval state and expiry, approve again, revoke, the あいもげ delete key
 * (shown and copied only on a tap) and the J7 consent. Nothing is sent after approving.
 */
@Composable
fun AimogeSettingsDialog(store: CompatibilityStore, onDismiss: () -> Unit) {
    val auth = AimogeAuthRegistry.auth ?: return
    // The shared store holds the 4 modes' boards (D-B1), so every mode approves with the same board list.
    val boards by store.boards.collectAsState(emptyList())
    val slug = aimogeApprovalSlugOf(boards.flatMap { listOf(it.canonicalUrl, it.originalUrl) })
    AimogeSettingsContent(auth, slug, onDismiss)
}

@Composable
private fun AimogeSettingsContent(auth: AimogeAuth, slug: String?, onDismiss: () -> Unit) {
    val status by auth.status.collectAsState()
    val scope = rememberCoroutineScope()
    val opener = rememberAimogeApprovalOpener()
    val clipboard = LocalClipboardManager.current
    var message by remember { mutableStateOf<String?>(null) }
    var keyShown by remember { mutableStateOf(false) }
    var editingKey by remember { mutableStateOf<String?>(null) }
    var shownKey by remember { mutableStateOf<String?>(null) }
    var saveFailed by remember { mutableStateOf(false) }
    var confirmRevoke by remember { mutableStateOf(false) }
    LaunchedEffect(auth) { auth.refresh() }

    FutachaAppLockAwareWindow {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(AIMOGE_SETTINGS_TITLE) },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("aimoge-settings-close")) { Text("閉じる") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!status.supported) {
                    Text(AIMOGE_UNSUPPORTED_MESSAGE)
                    return@Column
                }
                Text("承認", style = MaterialTheme.typography.titleSmall)
                val expiry = status.expiresAtMillis
                Text(
                    when {
                        status.unreadable -> AIMOGE_UNREADABLE_MESSAGE
                        !status.approved -> "承認していません"
                        status.expiresSoon && expiry != null -> "承認済み（${aimogeExpiryText(expiry)}まで）もうすぐ切れます"
                        expiry != null -> "承認済み（${aimogeExpiryText(expiry)}まで）"
                        else -> "承認済み"
                    },
                    modifier = Modifier.testTag("aimoge-settings-status")
                )
                if (saveFailed) Text(AIMOGE_SAVE_FAILED_MESSAGE, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("aimoge-settings-save-failed"))
                Text("書き込むにはブラウザであいもげの承認が要ります。承認した後に、何かを自動で送ることはありません。", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        enabled = slug != null,
                        modifier = Modifier.testTag("aimoge-settings-approve"),
                        onClick = {
                            val boardSlug = slug ?: return@TextButton
                            scope.launch {
                                when (val begin = auth.begin(boardSlug)) {
                                    is AimogeBeginResult.Failed -> message = begin.message
                                    is AimogeBeginResult.Open -> {
                                        val opened = opener.open(begin.authorizationUrl) { returned ->
                                            scope.launch {
                                                if (returned == null) {
                                                    auth.cancelPending()
                                                } else when (val r = auth.handleReturn(returned)) {
                                                    AimogeReturnResult.Approved -> message = "あいもげの承認が済みました"
                                                    is AimogeReturnResult.Failed -> message = r.message
                                                    AimogeReturnResult.Ignored -> Unit
                                                }
                                            }
                                        }
                                        if (!opened) {
                                            auth.cancelPending()
                                            message = "ブラウザを開けませんでした"
                                        }
                                    }
                                }
                            }
                        }
                    ) { Text(if (status.approved) "承認し直す" else "承認する") }
                    TextButton(
                        enabled = status.approved,
                        modifier = Modifier.testTag("aimoge-settings-revoke"),
                        onClick = { confirmRevoke = true }
                    ) { Text("承認を取り消す") }
                }
                if (slug == null) Text("「板を追加」のURL欄からあいもげの板を足すと承認できます。", style = MaterialTheme.typography.bodySmall)
                message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("aimoge-settings-message")) }

                HorizontalDivider()
                Text("削除キー（あいもげ）", style = MaterialTheme.typography.titleSmall)
                Text("あいもげの板のキーです。ふたばの板のキーとは別です。", style = MaterialTheme.typography.bodySmall)
                val editing = editingKey
                if (editing != null) {
                    OutlinedTextField(
                        value = editing,
                        onValueChange = { editingKey = it.take(AIMOGE_DELETE_KEY_MAX) },
                        singleLine = true,
                        label = { Text("削除キー（64字まで）") },
                        modifier = Modifier.fillMaxWidth().testTag("aimoge-settings-key-field")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { editingKey = newAimogeDeleteKey() }) { Text("新しいキーを作る") }
                        TextButton(
                            modifier = Modifier.testTag("aimoge-settings-key-save"),
                            onClick = {
                                val value = editing.trim()
                                scope.launch {
                                    if (!auth.setDeleteKey(value.ifEmpty { null })) {
                                        saveFailed = true
                                        return@launch
                                    }
                                    saveFailed = false
                                    editingKey = null
                                    keyShown = false
                                    shownKey = null
                                }
                            }
                        ) { Text("保存") }
                        TextButton(onClick = { editingKey = null }) { Text("やめる") }
                    }
                    Text("キーを変えると、次の書き込みの前にもう一度説明への同意が要ります。", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(
                        when {
                            !status.hasDeleteKey -> "まだありません"
                            keyShown -> shownKey.orEmpty()
                            else -> "••••••••"
                        },
                        modifier = Modifier.testTag("aimoge-settings-key")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (status.hasDeleteKey) {
                            TextButton(onClick = {
                                if (keyShown) { keyShown = false; shownKey = null }
                                else scope.launch { shownKey = auth.deleteKey(); keyShown = true }
                            }) { Text(if (keyShown) "隠す" else "表示") }
                            TextButton(onClick = {
                                scope.launch { auth.deleteKey()?.let { clipboard.setText(AnnotatedString(it)) } }
                            }) { Text("コピー") }
                        }
                        TextButton(
                            modifier = Modifier.testTag("aimoge-settings-key-edit"),
                            onClick = { scope.launch { editingKey = auth.deleteKey() ?: newAimogeDeleteKey() } }
                        ) { Text(if (status.hasDeleteKey) "変える" else "作る") }
                    }
                }

                HorizontalDivider()
                Text("削除キーの結び付き", style = MaterialTheme.typography.titleSmall)
                Text(AIMOGE_DELETE_KEY_CONSENT_TEXT, style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = status.deleteKeyConsented,
                        enabled = status.hasDeleteKey,
                        onCheckedChange = { agreed -> scope.launch { saveFailed = !auth.setDeleteKeyConsent(agreed) } },
                        modifier = Modifier.testTag("aimoge-settings-consent")
                    )
                    Text("説明を読み、同意します")
                }
            }
        }
    )
    }

    if (confirmRevoke) {
        FutachaAppLockAwareWindow {
        AlertDialog(
            onDismissRequest = { confirmRevoke = false },
            title = { Text("承認を取り消す") },
            text = { Text("この端末の資格を消し、あいもげにも取り消しを送ります。") },
            confirmButton = {
                TextButton(modifier = Modifier.testTag("aimoge-settings-revoke-confirm"), onClick = {
                    confirmRevoke = false
                    scope.launch {
                        message = when (val r = auth.revoke()) {
                            AimogeRevokeResult.Revoked -> "承認を取り消しました"
                            is AimogeRevokeResult.ServerUnconfirmed -> r.message
                        }
                    }
                }) { Text("取り消す") }
            },
            dismissButton = { TextButton(onClick = { confirmRevoke = false }) { Text("やめる") } }
        )
        }
    }
}
