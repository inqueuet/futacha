package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.analytics.AnalyticsTracker
import com.valoser.futacha.shared.model.CatalogDisplayStyle

private const val DIALOG_MIN_CATALOG_GRID_COLUMNS = 2
private const val DIALOG_MAX_CATALOG_GRID_COLUMNS = 8

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun CatalogSettingsSheet(
    onDismiss: () -> Unit,
    onAction: (CatalogSettingsMenuItem) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = {
            AnalyticsTracker.uiControl("catalog_settings", "カタログ設定メニューを閉じる")
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
                        .clickable {
                            AnalyticsTracker.uiControl("catalog_settings", "カタログ設定: ${menuItem.label}")
                            onAction(menuItem)
                        }
                )
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun WatchWordsSheet(
    globalWatchWords: List<String>,
    boardWatchWordsOverride: List<String>?,
    effectiveBoardWatchWords: List<String>,
    boardName: String,
    isLoaded: Boolean,
    onAddGlobalWord: (String) -> Unit,
    onRemoveGlobalWord: (String) -> Unit,
    onAddBoardWord: (String) -> Unit,
    onRemoveBoardWord: (String) -> Unit,
    onClearBoardOverride: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var globalInput by remember { mutableStateOf("") }
    var boardInput by remember { mutableStateOf("") }
    val globalInputState = rememberStableTextInputState(
        text = globalInput,
        onTextChange = { globalInput = it },
        analyticsFieldLabel = "共通監視ワード"
    )
    val boardInputState = rememberStableTextInputState(
        text = boardInput,
        onTextChange = { boardInput = it },
        analyticsFieldLabel = "板別監視ワード"
    )
    val boardWords = boardWatchWordsOverride ?: effectiveBoardWatchWords
    val isBoardOverridden = boardWatchWordsOverride != null

    ModalBottomSheet(
        onDismissRequest = {
            AnalyticsTracker.uiControl("watch_words", "監視ワード設定を閉じる")
            onDismiss()
        },
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
                        text = "共通設定と板別設定を編集できます",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    AnalyticsTracker.uiControl("watch_words", "監視ワード設定を閉じる")
                    onDismiss()
                }) {
                    Icon(Icons.Rounded.Close, contentDescription = "閉じる")
                }
            }

            WatchWordsSection(
                title = "共通",
                description = "板別設定がない板で使われます",
                words = globalWatchWords,
                input = globalInputState.value,
                onInputChange = globalInputState.onValueChange,
                onAddWord = {
                    onAddGlobalWord(globalInput)
                    globalInput = ""
                },
                onRemoveWord = onRemoveGlobalWord,
                emptyMessage = "共通の監視ワードは登録されていません",
                enabled = isLoaded
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (boardName.isBlank()) "この板" else boardName,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = if (isBoardOverridden) {
                            "板別設定を使用中"
                        } else {
                            "共通設定を継承中"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isBoardOverridden) {
                    TextButton(
                        onClick = {
                            AnalyticsTracker.uiControl("watch_words", "板別監視ワードを共通設定に戻す")
                            onClearBoardOverride()
                        },
                        enabled = isLoaded
                    ) {
                        Text("共通に戻す")
                    }
                }
            }

            WatchWordsSection(
                title = "板別",
                description = if (isBoardOverridden) "空にするとこの板では監視しません" else "追加するとこの板専用の設定になります",
                words = boardWords,
                input = boardInputState.value,
                onInputChange = boardInputState.onValueChange,
                onAddWord = {
                    onAddBoardWord(boardInput)
                    boardInput = ""
                },
                onRemoveWord = onRemoveBoardWord,
                emptyMessage = if (isBoardOverridden) {
                    "この板では監視ワードを使いません"
                } else {
                    "共通の監視ワードを継承しています"
                },
                enabled = isLoaded
            )
        }
    }
}

@Composable
private fun WatchWordsSection(
    title: String,
    description: String,
    words: List<String>,
    input: TextFieldValue,
    onInputChange: (TextFieldValue) -> Unit,
    onAddWord: () -> Unit,
    onRemoveWord: (String) -> Unit,
    emptyMessage: String,
    enabled: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        OutlinedTextField(
            value = input,
            onValueChange = { nextValue ->
                val wasFilled = input.text.isNotBlank()
                val isFilled = nextValue.text.isNotBlank()
                if (wasFilled != isFilled) {
                    AnalyticsTracker.uiControl(
                        "watch_word_field_state",
                        if (isFilled) "$title の監視ワード入力を開始" else "$title の監視ワードを消去",
                        mapOf("input_state" to if (isFilled) "入力あり" else "空")
                    )
                }
                onInputChange(nextValue)
            },
            label = { Text("追加するワード") },
            placeholder = { Text("例: 夏休み") },
            singleLine = true,
            enabled = enabled,
            trailingIcon = {
                IconButton(
                    onClick = {
                        if (input.text.isNotBlank()) {
                            AnalyticsTracker.uiControl("watch_words", "$title の監視ワードを追加")
                            onAddWord()
                        }
                    },
                    enabled = enabled && input.text.isNotBlank()
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "追加")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        if (words.isEmpty()) {
            Text(
                text = emptyMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 72.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(
                    items = words,
                    key = { entry -> "$title:$entry" }
                ) { entry ->
                    ListItem(
                        headlineContent = { Text(entry) },
                        trailingContent = {
                            IconButton(
                                onClick = {
                                    AnalyticsTracker.uiControl("watch_words", "$title の監視ワードを削除")
                                    onRemoveWord(entry)
                                },
                                enabled = enabled
                            ) {
                                Icon(Icons.Rounded.Delete, contentDescription = "削除")
                            }
                        }
                    )
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
        onDismissRequest = {
            AnalyticsTracker.uiControl("catalog_display_style", "カタログ表示方法を閉じる")
            onDismiss()
        },
        title = { Text("表示スタイル") },
        text = {
            Column {
                CatalogDisplayStyle.entries.forEach { style ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                AnalyticsTracker.uiControl("catalog_display_style", "カタログ表示方法: ${style.label}")
                                onStyleSelected(style)
                            }
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = style == currentStyle,
                            onClick = {
                                AnalyticsTracker.uiControl("catalog_display_style", "カタログ表示方法: ${style.label}")
                                onStyleSelected(style)
                            }
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
                                onClick = {
                                    AnalyticsTracker.uiControl("catalog_display_style", "カタログの列数を${columns}列に変更")
                                    onGridColumnsSelected(columns)
                                },
                                label = { Text("${columns}列") }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                AnalyticsTracker.uiControl("catalog_display_style", "カタログ表示方法を閉じる")
                onDismiss()
            }) {
                Text("閉じる")
            }
        }
    )
}
