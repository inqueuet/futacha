package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun ThreadAttachmentActionSheet(
    target: ThreadAttachmentActionTarget,
    onDismiss: () -> Unit,
    onPreview: () -> Unit,
    onJumpToPost: () -> Unit,
    onSave: () -> Unit,
    onOpenExternal: () -> Unit
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
                text = "添付の操作",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 2.dp)
            )
            Text(
                text = "No.${target.post.id}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            ListItem(
                leadingContent = {
                    Icon(Icons.Rounded.Image, contentDescription = null)
                },
                headlineContent = { Text("表示する") },
                supportingContent = { Text("プレビューを開きます") },
                modifier = Modifier.clickable { onPreview() }
            )
            if (target.canJumpToPost) {
                ListItem(
                    leadingContent = {
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
                    },
                    headlineContent = { Text("No.${target.post.id} へ移動") },
                    supportingContent = { Text("対象レスまでスクロールします") },
                    modifier = Modifier.clickable { onJumpToPost() }
                )
            }
            ListItem(
                leadingContent = {
                    Icon(Icons.Rounded.Archive, contentDescription = null)
                },
                headlineContent = { Text("保存") },
                supportingContent = { Text("添付だけを保存します") },
                modifier = Modifier.clickable { onSave() }
            )
            ListItem(
                leadingContent = {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
                },
                headlineContent = { Text("外部で開く") },
                supportingContent = { Text("ブラウザや対応アプリで開きます") },
                modifier = Modifier.clickable { onOpenExternal() }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
