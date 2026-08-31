package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.valoser.futacha.shared.analytics.AnalyticsTracker
import com.valoser.futacha.shared.analytics.analyticsSessionContextId
import com.valoser.futacha.shared.analytics.analyticsTextLengthBucket
import com.valoser.futacha.shared.model.ThreadBodyTextSize
import com.valoser.futacha.shared.network.ArchiveSearchItem
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import com.valoser.futacha.shared.util.ImageData

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun CreateThreadDialog(
    boardName: String?,
    attachmentPickerPreference: AttachmentPickerPreference,
    preferredFileManagerPackage: String?,
    name: String,
    onNameChange: (String) -> Unit,
    email: String,
    onEmailChange: (String) -> Unit,
    title: String,
    onTitleChange: (String) -> Unit,
    comment: String,
    onCommentChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    selectedImage: ImageData?,
    onImageSelected: (ImageData?) -> Unit,
    isSubmitEnabled: Boolean,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    bodyTextSize: ThreadBodyTextSize = ThreadBodyTextSize.Standard
) {
    val emailPresets = remember { listOf("ID表示", "IP表示", "sage") }

    ThreadFormDialog(
        title = "スレ立て",
        subtitle = boardName?.takeIf { it.isNotBlank() },
        barColorScheme = MaterialTheme.colorScheme,
        attachmentPickerPreference = attachmentPickerPreference,
        preferredFileManagerPackage = preferredFileManagerPackage,
        emailPresets = emailPresets,
        comment = comment,
        onCommentChange = onCommentChange,
        name = name,
        onNameChange = onNameChange,
        email = email,
        onEmailChange = onEmailChange,
        subject = title,
        onSubjectChange = onTitleChange,
        password = password,
        onPasswordChange = onPasswordChange,
        selectedImage = selectedImage,
        onImageSelected = onImageSelected,
        onDismiss = onDismiss,
        onSubmit = onSubmit,
        onClear = onClear,
        isSubmitEnabled = isSubmitEnabled,
        sendDescription = "スレ立て",
        showSubject = true,
        showPassword = true,
        bodyTextSize = bodyTextSize
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun PastThreadSearchNoticeDialog(
    onDismiss: () -> Unit,
    onContinue: (doNotShowAgain: Boolean) -> Unit
) {
    var doNotShowAgain by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = {
            AnalyticsTracker.uiControl("past_thread_search_notice", "過去スレ検索の案内を閉じる")
            onDismiss()
        },
        icon = {
            Icon(
                imageVector = Icons.Rounded.History,
                contentDescription = null
            )
        },
        title = { Text(buildPastThreadSearchNoticeTitle()) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    buildPastThreadSearchNoticeMessages().forEach { message ->
                        Text(
                            text = "・$message",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            doNotShowAgain = !doNotShowAgain
                            AnalyticsTracker.uiControl(
                                "past_thread_search_notice",
                                if (doNotShowAgain) "過去スレ検索の案内を次回から非表示" else "過去スレ検索の案内を次回も表示"
                            )
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = doNotShowAgain,
                        onCheckedChange = {
                            doNotShowAgain = it
                            AnalyticsTracker.uiControl(
                                "past_thread_search_notice",
                                if (it) "過去スレ検索の案内を次回から非表示" else "過去スレ検索の案内を次回も表示"
                            )
                        }
                    )
                    Text(
                        text = "次回から表示しない",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                AnalyticsTracker.uiControl(
                    "past_thread_search_notice",
                    "過去スレ検索へ進む",
                    mapOf("hide_notice_next_time" to if (doNotShowAgain) "enabled" else "disabled")
                )
                onContinue(doNotShowAgain)
            }) {
                Text("検索へ進む")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                AnalyticsTracker.uiControl("past_thread_search_notice", "過去スレ検索の案内を閉じる")
                onDismiss()
            }) {
                Text("閉じる")
            }
        }
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun PastThreadSearchDialog(
    initialQuery: String,
    onDismiss: () -> Unit,
    onSearch: (query: String) -> Unit
) {
    var query by rememberSaveable(initialQuery) { mutableStateOf(initialQuery) }
    val queryInputState = rememberStableTextInputState(
        text = query,
        onTextChange = { query = it },
        analyticsFieldLabel = "過去スレ検索"
    )

    AlertDialog(
        onDismissRequest = {
            AnalyticsTracker.uiControl("past_thread_search", "過去スレ検索を閉じる")
            onDismiss()
        },
        icon = {
            Icon(
                imageVector = Icons.Rounded.History,
                contentDescription = null
            )
        },
        title = { Text("過去スレ検索") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = queryInputState.value,
                    onValueChange = { nextValue ->
                        val wasFilled = queryInputState.value.text.isNotBlank()
                        val isFilled = nextValue.text.isNotBlank()
                        if (wasFilled != isFilled) {
                            AnalyticsTracker.uiControl(
                                "past_thread_search_field_state",
                                if (isFilled) "過去スレ検索語の入力を開始" else "過去スレ検索語を消去",
                                mapOf("input_state" to if (isFilled) "入力あり" else "空")
                            )
                        }
                        queryInputState.onValueChange(nextValue)
                    },
                    label = { Text("スレタイ / スレNo.") },
                    placeholder = { Text("例: テスト") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                AnalyticsTracker.uiControl(
                    "past_thread_search",
                    "過去スレを検索",
                    mapOf("query_length_bucket" to analyticsTextLengthBucket(query))
                )
                onSearch(query)
            }) {
                Text("検索")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                AnalyticsTracker.uiControl("past_thread_search", "過去スレ検索をキャンセル")
                onDismiss()
            }) {
                Text("キャンセル")
            }
        }
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun PastThreadSearchResultSheet(
    state: ArchiveSearchState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onItemSelected: (ArchiveSearchItem) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = {
            AnalyticsTracker.uiControl("past_thread_search_result", "過去スレ検索結果を閉じる")
            onDismiss()
        },
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "過去スレ検索結果",
                style = MaterialTheme.typography.titleMedium
            )
            when (state) {
                ArchiveSearchState.Idle -> {
                    Text(
                        text = buildPastThreadSearchIdleMessage(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                ArchiveSearchState.Loading -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Text("検索中…")
                    }
                }

                is ArchiveSearchState.Error -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        TextButton(onClick = {
                            AnalyticsTracker.uiControl("past_thread_search_result", "過去スレ検索を再試行")
                            onRetry()
                        }) {
                            Text("再試行")
                        }
                    }
                }

                is ArchiveSearchState.Success -> {
                    if (state.items.isEmpty()) {
                        Text(
                            text = buildPastThreadSearchEmptyMessage(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                        itemsIndexed(state.items, key = { index, item -> "${item.server}/${item.board}/${item.threadId}:$index" }) { _, item ->
                                PastSearchResultRow(
                                    item = item,
                                    onClick = {
                                        AnalyticsTracker.uiControl(
                                            "past_thread_search_result",
                                            "過去スレ検索結果を開く",
                                            mapOf(
                                                "thread_context" to analyticsSessionContextId(
                                                    "past_thread",
                                                    item.server,
                                                    item.board,
                                                    item.threadId
                                                )
                                            )
                                        )
                                        onItemSelected(item)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PastSearchResultRow(
    item: ArchiveSearchItem,
    onClick: () -> Unit
) {
    val imageLoader = LocalFutachaImageLoader.current
    val platformContext = LocalPlatformContext.current
    val density = LocalDensity.current
    val thumbnailSizePx = remember(density) {
        with(density) { 56.dp.roundToPx() }
    }
    val thumbnailRequest = remember(platformContext, item.thumbUrl, thumbnailSizePx) {
        ImageRequest.Builder(platformContext)
            .data(item.thumbUrl)
            .crossfade(false)
            .size(thumbnailSizePx, thumbnailSizePx)
            .build()
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val painter = rememberAsyncImagePainter(
                model = thumbnailRequest,
                imageLoader = imageLoader
            )
            val painterState by painter.state.collectAsState()
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                when (painterState) {
                    is AsyncImagePainter.State.Error, is AsyncImagePainter.State.Empty -> {
                        MediaThumbnailFallbackIcon(
                            url = item.thumbUrl,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    else -> {
                        Image(
                            painter = painter,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.title.orEmpty().ifBlank { "無題" },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.server}/${item.board}  No.${item.threadId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (item.replyCount > 0 || item.totalBytes != null) {
                    Text(
                        text = buildPastSearchResultMetaText(item),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item.status?.let { status ->
                    Text(
                        text = status,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                item.uploadedAt?.let { uploadedAt ->
                    Text(
                        text = "uploaded: $uploadedAt",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun buildPastSearchResultMetaText(item: ArchiveSearchItem): String {
    return buildList {
        if (item.replyCount > 0) add("${item.replyCount}レス")
        item.totalBytes?.let { add(formatPastSearchBytes(it)) }
    }.joinToString(" / ")
}

private fun formatPastSearchBytes(bytes: Long): String {
    if (bytes < 1024L) return "${bytes}B"
    val kib = bytes / 1024.0
    if (kib < 1024.0) return "${kib.toOneDecimalString()}KB"
    val mib = kib / 1024.0
    return "${mib.toOneDecimalString()}MB"
}

private fun Double.toOneDecimalString(): String {
    val scaled = (this * 10.0).toLong()
    val whole = scaled / 10L
    val fraction = scaled % 10L
    return if (fraction == 0L) "$whole" else "$whole.$fraction"
}
