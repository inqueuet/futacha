package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import com.valoser.futacha.shared.util.ImageData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThreadFormDialog(
    title: String,
    subtitle: String?,
    barColorScheme: ColorScheme = MaterialTheme.colorScheme,
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
    selectedImage: ImageData?,
    onImageSelected: (ImageData?) -> Unit,
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
        onImageSelected = { image -> onImageSelected(image) }
    )
    val videoPickerLauncher = rememberAttachmentPickerLauncher(
        preference = attachmentPickerPreference,
        mimeType = "video/*",
        preferredFileManagerPackage = preferredFileManagerPackage,
        onImageSelected = { image -> onImageSelected(image) }
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
                                    IconButton(onClick = { onCommentChange("") }) {
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
