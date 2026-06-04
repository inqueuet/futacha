package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.model.AppIconVariant
import com.valoser.futacha.shared.model.ThemeMode
import com.valoser.futacha.shared.model.ThemePalette
import com.valoser.futacha.shared.model.ThreadDisplayMode
import com.valoser.futacha.shared.model.ThreadGalleryTapAction
import com.valoser.futacha.shared.ai.AiAvailability

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
                    text = bindings.behavior.text,
                    isBackgroundRefreshEnabled = bindings.behavior.isBackgroundRefreshEnabled,
                    onBackgroundRefreshChanged = bindings.behavior.onBackgroundRefreshChanged,
                    isAdsEnabled = bindings.behavior.isAdsEnabled,
                    onAdsEnabledChanged = bindings.behavior.onAdsEnabledChanged,
                    isLightweightModeEnabled = bindings.behavior.isLightweightModeEnabled,
                    onLightweightModeChanged = bindings.behavior.onLightweightModeChanged,
                    isThreadSummaryModeEnabled = bindings.behavior.isThreadSummaryModeEnabled,
                    onThreadSummaryModeChanged = bindings.behavior.onThreadSummaryModeChanged,
                    isAiPostFilterEnabled = bindings.behavior.isAiPostFilterEnabled,
                    onAiPostFilterChanged = bindings.behavior.onAiPostFilterChanged,
                    isAiCommandEnabled = bindings.behavior.isAiCommandEnabled,
                    onAiCommandChanged = bindings.behavior.onAiCommandChanged,
                    aiAvailability = bindings.behavior.aiAvailability,
                    threadGalleryTapAction = bindings.behavior.threadGalleryTapAction,
                    onThreadGalleryTapActionChanged = bindings.behavior.onThreadGalleryTapActionChanged,
                    themeMode = bindings.behavior.themeMode,
                    onThemeModeChanged = bindings.behavior.onThemeModeChanged,
                    themePalette = bindings.behavior.themePalette,
                    onThemePaletteChanged = bindings.behavior.onThemePaletteChanged,
                    appIconVariant = bindings.behavior.appIconVariant,
                    onAppIconVariantChanged = bindings.behavior.onAppIconVariantChanged,
                    threadDisplayMode = bindings.behavior.threadDisplayMode,
                    onThreadDisplayModeChanged = bindings.behavior.onThreadDisplayModeChanged
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
                    bindings = bindings.save
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
    text: GlobalSettingsBehaviorText,
    isBackgroundRefreshEnabled: Boolean,
    onBackgroundRefreshChanged: (Boolean) -> Unit,
    isAdsEnabled: Boolean,
    onAdsEnabledChanged: (Boolean) -> Unit,
    isLightweightModeEnabled: Boolean,
    onLightweightModeChanged: (Boolean) -> Unit,
    isThreadSummaryModeEnabled: Boolean,
    onThreadSummaryModeChanged: (Boolean) -> Unit,
    isAiPostFilterEnabled: Boolean,
    onAiPostFilterChanged: (Boolean) -> Unit,
    isAiCommandEnabled: Boolean,
    onAiCommandChanged: (Boolean) -> Unit,
    aiAvailability: AiAvailability,
    threadGalleryTapAction: ThreadGalleryTapAction,
    onThreadGalleryTapActionChanged: (ThreadGalleryTapAction) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChanged: (ThemeMode) -> Unit,
    themePalette: ThemePalette,
    onThemePaletteChanged: (ThemePalette) -> Unit,
    appIconVariant: AppIconVariant,
    onAppIconVariantChanged: (AppIconVariant) -> Unit,
    threadDisplayMode: ThreadDisplayMode,
    onThreadDisplayModeChanged: (ThreadDisplayMode) -> Unit
) {
    SettingsSection(
        title = "動作",
        icon = Icons.Rounded.Palette,
        description = "テーマ、アイコン、表示モードと基本動作をまとめています。"
    ) {
        ListItem(
            headlineContent = { Text("テーマモード") },
            supportingContent = {
                Text(
                    text = "ライト / ダークを固定するか、端末設定に合わせます。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        ThemeMode.entries.forEach { mode ->
            GlobalSettingsRadioOptionRow(
                label = mode.label,
                description = when (mode) {
                    ThemeMode.System -> "端末のライト / ダーク設定に追従します。"
                    ThemeMode.Light -> "常にライトテーマで表示します。"
                    ThemeMode.Dark -> "常にダークテーマで表示します。"
                },
                selected = themeMode == mode,
                onClick = { onThemeModeChanged(mode) }
            )
        }
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("テーマ種類") },
            supportingContent = {
                Text(
                    text = "ふたちゃテーマを標準に、ふたばクラシックとミッドナイトを選べます。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        ThemePalette.entries.forEach { palette ->
            GlobalSettingsRadioOptionRow(
                label = palette.label,
                description = when (palette) {
                    ThemePalette.Current -> "ふたちゃ標準の配色です。"
                    ThemePalette.FutabaClassic -> "生成りとえんじを基調にした、ふたば寄りの配色です。"
                    ThemePalette.Midnight -> "暗所向けの高コントラスト配色です。"
                },
                selected = themePalette == palette,
                onClick = { onThemePaletteChanged(palette) }
            )
        }
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("アプリアイコン") },
            supportingContent = {
                Text(
                    text = resolveAppIconSectionDescription(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        listOf(AppIconVariant.Current, AppIconVariant.Classic).forEach { variant ->
            AppIconVariantOptionCard(
                variant = variant,
                selected = appIconVariant == variant,
                onClick = { onAppIconVariantChanged(variant) }
            )
        }
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("スレ表示モード") },
            supportingContent = {
                Text(
                    text = "現行の通常表示か、引用関係から組み立てたツリー表示を選べます。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        ThreadDisplayMode.entries.forEach { mode ->
            GlobalSettingsRadioOptionRow(
                label = mode.label,
                description = when (mode) {
                    ThreadDisplayMode.Flat -> "今までどおり時系列順で表示します。"
                    ThreadDisplayMode.Tree -> "引用先を親にしてインデント付きで表示します。"
                },
                selected = threadDisplayMode == mode,
                onClick = { onThreadDisplayModeChanged(mode) }
            )
        }
        HorizontalDivider()
        GlobalSettingsAiControls(
            aiAvailability = aiAvailability,
            isThreadSummaryModeEnabled = isThreadSummaryModeEnabled,
            onThreadSummaryModeChanged = onThreadSummaryModeChanged,
            isAiPostFilterEnabled = isAiPostFilterEnabled,
            onAiPostFilterChanged = onAiPostFilterChanged,
            isAiCommandEnabled = isAiCommandEnabled,
            onAiCommandChanged = onAiCommandChanged
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("バックグラウンド更新") },
            supportingContent = {
                Text(
                    text = text.backgroundRefreshDescription,
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
            headlineContent = { Text("添付一覧のタップ動作") },
            supportingContent = {
                Text(
                    text = "添付一覧でカードをタップしたときの既定動作です。長押しすると添付メニューを開き、No.表示からはいつでもレスへ移動できます。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        GalleryTapActionOptionRow(
            label = "添付を開く",
            description = "画像や動画のプレビューを優先して開きます。",
            selected = threadGalleryTapAction == ThreadGalleryTapAction.OpenMedia,
            onClick = { onThreadGalleryTapActionChanged(ThreadGalleryTapAction.OpenMedia) }
        )
        GalleryTapActionOptionRow(
            label = "レスに移動",
            description = "対象レスまでスクロールします。",
            selected = threadGalleryTapAction == ThreadGalleryTapAction.JumpToPost,
            onClick = { onThreadGalleryTapActionChanged(ThreadGalleryTapAction.JumpToPost) }
        )
    }
}

@Composable
private fun GlobalSettingsAiControls(
    aiAvailability: AiAvailability,
    isThreadSummaryModeEnabled: Boolean,
    onThreadSummaryModeChanged: (Boolean) -> Unit,
    isAiPostFilterEnabled: Boolean,
    onAiPostFilterChanged: (Boolean) -> Unit,
    isAiCommandEnabled: Boolean,
    onAiCommandChanged: (Boolean) -> Unit
) {
    val summaryEnabled = isThreadSummaryFeatureAvailable(aiAvailability)
    val postFilterEnabled = isAiPostFilterFeatureAvailable(aiAvailability)
    ListItem(
        leadingContent = {
            Icon(
                imageVector = Icons.Rounded.Psychology,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        headlineContent = { Text("端末AI") },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = aiAvailability.unavailableReason
                        ?: "${aiAvailability.providerLabel} を使って端末内で処理します。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (aiAvailability.isDownloadInProgress) {
                    val progress = aiAvailability.downloadProgress
                    if (progress == null) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
    ListItem(
        headlineContent = { Text("端末内処理") },
        supportingContent = {
            Text(
                text = aiLocalProcessingDescription(aiAvailability.providerLabel),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        modifier = Modifier.fillMaxWidth()
    )
    ListItem(
        headlineContent = { Text("AIアプリ操作（アルファ版）") },
        supportingContent = {
            Text(
                text = aiCommandSettingDescription(aiAvailability, isAiCommandEnabled),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Switch(
                checked = isAiCommandEnabled && ALPHA_AI_COMMAND_ENABLED,
                enabled = ALPHA_AI_COMMAND_ENABLED,
                onCheckedChange = {
                    if (ALPHA_AI_COMMAND_ENABLED) {
                        onAiCommandChanged(it)
                    }
                }
            )
        },
        modifier = Modifier.fillMaxWidth()
    )
    ListItem(
        headlineContent = { Text("スレ要約モード") },
        supportingContent = {
            Text(
                text = threadSummarySettingDescription(aiAvailability),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Switch(
                checked = isThreadSummaryModeEnabled && summaryEnabled,
                enabled = summaryEnabled,
                onCheckedChange = onThreadSummaryModeChanged
            )
        },
        modifier = Modifier.fillMaxWidth()
    )
    ListItem(
        headlineContent = { Text("荒らし非表示モード（アルファ版）") },
        supportingContent = {
            Text(
                text = aiPostFilterSettingDescription(aiAvailability),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Switch(
                checked = isAiPostFilterEnabled && postFilterEnabled && ALPHA_AI_POST_FILTER_ENABLED,
                enabled = postFilterEnabled && ALPHA_AI_POST_FILTER_ENABLED,
                onCheckedChange = {
                    if (ALPHA_AI_POST_FILTER_ENABLED) {
                        onAiPostFilterChanged(it)
                    }
                }
            )
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun GlobalSettingsRadioOptionRow(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            RadioButton(
                selected = selected,
                onClick = null
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    )
}

@Composable
private fun AppIconVariantOptionCard(
    variant: AppIconVariant,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        tonalElevation = if (selected) 4.dp else 0.dp,
        shadowElevation = if (selected) 2.dp else 0.dp,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(0.32f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(18.dp))
            ) {
                AppIconVariantPreview(
                    variant = variant,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column(
                modifier = Modifier.weight(0.68f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = variant.label,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = resolveAppIconVariantDescription(variant),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            RadioButton(
                selected = selected,
                onClick = null
            )
        }
    }
}

@Composable
private fun GalleryTapActionOptionRow(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    GlobalSettingsRadioOptionRow(
        label = label,
        description = description,
        selected = selected,
        onClick = onClick
    )
}
