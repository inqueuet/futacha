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
    bindings: GlobalSettingsSaveSectionBindings
) {
    val state = bindings.state
    val callbacks = bindings.callbacks
    val text = state.text
    val manualSaveInputState = rememberStableTextInputState(
        text = state.manualSaveInput,
        onTextChange = callbacks.onManualSaveInputChanged
    )

    SettingsSection(
        title = text.sectionTitle,
        icon = Icons.Rounded.Folder,
        description = text.sectionDescription
    ) {
        ListItem(
            headlineContent = { Text(text.preferredAppTitle) },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = text.preferredAppDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = state.preferredFileManagerState.currentSettingText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.preferredFileManagerState.isConfigured) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = callbacks.onOpenFileManagerPicker) {
                            Text(text.preferredAppButtonLabel)
                        }
                        if (state.preferredFileManagerState.isConfigured) {
                            OutlinedButton(onClick = { callbacks.onClearPreferredFileManager?.invoke() }) {
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
                        text = text.manualSaveDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.availableSaveDirectorySelections.forEach { selection ->
                            FilterChip(
                                selected = state.effectiveSaveDirectorySelection == selection,
                                onClick = { callbacks.onSaveDirectorySelectionChanged(selection) },
                                label = {
                                    Text(
                                        when (selection) {
                                            SaveDirectorySelection.MANUAL_INPUT -> "手入力"
                                            SaveDirectorySelection.PICKER -> text.pickerSelectionLabel
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
                                text = state.saveDestinationModeLabel,
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = state.resolvedManualPath,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = state.saveDestinationHint,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    state.defaultSaveWarningText?.let { warningText ->
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                text.defaultWarningTitle?.let { title ->
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                                Text(
                                    text = warningText,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    when (state.effectiveSaveDirectorySelection) {
                        SaveDirectorySelection.MANUAL_INPUT -> {
                            OutlinedTextField(
                                value = manualSaveInputState.value,
                                onValueChange = manualSaveInputState.onValueChange,
                                singleLine = true,
                                placeholder = { Text(DEFAULT_MANUAL_SAVE_ROOT) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(text.manualSaveInputLabel) }
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                TextButton(onClick = callbacks.onResetManualSaveDirectory) {
                                    Text(text.resetManualSaveButtonLabel)
                                }
                                Button(onClick = callbacks.onUpdateManualSaveDirectory) {
                                    Text(text.updateManualSaveButtonLabel)
                                }
                            }
                        }
                        SaveDirectorySelection.PICKER -> {
                            Text(
                                text = state.saveDirectoryPickerState.descriptionText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = state.saveDirectoryPickerState.warningText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = { callbacks.onOpenSaveDirectoryPicker?.invoke() },
                                    enabled = state.saveDirectoryPickerState.isPickerButtonEnabled
                                ) {
                                    Text(text.pickerButtonLabel)
                                }
                                if (state.saveDirectoryPickerState.showManualInputFallbackButton) {
                                    OutlinedButton(onClick = callbacks.onFallbackToManualInput) {
                                        Text(text.pickerFallbackButtonLabel)
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
                Button(
                    onClick = cacheCallbacks.clearImageCache,
                    enabled = !cacheCallbacks.isCleanupInProgress()
                ) {
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
                OutlinedButton(
                    onClick = cacheCallbacks.clearTemporaryCache,
                    enabled = !cacheCallbacks.isCleanupInProgress()
                ) {
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
                    storageSummaryState.historyDiagnosticsText?.let { diagnosticsText ->
                        Text(
                            text = diagnosticsText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (storageSummaryState.isHistoryDiagnosticsWarning) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
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
