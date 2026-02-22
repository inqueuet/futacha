package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.take
import androidx.compose.runtime.snapshotFlow

/**
 * History drawer content showing thread history with swipe-to-dismiss
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryDrawerContent(
    history: List<ThreadHistoryEntry>,
    onHistoryEntryDismissed: (ThreadHistoryEntry) -> Unit,
    onHistoryEntrySelected: (ThreadHistoryEntry) -> Unit,
    onBoardClick: () -> Unit = {},
    onRefreshClick: () -> Unit = {},
    onBatchDeleteClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    val drawerWidth = 320.dp
    ModalDrawerSheet(
        modifier = Modifier
            .width(drawerWidth)
            .fillMaxHeight(),
        drawerShape = MaterialTheme.shapes.extraLarge
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                item { HistoryListHeader() }
                items(
                    items = history,
                    key = {
                        buildString {
                            append(it.boardId)
                            append('|')
                            append(it.threadId)
                            append('|')
                            append(it.boardUrl)
                        }
                    }
                ) { entry ->
                    DismissibleHistoryEntry(
                        entry = entry,
                        onDismissed = onHistoryEntryDismissed,
                        onClicked = { onHistoryEntrySelected(entry) }
                    )
                }
            }
            HistoryBottomBar(
                onBoardClick = onBoardClick,
                onRefreshClick = onRefreshClick,
                onBatchDeleteClick = onBatchDeleteClick,
                onSettingsClick = onSettingsClick
            )
        }
    }
}

@Composable
private fun HistoryListHeader() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            text = "閲覧中のスレッド",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun HistoryBottomBar(
    onBoardClick: () -> Unit = {},
    onRefreshClick: () -> Unit = {},
    onBatchDeleteClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    Surface(color = MaterialTheme.colorScheme.primary) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HistoryBottomIcon(Icons.Rounded.Home, "板", onBoardClick)
            HistoryBottomIcon(
                icon = Icons.Rounded.Refresh,
                label = "更新"
            ) {
                onRefreshClick()
            }
            HistoryBottomIcon(Icons.Rounded.DeleteSweep, "一括削除", onBatchDeleteClick)
            HistoryBottomIcon(Icons.Rounded.Settings, "設定", onSettingsClick)
        }
    }
}

@Composable
private fun HistoryBottomIcon(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit = {}
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onPrimary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DismissibleHistoryEntry(
    entry: ThreadHistoryEntry,
    onDismissed: (ThreadHistoryEntry) -> Unit,
    onClicked: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState()
    LaunchedEffect(entry, dismissState) {
        snapshotFlow { dismissState.currentValue }
            .distinctUntilChanged()
            .filter { it == SwipeToDismissBoxValue.StartToEnd }
            .take(1)
            .collect {
                onDismissed(entry)
            }
    }
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromEndToStart = false,
        backgroundContent = { HistoryDismissBackground() }
    ) {
        HistoryEntryCard(entry = entry, onClick = onClicked)
    }
}

@Composable
private fun HistoryDismissBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = "削除",
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = "削除",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun HistoryEntryCard(
    entry: ThreadHistoryEntry,
    onClick: () -> Unit
) {
    val platformContext = LocalPlatformContext.current
    val formattedLastVisited = remember(entry.lastVisitedEpochMillis) {
        formatLastVisited(entry.lastVisitedEpochMillis)
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(platformContext)
                    .data(entry.titleImageUrl)
                    .crossfade(true)
                    .build(),
                imageLoader = LocalFutachaImageLoader.current,
                contentDescription = "${entry.title} のタイトル画像",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = entry.boardUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "最終閲覧: $formattedLastVisited",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                // FIX: 自動保存がある場合はアイコンを表示
                if (entry.hasAutoSave) {
                    Icon(
                        imageVector = Icons.Rounded.Folder,
                        contentDescription = "自動保存あり",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "保存済",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Text(
                    text = entry.replyCount.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "レス数",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Thread form dialog for creating new threads or posting replies
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThreadFormDialog(
    title: String,
    subtitle: String?,
    barColorScheme: androidx.compose.material3.ColorScheme = MaterialTheme.colorScheme,
    attachmentPickerPreference: AttachmentPickerPreference,
    preferredFileManagerPackage: String?,
    emailPresets: List<String>,
    comment: String,
    onCommentChange: (String) -> Unit,
    name: String,
    onNameChange: (String) -> Unit,
    email: String,
    onEmailChange: (String) -> Unit,
    subject: String,
    onSubjectChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    selectedImage: com.valoser.futacha.shared.util.ImageData?,
    onImageSelected: (com.valoser.futacha.shared.util.ImageData?) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    isSubmitEnabled: Boolean,
    sendDescription: String,
    showSubject: Boolean = true,
    showPassword: Boolean = true
) {
    val commentLineCount = remember(comment) {
        if (comment.isBlank()) 0 else comment.count { it == '\n' } + 1
    }
    val commentByteCount = remember(comment) { utf8ByteLength(comment) }
    val scrollState = rememberScrollState()
    val textFieldColors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
        unfocusedIndicatorColor = MaterialTheme.colorScheme.outline
    )
    val imagePickerLauncher = rememberAttachmentPickerLauncher(
        preference = attachmentPickerPreference,
        preferredFileManagerPackage = preferredFileManagerPackage,
        onImageSelected = { image ->
            onImageSelected(image)
        }
    )
    val videoPickerLauncher = rememberAttachmentPickerLauncher(
        preference = attachmentPickerPreference,
        mimeType = "video/*",
        preferredFileManagerPackage = preferredFileManagerPackage,
        onImageSelected = { image ->
            onImageSelected(image)
        }
    )
    var overflowMenuExpanded by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
            ) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            subtitle?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "閉じる"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { overflowMenuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Outlined.MoreVert,
                                contentDescription = "その他"
                            )
                        }
                        DropdownMenu(
                            expanded = overflowMenuExpanded,
                            onDismissRequest = { overflowMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("キャンセル") },
                                onClick = {
                                    overflowMenuExpanded = false
                                    onDismiss()
                                }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = barColorScheme.primary,
                        titleContentColor = barColorScheme.onPrimary,
                        navigationIconContentColor = barColorScheme.onPrimary,
                        actionIconContentColor = barColorScheme.onPrimary
                    )
                )
                HorizontalDivider()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        TextField(
                            value = comment,
                            onValueChange = onCommentChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusProperties {
                                    up = FocusRequester.Cancel
                                    down = FocusRequester.Cancel
                                    left = FocusRequester.Cancel
                                    right = FocusRequester.Cancel
                                },
                            label = { Text("コメント") },
                            minLines = 2,
                            maxLines = 5,
                            textStyle = MaterialTheme.typography.bodyLarge,
                            colors = textFieldColors,
                            trailingIcon = {
                                if (comment.isNotBlank()) {
                                    IconButton(onClick = {
                                        onCommentChange("")
                                    }) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "コメントをクリア"
                                        )
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Default),
                            keyboardActions = KeyboardActions(
                                onNext = {},
                                onPrevious = {}
                            )
                        )
                        Text(
                            text = "${commentLineCount}行 ${commentByteCount}バイト",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    TextField(
                        value = name,
                        onValueChange = onNameChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("おなまえ") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        colors = textFieldColors,
                        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        TextField(
                            value = email,
                            onValueChange = onEmailChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("メール") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge,
                            colors = textFieldColors,
                            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            emailPresets.forEachIndexed { index, preset ->
                                Text(
                                    text = preset,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clickable { onEmailChange(preset) }
                                        .padding(start = if (index == 0) 0.dp else 8.dp)
                                )
                            }
                        }
                    }

                    if (showSubject) {
                        TextField(
                            value = subject,
                            onValueChange = onSubjectChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("題名") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge,
                            colors = textFieldColors,
                            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next)
                        )
                    }

                    if (showPassword) {
                        TextField(
                            value = password,
                            onValueChange = onPasswordChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("削除キー") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge,
                            visualTransformation = PasswordVisualTransformation(),
                            supportingText = {
                                Text("削除用. 英数字で8字以内", style = MaterialTheme.typography.bodySmall)
                            },
                            colors = textFieldColors,
                            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done)
                        )
                    }

                    selectedImage?.let { image ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = image.fileName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${image.bytes.size / 1024} KB",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { onImageSelected(null) }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = "添付を削除"
                                    )
                                }
                            }
                        }
                    }
                }
                HorizontalDivider()
                Surface(
                    color = barColorScheme.surfaceVariant,
                    contentColor = barColorScheme.onSurfaceVariant
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .padding(horizontal = 4.dp)
                            .navigationBarsPadding()
                            .imePadding(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = onSubmit,
                            enabled = isSubmitEnabled
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = sendDescription,
                                tint = LocalContentColor.current
                            )
                        }
                        IconButton(onClick = { imagePickerLauncher() }) {
                            Icon(
                                imageVector = Icons.Outlined.Image,
                                contentDescription = "画像を選択",
                                tint = LocalContentColor.current
                            )
                        }
                        IconButton(onClick = { videoPickerLauncher() }) {
                            Icon(
                                imageVector = Icons.Rounded.VideoLibrary,
                                contentDescription = "動画を選択",
                                tint = LocalContentColor.current
                            )
                        }
                        IconButton(onClick = onClear) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "入力をクリア",
                                tint = LocalContentColor.current
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Outlined.MoreVert,
                                contentDescription = "その他",
                                tint = LocalContentColor.current
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Section toggle for NG management sheet
 */
