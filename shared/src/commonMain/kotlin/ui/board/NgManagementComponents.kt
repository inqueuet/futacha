package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.analytics.AnalyticsTracker

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
    includeWordSection: Boolean = true,
    reviewBlockFlow: Boolean = false
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
    val inputState = rememberStableTextInputState(
        text = input,
        onTextChange = { input = it },
        analyticsFieldLabel = "NG語・NGヘッダー"
    )
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
        onDismissRequest = {
            AnalyticsTracker.uiControl("ng_management", "NG管理を閉じる")
            onDismiss()
        },
        sheetState = sheetState
    ) {
        Column(
            modifier = androidx.compose.ui.Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = androidx.compose.ui.Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = if (reviewBlockFlow) "この利用者をブロック" else "NG管理",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = descriptionText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = androidx.compose.ui.Modifier.weight(1f))
                IconButton(onClick = {
                    AnalyticsTracker.uiControl("ng_management", "NG管理を閉じる")
                    onDismiss()
                }) {
                    Icon(Icons.Rounded.Close, contentDescription = "閉じる")
                }
            }

            if (allowedSections.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    allowedSections.forEach { availableSection ->
                        SectionChip(
                            label = if (availableSection == NgManagementSection.Header) "NGヘッダー" else "NGワード",
                            selected = section == availableSection,
                            onClick = {
                                section = availableSection
                                AnalyticsTracker.uiControl(
                                    "ng_management",
                                    if (availableSection == NgManagementSection.Header) "NGヘッダー一覧を表示" else "NGワード一覧を表示"
                                )
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = inputState.value,
                onValueChange = { nextValue ->
                    val wasFilled = inputState.value.text.isNotBlank()
                    val isFilled = nextValue.text.isNotBlank()
                    if (wasFilled != isFilled) {
                        AnalyticsTracker.uiControl(
                            "ng_field_state",
                            if (isFilled) "$sectionLabel の入力を開始" else "$sectionLabel を消去",
                            mapOf("input_state" to if (isFilled) "入力あり" else "空")
                        )
                    }
                    inputState.onValueChange(nextValue)
                },
                label = { Text("$sectionLabel を追加") },
                placeholder = { Text(hint) },
                singleLine = true,
                trailingIcon = {
                    IconButton(
                        onClick = {
                            val trimmed = input.trim()
                            if (trimmed.isEmpty()) return@IconButton
                            AnalyticsTracker.uiControl(
                                "ng_management",
                                if (section == NgManagementSection.Header) "NGヘッダーを追加" else "NGワードを追加"
                            )
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
                },
                modifier = androidx.compose.ui.Modifier
                    .fillMaxWidth()
                    .testTag("ng-management-input")
            )

            if (reviewBlockFlow && section == NgManagementSection.Header) {
                Button(
                    onClick = {
                        val identity = input.trim()
                        if (!ngFilteringEnabled) onToggleFiltering()
                        onAddHeader(identity)
                        input = ""
                        onDismiss()
                    },
                    enabled = input.isNotBlank(),
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth()
                ) {
                    Text("この利用者をブロック")
                }
                Text(
                    text = if (input.isBlank()) {
                        "ブロックする利用者のID・IP・名前を入力してください。"
                    } else {
                        "「$input」に一致する利用者の投稿をこの端末で非表示にします。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (entries.isEmpty()) {
                Text(
                    text = "まだ登録されていません",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = androidx.compose.ui.Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(
                        items = entries,
                        key = { entry -> "${section.name}:$entry" }
                    ) { entry ->
                        ListItem(
                            headlineContent = { Text(entry) },
                            trailingContent = {
                                IconButton(
                                    onClick = {
                                        AnalyticsTracker.uiControl(
                                            "ng_management",
                                            if (section == NgManagementSection.Header) "NGヘッダーを削除" else "NGワードを削除"
                                        )
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
                onClick = {
                    AnalyticsTracker.uiControl(
                        "ng_management",
                        if (ngFilteringEnabled) "NGフィルターを無効化" else "NGフィルターを有効化"
                    )
                    onToggleFiltering()
                },
                modifier = androidx.compose.ui.Modifier.fillMaxWidth()
            ) {
                Text(if (ngFilteringEnabled) "NGを無効にする" else "NGを有効にする")
            }
        }
    }
}
