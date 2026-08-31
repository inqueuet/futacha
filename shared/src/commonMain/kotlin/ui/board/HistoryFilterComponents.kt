package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryFilterSheet(
    totalCount: Int,
    filteredCount: Int,
    settings: HistoryViewSettings,
    boardOptions: List<HistoryBoardFilterOption>,
    onSettingsChanged: (HistoryViewSettings) -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "履歴の表示設定",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${totalCount.coerceAtLeast(0)}件中 ${filteredCount.coerceAtLeast(0)}件を表示",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "日時が記録されていない古い履歴は、日時順では末尾に表示されます。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item {
                    HistoryFilterSectionTitle("並び替え")
                    HistorySortOption.entries.forEach { option ->
                        HistoryRadioRow(
                            label = option.label,
                            selected = settings.sortOption == option,
                            onClick = { onSettingsChanged(settings.copy(sortOption = option)) }
                        )
                    }
                }
                item {
                    HistoryFilterSectionTitle("並び順")
                    HistorySortDirection.entries.forEach { direction ->
                        val label = when {
                            settings.sortOption == HistorySortOption.Title &&
                                direction == HistorySortDirection.Ascending -> "昇順（あ→ん）"
                            settings.sortOption == HistorySortOption.Title -> "降順（ん→あ）"
                            else -> direction.label
                        }
                        HistoryRadioRow(
                            label = label,
                            selected = settings.sortDirection == direction,
                            onClick = { onSettingsChanged(settings.copy(sortDirection = direction)) }
                        )
                    }
                }
                item {
                    HistoryFilterSectionTitle("表示する履歴")
                    HistoryCheckboxRow(
                        label = "自分が書き込んだスレだけ",
                        checked = settings.selfPostsOnly,
                        onCheckedChange = { checked ->
                            onSettingsChanged(settings.copy(selfPostsOnly = checked))
                        }
                    )
                }
                item {
                    HistoryFilterSectionTitle("スレの確認状態")
                    HistoryLifeFilter.entries.forEach { filter ->
                        HistoryRadioRow(
                            label = filter.label,
                            selected = settings.lifeFilter == filter,
                            onClick = { onSettingsChanged(settings.copy(lifeFilter = filter)) }
                        )
                    }
                }
                item {
                    HistoryFilterSectionTitle("板")
                    HistoryBoardSelector(
                        selectedKey = settings.boardKey,
                        boardOptions = boardOptions,
                        onSelected = { key -> onSettingsChanged(settings.copy(boardKey = key)) }
                    )
                }
                item {
                    HistoryFilterSectionTitle("スレタイ")
                    OutlinedTextField(
                        value = settings.titleQuery,
                        onValueChange = { query ->
                            onSettingsChanged(settings.copy(titleQuery = query))
                        },
                        label = { Text("タイトルに含む文字") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 4.dp)
                    )
                }
            }
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("初期設定に戻す", maxLines = 1)
                }
                Button(
                    onClick = onApply,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("適用")
                }
            }
        }
    }
}

@Composable
private fun HistoryFilterSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun HistoryRadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun HistoryCheckboxRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun HistoryBoardSelector(
    selectedKey: String?,
    boardOptions: List<HistoryBoardFilterOption>,
    onSelected: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = boardOptions.firstOrNull { it.key == selectedKey }?.label ?: "すべての板"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp)
    ) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = selectedLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 360.dp)
        ) {
            DropdownMenuItem(
                text = { Text("すべての板") },
                onClick = {
                    expanded = false
                    onSelected(null)
                }
            )
            boardOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        expanded = false
                        onSelected(option.key)
                    }
                )
            }
        }
    }
}

@Composable
internal fun HistoryActiveSettingsBanner(
    visibleCount: Int,
    totalCount: Int,
    labels: List<String>,
    onReset: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "${totalCount.coerceAtLeast(0)}件中 ${visibleCount.coerceAtLeast(0)}件を表示",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = labels.joinToString(" / "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            TextButton(onClick = onReset) {
                Text("初期表示に戻す")
            }
        }
    }
}

@Composable
internal fun HistoryEmptyFilterResult(
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("条件に一致する履歴はありません")
        TextButton(onClick = onReset) {
            Text("初期表示に戻す")
        }
    }
}

@Composable
internal fun HistoryBatchDeleteConfirmationDialog(
    totalCount: Int,
    visibleCount: Int,
    hasHiddenEntries: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("履歴を一括削除") },
        text = {
            Text(
                if (hasHiddenEntries) {
                    "表示中は${visibleCount}件ですが、フィルターで非表示の履歴を含む全${totalCount}件を削除します。"
                } else {
                    "履歴${totalCount}件をすべて削除します。この操作は取り消せません。"
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("全件削除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}