private enum class NgManagementSection {
    Header,
    Word
}

@Composable
private fun SectionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Text(label)
    }
}

/**
 * NG (block) management sheet for filtering headers and words
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NgManagementSheet(
    onDismiss: () -> Unit,
    ngHeaders: List<String>,
    ngWords: List<String>,
    ngFilteringEnabled: Boolean,
    onAddHeader: (String) -> Unit,
    onAddWord: (String) -> Unit,
    onRemoveHeader: (String) -> Unit,
    onRemoveWord: (String) -> Unit,
    onToggleFiltering: () -> Unit,
    initialInput: String? = null,
    includeHeaderSection: Boolean = true,
    includeWordSection: Boolean = true
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val allowedSections = remember(includeHeaderSection, includeWordSection) {
        buildList {
            if (includeHeaderSection) add(NgManagementSection.Header)
            if (includeWordSection) add(NgManagementSection.Word)
        }.ifEmpty { listOf(NgManagementSection.Word) }
    }
    val defaultSection = remember(includeHeaderSection, includeWordSection) {
        when {
            includeHeaderSection -> NgManagementSection.Header
            else -> NgManagementSection.Word
        }
    }
    var section by rememberSaveable(includeHeaderSection, includeWordSection) {
        mutableStateOf(defaultSection)
    }
    LaunchedEffect(allowedSections) {
        if (section !in allowedSections) {
            section = allowedSections.first()
        }
    }
    var input by rememberSaveable(section) { mutableStateOf("") }
    LaunchedEffect(section, initialInput) {
        input = when (section) {
            NgManagementSection.Header -> if (includeHeaderSection) {
                initialInput?.takeIf { it.isNotBlank() } ?: ""
            } else {
                ""
            }
            NgManagementSection.Word -> ""
        }
    }
    val entries = when (section) {
        NgManagementSection.Header -> ngHeaders
        NgManagementSection.Word -> ngWords
    }
    val hint = when (section) {
        NgManagementSection.Header -> "ヘッダーに含めたい文字列"
        NgManagementSection.Word -> "本文に含めたい文字列"
    }
    val sectionLabel = when (section) {
        NgManagementSection.Header -> "NGヘッダー"
        NgManagementSection.Word -> "NGワード"
    }
    val descriptionText = if (includeHeaderSection) {
        "一致したレスが即座に非表示になります"
    } else {
        "一致したスレッドが即座に非表示になります"
    }

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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "NG管理",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = descriptionText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "閉じる")
                }
            }

            if (allowedSections.size > 1) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    allowedSections.forEach { availableSection ->
                        SectionChip(
                            label = if (availableSection == NgManagementSection.Header) "NGヘッダー" else "NGワード",
                            selected = section == availableSection,
                            onClick = { section = availableSection }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("$sectionLabel を追加") },
                placeholder = { Text(hint) },
                singleLine = true,
                trailingIcon = {
                    IconButton(
                        onClick = {
                            val trimmed = input.trim()
                            if (trimmed.isEmpty()) return@IconButton
                            when (section) {
                                NgManagementSection.Header -> onAddHeader(trimmed)
                                NgManagementSection.Word -> onAddWord(trimmed)
                            }
                            input = ""
                        },
                        enabled = input.isNotBlank()
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "追加")
                    }
                }
            )

            if (entries.isEmpty()) {
                Text(
                    text = "まだ登録されていません",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(entries) { entry ->
                        ListItem(
                            headlineContent = { Text(entry) },
                            trailingContent = {
                                IconButton(
                                    onClick = {
                                        when (section) {
                                            NgManagementSection.Header -> onRemoveHeader(entry)
                                            NgManagementSection.Word -> onRemoveWord(entry)
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Delete,
                                        contentDescription = "削除"
                                    )
                                }
                            }
                        )
                    }
                }
            }

            Button(
                onClick = onToggleFiltering,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (ngFilteringEnabled) "NGを無効にする" else "NGを有効にする")
            }
        }
    }
}

/**
 * Helper function to increment "そうだねx{count}" label
 */
