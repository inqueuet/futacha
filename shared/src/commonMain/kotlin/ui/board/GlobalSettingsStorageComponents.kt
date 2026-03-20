package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.service.DEFAULT_MANUAL_SAVE_ROOT
import com.valoser.futacha.shared.util.SaveDirectorySelection

@Composable
internal fun GlobalSettingsFileManagerPickerHost(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onFileManagerSelected: (packageName: String, label: String) -> Unit
) {
    if (isVisible) {
        FileManagerPickerDialog(
            onDismiss = onDismiss,
            onFileManagerSelected = onFileManagerSelected
        )
    }
}

@Composable
internal fun GlobalSettingsSaveSection(
    preferredFileManagerState: PreferredFileManagerSummaryState,
    onOpenFileManagerPicker: () -> Unit,
    onClearPreferredFileManager: (() -> Unit)?,
    availableSaveDirectorySelections: List<SaveDirectorySelection>,
    effectiveSaveDirectorySelection: SaveDirectorySelection,
    onSaveDirectorySelectionChanged: (SaveDirectorySelection) -> Unit,
    saveDestinationModeLabel: String,
    resolvedManualPath: String,
    saveDestinationHint: String,
    manualSaveInput: String,
    onManualSaveInputChanged: (String) -> Unit,
    onResetManualSaveDirectory: () -> Unit,
    onUpdateManualSaveDirectory: () -> Unit,
    saveDirectoryPickerState: SaveDirectoryPickerState,
    onOpenSaveDirectoryPicker: (() -> Unit)?,
    onFallbackToManualInput: () -> Unit
) {
    SettingsSection(
        title = "保存とファイラー",
        icon = Icons.Rounded.Folder,
        description = "保存先とディレクトリ選択まわりをまとめました。"
    ) {
        ListItem(
            headlineContent = { Text("優先ファイラー") },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "ディレクトリ選択で使うファイラーアプリを指定できます。設定すると次回からそのアプリを直接起動します。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = preferredFileManagerState.currentSettingText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (preferredFileManagerState.isConfigured) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onOpenFileManagerPicker) {
                            Text("ファイラーを選択")
                        }
                        if (preferredFileManagerState.isConfigured) {
                            OutlinedButton(onClick = { onClearPreferredFileManager?.invoke() }) {
                                Text("クリア")
                            }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("スレ保存先") },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "手入力ではプラットフォーム既定の保存領域を使います。「Documents」や「Download」は端末ごとの既定フォルダ系統に解決されます。絶対パス指定もできます。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        availableSaveDirectorySelections.forEach { selection ->
                            FilterChip(
                                selected = effectiveSaveDirectorySelection == selection,
                                onClick = { onSaveDirectorySelectionChanged(selection) },
                                label = {
                                    Text(
                                        when (selection) {
                                            SaveDirectorySelection.MANUAL_INPUT -> "手入力"
                                            SaveDirectorySelection.PICKER -> "ファイラーで選ぶ"
                                        }
                                    )
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = saveDestinationModeLabel,
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = resolvedManualPath,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = saveDestinationHint,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    when (effectiveSaveDirectorySelection) {
                        SaveDirectorySelection.MANUAL_INPUT -> {
                            OutlinedTextField(
                                value = manualSaveInput,
                                onValueChange = onManualSaveInputChanged,
                                singleLine = true,
                                placeholder = { Text(DEFAULT_MANUAL_SAVE_ROOT) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("フォルダ名またはパス") }
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                TextButton(onClick = onResetManualSaveDirectory) {
                                    Text("デフォルトに戻す")
                                }
                                Button(onClick = onUpdateManualSaveDirectory) {
                                    Text("保存先を更新")
                                }
                            }
                        }
                        SaveDirectorySelection.PICKER -> {
                            Text(
                                text = saveDirectoryPickerState.descriptionText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = saveDirectoryPickerState.warningText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = { onOpenSaveDirectoryPicker?.invoke() },
                                    enabled = saveDirectoryPickerState.isPickerButtonEnabled
                                ) {
                                    Text("フォルダを選択")
                                }
                                if (saveDirectoryPickerState.showManualInputFallbackButton) {
                                    OutlinedButton(onClick = onFallbackToManualInput) {
                                        Text("手入力に戻す")
                                    }
                                }
                            }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun GlobalSettingsCacheSection(
    cacheCallbacks: GlobalSettingsCacheCallbacks
) {
    SettingsSection(
        title = "キャッシュ管理",
        icon = Icons.Rounded.DeleteSweep,
        description = "表示用キャッシュを一括で掃除します。保存データは消えません。"
    ) {
        ListItem(
            headlineContent = { Text("画像キャッシュ") },
            supportingContent = {
                Text(
                    text = "サムネイル・画像のキャッシュを削除します。保存済みスレッドや履歴データは保持されます。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingContent = {
                Button(onClick = cacheCallbacks.clearImageCache) {
                    Text("削除")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("一時キャッシュを掃除") },
            supportingContent = {
                Text(
                    text = "一時フォルダに溜まった画像キャッシュをOS任せにせず強制削除します。保存済みスレッドは削除されません。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingContent = {
                OutlinedButton(onClick = cacheCallbacks.clearTemporaryCache) {
                    Text("掃除する")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun GlobalSettingsStorageSection(
    storageSummaryState: GlobalSettingsStorageSummaryState,
    onRefreshStorageStats: () -> Unit
) {
    SettingsSection(
        title = "履歴とオフライン保存",
        icon = Icons.Rounded.History,
        description = "保存量の目安を確認できます。"
    ) {
        ListItem(
            headlineContent = { Text("サイズの目安") },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = storageSummaryState.historyText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (storageSummaryState.isHistoryWarning) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Text(
                        text = storageSummaryState.autoSavedText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    storageSummaryState.warningText?.let { warningText ->
                        Text(
                            text = warningText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            trailingContent = {
                OutlinedButton(onClick = onRefreshStorageStats) {
                    Text("更新")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun GlobalSettingsLinksSection(
    settingsEntries: List<GlobalSettingsEntry>,
    linkCallbacks: GlobalSettingsLinkCallbacks
) {
    SettingsSection(
        title = "リンク・ポリシー",
        icon = Icons.Rounded.Link
    ) {
        settingsEntries.forEachIndexed { index, entry ->
            ListItem(
                leadingContent = { androidx.compose.material3.Icon(imageVector = entry.icon, contentDescription = null) },
                headlineContent = { Text(entry.label) },
                supportingContent = {
                    Text(
                        text = entry.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        linkCallbacks.onEntrySelected(entry.action)
                    }
            )
            if (index != settingsEntries.lastIndex) {
                HorizontalDivider()
            }
        }
    }
}

@Composable
internal fun GlobalSettingsAppInfoSection(appVersion: String) {
    SettingsSection(
        title = "アプリ情報",
        icon = Icons.Rounded.Info
    ) {
        ListItem(
            headlineContent = { Text("アプリバージョン") },
            trailingContent = { Text(appVersion) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
