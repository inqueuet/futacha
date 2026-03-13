package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.valoser.futacha.shared.model.CatalogDisplayStyle
import com.valoser.futacha.shared.network.ArchiveSearchItem
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import com.valoser.futacha.shared.util.ImageData

private const val DIALOG_MIN_CATALOG_GRID_COLUMNS = 2
private const val DIALOG_MAX_CATALOG_GRID_COLUMNS = 8

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
    onClear: () -> Unit
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
        showPassword = true
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

    AlertDialog(
        onDismissRequest = onDismiss,
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
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("検索ワード") },
                    placeholder = { Text("例: ふたば (空欄で全件取得)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSearch(query) }
            ) {
                Text("検索")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
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
        onDismissRequest = onDismiss,
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
                        TextButton(onClick = onRetry) {
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
                            items(state.items, key = { it.threadId }) { item ->
                                PastSearchResultRow(
                                    item = item,
                                    onClick = { onItemSelected(item) }
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
                model = ImageRequest.Builder(LocalPlatformContext.current)
                    .data(item.thumbUrl)
                    .crossfade(true)
                    .build(),
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
                        Icon(
                            imageVector = Icons.Outlined.Image,
                            contentDescription = null,
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun CatalogSettingsSheet(
    onDismiss: () -> Unit,
    onAction: (CatalogSettingsMenuItem) -> Unit
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
                text = "設定メニュー",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            CatalogSettingsMenuItem.entries.forEach { menuItem ->
                ListItem(
                    leadingContent = { Icon(imageVector = menuItem.icon, contentDescription = null) },
                    headlineContent = {
                        Column {
                            Text(menuItem.label)
                            menuItem.description?.let { description ->
                                Text(
                                    text = description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable { onAction(menuItem) }
                )
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun WatchWordsSheet(
    watchWords: List<String>,
    onAddWord: (String) -> Unit,
    onRemoveWord: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var input by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "監視ワード",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "各カタログモードで見つかった一致スレを「監視」モードでまとめて確認できます",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "閉じる")
                }
            }

            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("追加するワード") },
                placeholder = { Text("例: 夏休み") },
                singleLine = true,
                trailingIcon = {
                    IconButton(
                        onClick = {
                            if (input.isNotBlank()) {
                                onAddWord(input)
                                input = ""
                            }
                        },
                        enabled = input.isNotBlank()
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "追加")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            if (watchWords.isEmpty()) {
                Text(
                    text = "まだ監視ワードは登録されていません",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(watchWords) { entry ->
                        ListItem(
                            headlineContent = { Text(entry) },
                            trailingContent = {
                                IconButton(onClick = { onRemoveWord(entry) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "削除")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DisplayStyleDialog(
    currentStyle: CatalogDisplayStyle,
    currentGridColumns: Int,
    onStyleSelected: (CatalogDisplayStyle) -> Unit,
    onGridColumnsSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("表示スタイル") },
        text = {
            Column {
                CatalogDisplayStyle.entries.forEach { style ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onStyleSelected(style) }
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = style == currentStyle,
                            onClick = { onStyleSelected(style) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = style.label,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                if (currentStyle == CatalogDisplayStyle.Grid) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "列数",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        (DIALOG_MIN_CATALOG_GRID_COLUMNS..DIALOG_MAX_CATALOG_GRID_COLUMNS).forEach { columns ->
                            FilterChip(
                                selected = columns == currentGridColumns,
                                onClick = { onGridColumnsSelected(columns) },
                                label = { Text("${columns}列") }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("閉じる")
            }
        }
    )
}
