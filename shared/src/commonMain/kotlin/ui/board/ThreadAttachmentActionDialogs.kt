package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.analytics.AnalyticsTracker

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun ThreadAttachmentActionSheet(
    target: ThreadAttachmentActionTarget,
    onDismiss: () -> Unit,
    onPreview: () -> Unit,
    onJumpToPost: () -> Unit,
    onSave: () -> Unit,
    onOpenExternal: () -> Unit,
    onImageSearch: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val features = LocalFutachaSharedFeatures.current
    val context = LocalFutachaThreadProjection.current
    var imageNgOpen by remember { mutableStateOf(false) }
    if (imageNgOpen && features != null && context != null) FutachaImageNgRegistration(features,
        context.boardKey, com.valoser.futacha.shared.compat.CompatImageNgSource.THREAD, target.url,
        "No.${target.post.id}", { imageNgOpen = false })
    ModalBottomSheet(
        onDismissRequest = {
            AnalyticsTracker.uiControl("attachment_action_sheet_dismiss", "添付メニューを閉じる")
            onDismiss()
        },
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
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
                modifier = Modifier.clickable {
                    AnalyticsTracker.uiControl("attachment_preview", "添付を表示")
                    onPreview()
                }
            )
            if (target.canJumpToPost) {
                ListItem(
                    leadingContent = {
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
                    },
                    headlineContent = { Text("No.${target.post.id} へ移動") },
                    supportingContent = { Text("対象レスまでスクロールします") },
                    modifier = Modifier.clickable {
                        AnalyticsTracker.uiControl("attachment_jump_to_post", "添付元の投稿へ移動")
                        onJumpToPost()
                    }
                )
            }
            ListItem(
                leadingContent = {
                    Icon(Icons.Rounded.Archive, contentDescription = null)
                },
                headlineContent = { Text("保存") },
                supportingContent = { Text("添付だけを保存します") },
                modifier = Modifier.clickable {
                    AnalyticsTracker.uiControl("attachment_save", "添付を保存")
                    onSave()
                }
            )
            if (onImageSearch != null && threadImageSearchTargets(target.url).isNotEmpty()) {
                ListItem(
                    leadingContent = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    headlineContent = { Text("画像検索") },
                    modifier = Modifier.clickable(onClick = onImageSearch)
                )
            }
            if (features != null && context != null) {
                ListItem(headlineContent = { Text("NG画像に登録") }, modifier = Modifier.clickable { imageNgOpen = true })
            }
            ListItem(
                leadingContent = {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
                },
                headlineContent = { Text("外部で開く") },
                supportingContent = { Text("ブラウザや対応アプリで開きます") },
                modifier = Modifier.clickable {
                    AnalyticsTracker.uiControl("attachment_open_external", "添付を外部で開く")
                    onOpenExternal()
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
