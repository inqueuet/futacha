package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.analytics.AnalyticsTracker
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.ui.LocalIosReviewCompliance

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun ThreadPostActionSheet(
    post: Post,
    onDismiss: () -> Unit,
    onQuote: () -> Unit,
    onNgRegister: () -> Unit,
    onSaidane: () -> Unit,
    isSaidaneEnabled: Boolean = true,
    onDelRequest: () -> Unit,
    onDelete: () -> Unit
) {
    val reviewComplianceEnabled = LocalIosReviewCompliance.current.isEnabled
    var showReportConfirmation by remember(post.id) { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = {
            AnalyticsTracker.uiControl("post_action_sheet_dismiss", "投稿メニューを閉じる")
            onDismiss()
        },
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "No.${post.id} の操作",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            ListItem(
                leadingContent = {
                    Icon(Icons.Outlined.FormatQuote, contentDescription = null)
                },
                headlineContent = { Text("引用") },
                supportingContent = { Text("レス内容を返信欄にコピー") },
                modifier = Modifier.clickable {
                    AnalyticsTracker.uiControl("post_action_quote", "投稿を引用")
                    onQuote()
                }
            )
            ListItem(
                leadingContent = {
                    Icon(Icons.Rounded.Block, contentDescription = null)
                },
                headlineContent = {
                    Text(if (reviewComplianceEnabled) "この利用者をブロック" else "NG登録")
                },
                supportingContent = {
                    Text(
                        if (reviewComplianceEnabled) "ID・IP・名前を端末内で非表示"
                        else "IDやワードをNG管理に追加"
                    )
                },
                modifier = Modifier.clickable {
                    AnalyticsTracker.uiControl("post_action_ng_register", "投稿をNG登録")
                    onNgRegister()
                }
            )
            ListItem(
                leadingContent = {
                    Icon(Icons.Outlined.ThumbUp, contentDescription = null)
                },
                headlineContent = { Text("そうだね") },
                supportingContent = { Text("レスにそうだねを送信") },
                modifier = Modifier
                    .alpha(if (isSaidaneEnabled) 1f else 0.5f)
                    .clickable(
                        enabled = isSaidaneEnabled,
                        onClick = {
                            AnalyticsTracker.uiControl("post_action_saidane", "そうだねを送信")
                            onSaidane()
                        }
                    )
            )
            ListItem(
                leadingContent = {
                    Icon(Icons.Outlined.Flag, contentDescription = null)
                },
                headlineContent = {
                    Text(if (reviewComplianceEnabled) "不適切な投稿を通報" else "DEL 依頼")
                },
                supportingContent = { Text("掲示板管理者へ削除依頼を送信") },
                modifier = Modifier.clickable {
                    AnalyticsTracker.uiControl("post_action_del_request", "DEL依頼を送信")
                    if (reviewComplianceEnabled) {
                        showReportConfirmation = true
                    } else {
                        onDelRequest()
                    }
                }
            )
            ListItem(
                leadingContent = {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                },
                headlineContent = { Text("削除 (本人)") },
                supportingContent = { Text("削除キーでレスまたは画像を削除") },
                modifier = Modifier.clickable {
                    AnalyticsTracker.uiControl("post_action_delete_by_user", "本人削除を開く")
                    onDelete()
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
    if (showReportConfirmation) {
        AlertDialog(
            onDismissRequest = { showReportConfirmation = false },
            title = { Text("不適切な投稿を通報") },
            text = {
                Text("No.${post.id} を不適切な投稿として、ふたば☆ちゃんねるの掲示板管理者へ通報します。")
            },
            confirmButton = {
                TextButton(onClick = {
                    showReportConfirmation = false
                    onDelRequest()
                }) {
                    Text("通報する")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportConfirmation = false }) {
                    Text("キャンセル")
                }
            }
        )
    }
}

@Composable
internal fun DeleteByUserDialog(
    post: Post,
    password: String,
    onPasswordChange: (String) -> Unit,
    imageOnly: Boolean,
    onImageOnlyChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val passwordInputState = rememberStableTextInputState(
        text = password,
        onTextChange = onPasswordChange,
        analyticsFieldLabel = "本人削除キー"
    )
    AlertDialog(
        onDismissRequest = {
            AnalyticsTracker.uiControl("delete_by_user_dismiss", "本人削除を閉じる")
            onDismiss()
        },
        confirmButton = {
            TextButton(onClick = {
                AnalyticsTracker.uiControl("delete_by_user_confirm", "本人削除を確定")
                onConfirm()
            }) {
                Text("削除")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                AnalyticsTracker.uiControl("delete_by_user_cancel", "本人削除をキャンセル")
                onDismiss()
            }) {
                Text("キャンセル")
            }
        },
        title = { Text("No.${post.id} を削除") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = passwordInputState.value,
                    onValueChange = { nextValue ->
                        val wasFilled = passwordInputState.value.text.isNotBlank()
                        val isFilled = nextValue.text.isNotBlank()
                        if (wasFilled != isFilled) {
                            AnalyticsTracker.uiControl(
                                "delete_by_user_key_state",
                                if (isFilled) "本人削除キーの入力を開始" else "本人削除キーを消去",
                                mapOf("input_state" to if (isFilled) "入力あり" else "空")
                            )
                        }
                        passwordInputState.onValueChange(nextValue)
                    },
                    label = { Text("削除キー") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = imageOnly,
                        onCheckedChange = {
                            AnalyticsTracker.uiControl(
                                "delete_by_user_image_only",
                                "画像だけ削除を切替",
                                mapOf("value" to if (it) "enabled" else "disabled")
                            )
                            onImageOnlyChange(it)
                        }
                    )
                    Text("画像だけ消す")
                }
            }
        }
    )
}
