package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.WatchLater
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

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
                    isAdsEnabled = bindings.behavior.isAdsEnabled,
                    onAdsEnabledChanged = bindings.behavior.onAdsEnabledChanged,
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
                    defaultAndroidSaveWarningText = bindings.save.defaultAndroidSaveWarningText,
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
    isAdsEnabled: Boolean,
    onAdsEnabledChanged: (Boolean) -> Unit,
    isLightweightModeEnabled: Boolean,
    onLightweightModeChanged: (Boolean) -> Unit
) {
    SettingsSection(
        title = "動作",
        icon = Icons.Rounded.WatchLater,
        description = "履歴更新と軽量化の挙動をまとめています。"
    ) {
        ListItem(
            headlineContent = { Text("バックグラウンド更新") },
            supportingContent = {
                Text(
                    text = "Android は最短15分間隔の定期更新、iOS は OS 管理の間欠実行です。どちらも実際の実行時刻は端末状態や OS により前後します。",
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
            headlineContent = { Text("広告表示") },
            supportingContent = {
                Text(
                    text = "スレッド画面の下部メニューの下に AdMob バナーを表示します。OFF にすると広告枠ごと消えます。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingContent = {
                Switch(
                    checked = isAdsEnabled,
                    onCheckedChange = { onAdsEnabledChanged(it) }
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
