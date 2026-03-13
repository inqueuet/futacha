package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material.icons.rounded.WatchLater
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.model.CatalogNavEntryConfig
import com.valoser.futacha.shared.model.CatalogNavEntryPlacement
import com.valoser.futacha.shared.model.ThreadMenuEntryConfig
import com.valoser.futacha.shared.model.ThreadMenuEntryPlacement
import com.valoser.futacha.shared.service.DEFAULT_MANUAL_SAVE_ROOT
import com.valoser.futacha.shared.util.SaveDirectorySelection

internal data class GlobalSettingsEntry(
    val label: String,
    val description: String,
    val icon: ImageVector,
    val action: GlobalSettingsAction
)

internal val cookieSettingsEntry = GlobalSettingsEntry(
    label = "Cookie",
    description = "送信する Cookie を確認・削除",
    icon = Icons.Rounded.History,
    action = GlobalSettingsAction.Cookies
)

internal val globalSettingsEntries = listOf(
    GlobalSettingsEntry(
        label = "作者",
        description = "X (旧Twitter) で最新の動作報告を確認",
        icon = Icons.Rounded.Person,
        action = GlobalSettingsAction.X
    ),
    GlobalSettingsEntry(
        label = "お問い合わせ",
        description = "admin@valoser.com 宛にメールを送信します",
        icon = Icons.Rounded.Email,
        action = GlobalSettingsAction.Email
    ),
    GlobalSettingsEntry(
        label = "開発元",
        description = "GitHub でソースコードと issue を確認",
        icon = Icons.Rounded.Link,
        action = GlobalSettingsAction.Developer
    ),
    GlobalSettingsEntry(
        label = "プライバシーポリシー",
        description = "外部サイトでプライバシーポリシーを表示",
        icon = Icons.Rounded.PrivacyTip,
        action = GlobalSettingsAction.PrivacyPolicy
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GlobalSettingsScaffold(
    bindings: GlobalSettingsScaffoldBindings,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("設定") },
                navigationIcon = {
                    IconButton(onClick = bindings.onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "戻る"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(bindings.snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            item {
                GlobalSettingsBehaviorSection(
                    isBackgroundRefreshEnabled = bindings.behavior.isBackgroundRefreshEnabled,
                    onBackgroundRefreshChanged = bindings.behavior.onBackgroundRefreshChanged,
                    isLightweightModeEnabled = bindings.behavior.isLightweightModeEnabled,
                    onLightweightModeChanged = bindings.behavior.onLightweightModeChanged
                )
            }
            item {
                GlobalSettingsCatalogMenuSection(
                    localCatalogNavEntries = bindings.catalogMenu.localCatalogNavEntries,
                    catalogMenuCallbacks = bindings.catalogMenu.catalogMenuCallbacks
                )
            }
            item {
                GlobalSettingsThreadMenuSection(
                    localThreadMenuEntries = bindings.threadMenu.localThreadMenuEntries,
                    threadMenuCallbacks = bindings.threadMenu.threadMenuCallbacks
                )
            }
            item {
                GlobalSettingsSaveSection(
                    preferredFileManagerState = bindings.save.preferredFileManagerState,
                    onOpenFileManagerPicker = bindings.save.onOpenFileManagerPicker,
                    onClearPreferredFileManager = bindings.save.onClearPreferredFileManager,
                    availableSaveDirectorySelections = bindings.save.availableSaveDirectorySelections,
                    effectiveSaveDirectorySelection = bindings.save.effectiveSaveDirectorySelection,
                    onSaveDirectorySelectionChanged = bindings.save.onSaveDirectorySelectionChanged,
                    saveDestinationModeLabel = bindings.save.saveDestinationModeLabel,
                    resolvedManualPath = bindings.save.resolvedManualPath,
                    saveDestinationHint = bindings.save.saveDestinationHint,
                    manualSaveInput = bindings.save.manualSaveInput,
                    onManualSaveInputChanged = bindings.save.onManualSaveInputChanged,
                    onResetManualSaveDirectory = bindings.save.onResetManualSaveDirectory,
                    onUpdateManualSaveDirectory = bindings.save.onUpdateManualSaveDirectory,
                    saveDirectoryPickerState = bindings.save.saveDirectoryPickerState,
                    onOpenSaveDirectoryPicker = bindings.save.onOpenSaveDirectoryPicker,
                    onFallbackToManualInput = bindings.save.onFallbackToManualInput
                )
            }
            item {
                GlobalSettingsCacheSection(cacheCallbacks = bindings.cacheCallbacks)
            }
            item {
                GlobalSettingsStorageSection(
                    storageSummaryState = bindings.storage.storageSummaryState,
                    onRefreshStorageStats = bindings.storage.onRefreshStorageStats
                )
            }
            item {
                GlobalSettingsLinksSection(
                    settingsEntries = bindings.links.settingsEntries,
                    linkCallbacks = bindings.links.linkCallbacks
                )
            }
            item {
                GlobalSettingsAppInfoSection(appVersion = bindings.appVersion)
            }
        }
    }
}

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
internal fun SettingsSection(
    title: String,
    icon: ImageVector,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            content()
        }
    }
}