internal fun incrementSaidaneLabel(current: String?): String {
    val normalized = current?.trim().orEmpty()
    val existing = normalized.takeIf { it.isNotBlank() }?.let {
        Regex("(\\d+)$").find(it)?.value?.toIntOrNull()
    } ?: 0
    val next = (existing + 1).coerceAtLeast(1)
    return "そうだねx$next"
}

private fun utf8ByteLength(value: String): Int {
    var total = 0
    var index = 0
    while (index < value.length) {
        val code = value[index].code
        val nextCode = value.getOrNull(index + 1)?.code
        val hasSurrogatePair =
            code in 0xD800..0xDBFF &&
                nextCode != null &&
                nextCode in 0xDC00..0xDFFF
        total += when {
            code <= 0x7F -> 1
            code <= 0x7FF -> 2
            hasSurrogatePair -> {
                index += 1
                4
            }
            else -> 3
        }
        index += 1
    }
    return total
}

/**
 * Platform-specific attachment picker launcher
 */
@Composable
expect fun rememberAttachmentPickerLauncher(
    preference: AttachmentPickerPreference = AttachmentPickerPreference.MEDIA,
    mimeType: String = "image/*",
    onImageSelected: (com.valoser.futacha.shared.util.ImageData) -> Unit,
    preferredFileManagerPackage: String? = null
): () -> Unit
