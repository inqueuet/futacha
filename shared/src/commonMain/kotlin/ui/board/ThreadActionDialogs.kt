package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.model.Post

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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                modifier = Modifier.clickable { onQuote() }
            )
            ListItem(
                leadingContent = {
                    Icon(Icons.Rounded.Block, contentDescription = null)
                },
                headlineContent = { Text("NG登録") },
                supportingContent = { Text("IDやワードをNG管理に追加") },
                modifier = Modifier.clickable { onNgRegister() }
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
                        onClick = onSaidane
                    )
            )
            ListItem(
                leadingContent = {
                    Icon(Icons.Outlined.Flag, contentDescription = null)
                },
                headlineContent = { Text("DEL 依頼") },
                supportingContent = { Text("管理人へ削除依頼を送信") },
                modifier = Modifier.clickable { onDelRequest() }
            )
            ListItem(
                leadingContent = {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                },
                headlineContent = { Text("削除 (本人)") },
                supportingContent = { Text("削除キーでレスまたは画像を削除") },
                modifier = Modifier.clickable { onDelete() }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
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
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("削除")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        },
        title = { Text("No.${post.id} を削除") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
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
                        onCheckedChange = onImageOnlyChange
                    )
                    Text("画像だけ消す")
                }
            }
        }
    )
}

@Composable
internal fun QuoteSelectionDialog(
    post: Post,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    val selectionItems = remember(post.id) { buildQuoteSelectionItems(post) }
    var selectedIds by remember(post.id) {
        mutableStateOf(defaultQuoteSelectionIds(selectionItems))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val lines = selectionItems
                        .filter { selectedIds.contains(it.id) }
                        .map { it.content }
                    onConfirm(lines)
                },
                enabled = selectedIds.isNotEmpty(),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text("コピー")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text("キャンセル")
            }
        },
        title = { Text("引用する内容を選択") },
        text = {
            if (selectionItems.isEmpty()) {
                Text(
                    text = "引用できる内容がありません",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    selectionItems.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable {
                                    selectedIds = if (selectedIds.contains(item.id)) {
                                        selectedIds - item.id
                                    } else {
                                        selectedIds + item.id
                                    }
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedIds.contains(item.id),
                                onCheckedChange = { checked ->
                                    selectedIds = if (checked) {
                                        selectedIds + item.id
                                    } else {
                                        selectedIds - item.id
                                    }
                                }
                            )
                            Column(
                                modifier = Modifier.padding(start = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = item.preview,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}