@Composable
internal fun GlobalSettingsBehaviorSection(
    isBackgroundRefreshEnabled: Boolean,
    onBackgroundRefreshChanged: (Boolean) -> Unit,
    isLightweightModeEnabled: Boolean,
    onLightweightModeChanged: (Boolean) -> Unit
) {
    SettingsSection(
        title = "動作",
        icon = Icons.Rounded.WatchLater,
        description = "履歴更新と軽量化の挙動をまとめています。"
    ) {
        ListItem(
            headlineContent = { Text("バックグラウンド更新 (15分)") },
            supportingContent = {
                Text(
                    text = "アプリ起動中は15分ごとに履歴を更新します(通知あり)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingContent = {
                Switch(
                    checked = isBackgroundRefreshEnabled,
                    onCheckedChange = { onBackgroundRefreshChanged(it) }
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("軽量モード") },
            supportingContent = {
                Text(
                    text = "画像キャッシュを小さくし、並列ダウンロードや履歴更新の同時実行数を抑えます。低スペック端末では自動でONになります。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingContent = {
                Switch(
                    checked = isLightweightModeEnabled,
                    onCheckedChange = { onLightweightModeChanged(it) }
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun GlobalSettingsCatalogMenuSection(
    localCatalogNavEntries: List<CatalogNavEntryConfig>,
    catalogMenuCallbacks: GlobalSettingsCatalogMenuCallbacks
) {
    val catalogMenuState = remember(localCatalogNavEntries) {
        resolveCatalogMenuConfigState(localCatalogNavEntries)
    }
    SettingsSection(
        title = "カタログメニュー構成",
        icon = Icons.Rounded.ViewModule,
        description = "カタログ下部バーの並びを編集できます。"
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "表示するボタンと順序をカスタマイズできます。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = catalogMenuCallbacks.resetEntries) {
                Text("リセット")
            }
        }
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("バー:")
            if (catalogMenuState.barEntries.isEmpty()) {
                Text("なし", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            } else {
                catalogMenuState.barEntries.forEach { entry ->
                    val meta = entry.id.toMeta()
                    Icon(imageVector = meta.icon, contentDescription = meta.label, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        if (catalogMenuState.hiddenEntries.isNotEmpty()) {
            Text(
                text = "非表示: ${catalogMenuState.hiddenEntries.size} 件",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        catalogMenuState.allEntries.forEach { item ->
            val meta = item.id.toMeta()
            val barIndex = catalogMenuState.barEntries.indexOfFirst { it.id == item.id }
            val canMoveLeft = item.placement == CatalogNavEntryPlacement.BAR && barIndex > 0
            val canMoveRight =
                item.placement == CatalogNavEntryPlacement.BAR &&
                    barIndex in 0 until catalogMenuState.barEntries.lastIndex
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = meta.icon,
                            contentDescription = meta.label,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(meta.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { catalogMenuCallbacks.moveEntry(item.id, -1) },
                        enabled = canMoveLeft
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "左へ移動")
                    }
                    IconButton(
                        onClick = { catalogMenuCallbacks.moveEntry(item.id, 1) },
                        enabled = canMoveRight
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "右へ移動")
                    }
                    AssistChip(
                        onClick = { catalogMenuCallbacks.setPlacement(item.id, CatalogNavEntryPlacement.BAR) },
                        label = { Text("バー") },
                        leadingIcon = if (item.placement == CatalogNavEntryPlacement.BAR) {
                            { Icon(Icons.Rounded.Check, contentDescription = null) }
                        } else null,
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (item.placement == CatalogNavEntryPlacement.BAR) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                    AssistChip(
                        onClick = { catalogMenuCallbacks.setPlacement(item.id, CatalogNavEntryPlacement.HIDDEN) },
                        label = { Text("非表示") },
                        leadingIcon = if (item.placement == CatalogNavEntryPlacement.HIDDEN) {
                            { Icon(Icons.Rounded.Check, contentDescription = null) }
                        } else null,
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (item.placement == CatalogNavEntryPlacement.HIDDEN) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }
            HorizontalDivider()
        }
    }
}

@Composable
internal fun GlobalSettingsThreadMenuSection(
    localThreadMenuEntries: List<ThreadMenuEntryConfig>,
    threadMenuCallbacks: GlobalSettingsThreadMenuCallbacks
) {
    val threadMenuState = remember(localThreadMenuEntries) {
        resolveThreadMenuConfigState(localThreadMenuEntries)
    }
    SettingsSection(
        title = "スレッドメニュー構成",
        icon = Icons.AutoMirrored.Rounded.ViewList,
        description = "下部バーと設定シートの並びを見やすく配置できます。"
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "アクションの表示位置を編集できます。バーにもシートにも最低1つは置いてください。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = threadMenuCallbacks.resetEntries) {
                Text("リセット")
            }
        }
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("バー:")
            if (threadMenuState.barEntries.isEmpty()) {
                Text("なし", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            } else {
                threadMenuState.barEntries.forEach { entry ->
                    val meta = entry.toMeta()
                    Icon(imageVector = meta.icon, contentDescription = meta.label, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("設定:")
            if (threadMenuState.sheetEntries.isEmpty()) {
                Text("なし", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            } else {
                threadMenuState.sheetEntries.forEach { entry ->
                    val meta = entry.toMeta()
                    Icon(imageVector = meta.icon, contentDescription = meta.label, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        threadMenuState.allEntries.forEach { item ->
            val meta = item.toMeta()
            val placement = item.placement
            val barIndex = threadMenuState.barEntries.indexOfFirst { it.id == item.id }
            val sheetIndex = threadMenuState.sheetEntries.indexOfFirst { it.id == item.id }
            val canMoveLeft = placement == ThreadMenuEntryPlacement.BAR && barIndex > 0 ||
                placement == ThreadMenuEntryPlacement.SHEET && sheetIndex > 0
            val canMoveRight =
                placement == ThreadMenuEntryPlacement.BAR && barIndex in 0 until threadMenuState.barEntries.lastIndex ||
                    placement == ThreadMenuEntryPlacement.SHEET && sheetIndex in 0 until threadMenuState.sheetEntries.lastIndex
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = meta.icon, contentDescription = meta.label)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(meta.label, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = when (placement) {
                                ThreadMenuEntryPlacement.BAR -> "下部バーに表示"
                                ThreadMenuEntryPlacement.SHEET -> "設定シートに表示"
                                ThreadMenuEntryPlacement.HIDDEN -> "非表示"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = {
                            when (placement) {
                                ThreadMenuEntryPlacement.BAR -> threadMenuCallbacks.moveWithinPlacement(item.id, -1, ThreadMenuEntryPlacement.BAR)
                                ThreadMenuEntryPlacement.SHEET -> threadMenuCallbacks.moveWithinPlacement(item.id, -1, ThreadMenuEntryPlacement.SHEET)
                                else -> {}
                            }
                        },
                        enabled = canMoveLeft
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "左へ移動")
                    }
                    IconButton(
                        onClick = {
                            when (placement) {
                                ThreadMenuEntryPlacement.BAR -> threadMenuCallbacks.moveWithinPlacement(item.id, 1, ThreadMenuEntryPlacement.BAR)
                                ThreadMenuEntryPlacement.SHEET -> threadMenuCallbacks.moveWithinPlacement(item.id, 1, ThreadMenuEntryPlacement.SHEET)
                                else -> {}
                            }
                        },
                        enabled = canMoveRight
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "右へ移動")
                    }
                    AssistChip(
                        onClick = { threadMenuCallbacks.setPlacement(item.id, ThreadMenuEntryPlacement.BAR) },
                        label = { Text("バー") },
                        leadingIcon = if (placement == ThreadMenuEntryPlacement.BAR) {
                            { Icon(Icons.Rounded.Check, contentDescription = null) }
                        } else null,
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (placement == ThreadMenuEntryPlacement.BAR) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                    AssistChip(
                        onClick = { threadMenuCallbacks.setPlacement(item.id, ThreadMenuEntryPlacement.SHEET) },
                        label = { Text("設定") },
                        leadingIcon = if (placement == ThreadMenuEntryPlacement.SHEET) {
                            { Icon(Icons.Rounded.Check, contentDescription = null) }
                        } else null,
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (placement == ThreadMenuEntryPlacement.SHEET) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                    AssistChip(
                        onClick = { threadMenuCallbacks.setPlacement(item.id, ThreadMenuEntryPlacement.HIDDEN) },
                        label = { Text("非表示") },
                        leadingIcon = if (placement == ThreadMenuEntryPlacement.HIDDEN) {
                            { Icon(Icons.Rounded.Check, contentDescription = null) }
                        } else null,
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (placement == ThreadMenuEntryPlacement.HIDDEN) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }
            HorizontalDivider()
        }
        if (threadMenuState.hiddenEntries.isNotEmpty()) {
            Text(
                text = "非表示: ${threadMenuState.hiddenEntries.size} 件",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
                        text = "Documents/futacha 配下がデフォルトです。「Documents」か「Download」と入力すると futacha/saved_threads まで自動指定します。絶対パス指定もできます。",
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
                leadingContent = { Icon(imageVector = entry.icon, contentDescription = null) },
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
