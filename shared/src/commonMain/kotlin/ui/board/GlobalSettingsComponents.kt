package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonColors
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.ai.AiAvailability
import com.valoser.futacha.shared.analytics.AnalyticsTracker
import com.valoser.futacha.shared.billing.SupportProduct
import com.valoser.futacha.shared.billing.SupportPurchaseResult
import com.valoser.futacha.shared.billing.rememberSupportPurchaseClient
import com.valoser.futacha.shared.model.CATALOG_FETCH_ROW_OPTIONS
import com.valoser.futacha.shared.model.AppIconVariant
import com.valoser.futacha.shared.model.DEFAULT_CATALOG_FETCH_COLUMNS
import com.valoser.futacha.shared.model.ThemeMode
import com.valoser.futacha.shared.model.ThemePalette
import com.valoser.futacha.shared.model.ThreadBodyTextSize
import com.valoser.futacha.shared.model.ThreadDisplayMode
import com.valoser.futacha.shared.model.ThreadGalleryTapAction
import com.valoser.futacha.shared.model.ThreadGalleryThumbnailMode
import com.valoser.futacha.shared.model.ThreadPostImageSize
import com.valoser.futacha.shared.state.APP_LOCK_PASSWORD_MIN_LENGTH
import com.valoser.futacha.shared.state.APP_LOCK_PASSWORD_MAX_LENGTH
import com.valoser.futacha.shared.state.isValidAppLockPassword
import com.valoser.futacha.shared.ui.theme.LocalFutachaChromeColors
import com.valoser.futacha.shared.ui.theme.LocalFutachaThemePalette
import kotlinx.coroutines.launch

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

private fun isPrivacyOrSecuritySettingsEntry(entry: GlobalSettingsEntry): Boolean {
    return entry.action == GlobalSettingsAction.Cookies ||
        entry.action == GlobalSettingsAction.PrivacyPolicy
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GlobalSettingsScaffold(
    bindings: GlobalSettingsScaffoldBindings,
    modifier: Modifier = Modifier
) {
    val chromeColors = LocalFutachaChromeColors.current
    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("設定") },
                navigationIcon = {
                    IconButton(onClick = {
                        AnalyticsTracker.uiControl("global_settings", "共通設定を閉じる")
                        bindings.onBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "戻る"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = chromeColors.topBar,
                    titleContentColor = chromeColors.onBar,
                    navigationIconContentColor = chromeColors.onBar,
                    actionIconContentColor = chromeColors.onBar
                )
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
                GlobalSettingsModeSection()
            }
            item {
                GlobalSettingsDisplaySection(
                    themeMode = bindings.behavior.themeMode,
                    onThemeModeChanged = bindings.behavior.onThemeModeChanged,
                    themePalette = bindings.behavior.themePalette,
                    onThemePaletteChanged = bindings.behavior.onThemePaletteChanged,
                    threadBodyTextSize = bindings.behavior.threadBodyTextSize,
                    onThreadBodyTextSizeChanged = bindings.behavior.onThreadBodyTextSizeChanged,
                    threadPostImageSize = bindings.behavior.threadPostImageSize,
                    onThreadPostImageSizeChanged = bindings.behavior.onThreadPostImageSizeChanged,
                    isCompactThreadHeaderEnabled = bindings.behavior.isCompactThreadHeaderEnabled,
                    onCompactThreadHeaderChanged = bindings.behavior.onCompactThreadHeaderChanged,
                    threadDisplayMode = bindings.behavior.threadDisplayMode,
                    onThreadDisplayModeChanged = bindings.behavior.onThreadDisplayModeChanged,
                    appIconVariant = bindings.behavior.appIconVariant,
                    onAppIconVariantChanged = bindings.behavior.onAppIconVariantChanged
                )
            }
            item {
                GlobalSettingsThreadInteractionSection(
                    threadGalleryTapAction = bindings.behavior.threadGalleryTapAction,
                    onThreadGalleryTapActionChanged = bindings.behavior.onThreadGalleryTapActionChanged,
                    threadGalleryThumbnailMode = bindings.behavior.threadGalleryThumbnailMode,
                    onThreadGalleryThumbnailModeChanged = bindings.behavior.onThreadGalleryThumbnailModeChanged
                )
            }
            item {
                GlobalSettingsThreadMenuSection(
                    localThreadMenuEntries = bindings.threadMenu.localThreadMenuEntries,
                    threadMenuCallbacks = bindings.threadMenu.threadMenuCallbacks
                )
            }
            item {
                GlobalSettingsCatalogFetchSection(
                    catalogFetchRows = bindings.behavior.catalogFetchRows,
                    onCatalogFetchRowsChanged = bindings.behavior.onCatalogFetchRowsChanged
                )
            }
            item {
                GlobalSettingsCatalogMenuSection(
                    localCatalogNavEntries = bindings.catalogMenu.localCatalogNavEntries,
                    catalogMenuCallbacks = bindings.catalogMenu.catalogMenuCallbacks
                )
            }
            item {
                GlobalSettingsSaveSection(
                    bindings = bindings.save
                )
            }
            item {
                GlobalSettingsStorageSection(
                    storageSummaryState = bindings.storage.storageSummaryState,
                    onRefreshStorageStats = bindings.storage.onRefreshStorageStats
                )
            }
            item {
                GlobalSettingsCacheSection(cacheCallbacks = bindings.cacheCallbacks)
            }
            item {
                GlobalSettingsBackgroundSection(
                    text = bindings.behavior.text,
                    isUpdateCheckEnabled = bindings.behavior.isUpdateCheckEnabled,
                    onUpdateCheckChanged = bindings.behavior.onUpdateCheckChanged,
                    isBackgroundRefreshEnabled = bindings.behavior.isBackgroundRefreshEnabled,
                    onBackgroundRefreshChanged = bindings.behavior.onBackgroundRefreshChanged,
                    isWatchAlertEnabled = bindings.behavior.isWatchAlertEnabled,
                    onWatchAlertChanged = bindings.behavior.onWatchAlertChanged,
                    isLightweightModeEnabled = bindings.behavior.isLightweightModeEnabled,
                    onLightweightModeChanged = bindings.behavior.onLightweightModeChanged
                )
            }
            item {
                GlobalSettingsAiSection(
                    aiAvailability = bindings.behavior.aiAvailability,
                    isThreadSummaryModeEnabled = bindings.behavior.isThreadSummaryModeEnabled,
                    onThreadSummaryModeChanged = bindings.behavior.onThreadSummaryModeChanged,
                    isAiPostFilterEnabled = bindings.behavior.isAiPostFilterEnabled,
                    onAiPostFilterChanged = bindings.behavior.onAiPostFilterChanged,
                    isAiCommandEnabled = bindings.behavior.isAiCommandEnabled,
                    onAiCommandChanged = bindings.behavior.onAiCommandChanged
                )
            }
            item {
                GlobalSettingsSecuritySection(
                    isAppLockEnabled = bindings.behavior.isAppLockEnabled,
                    onAppLockPasswordChanged = bindings.behavior.onAppLockPasswordChanged,
                    onAppLockCleared = bindings.behavior.onAppLockCleared,
                    settingsEntries = bindings.links.settingsEntries.filter(::isPrivacyOrSecuritySettingsEntry),
                    linkCallbacks = bindings.links.linkCallbacks
                )
            }
            item {
                GlobalSettingsLinksSection(
                    title = "情報・サポート",
                    settingsEntries = bindings.links.settingsEntries.filterNot(::isPrivacyOrSecuritySettingsEntry),
                    linkCallbacks = bindings.links.linkCallbacks
                )
            }
            item {
                GlobalSettingsSupportPurchaseSection(
                    snackbarHostState = bindings.snackbarHostState,
                    isAndroidPlatform = bindings.isAndroidPlatform
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
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var isExpanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }

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
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        isExpanded = !isExpanded
                        AnalyticsTracker.uiControl(
                            "settings_section",
                            "$title を${if (isExpanded) "開く" else "閉じる"}"
                        )
                    },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (description != null) {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (isExpanded) "閉じる" else "開く",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(visible = isExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
internal fun GlobalSettingsCatalogFetchSection(
    catalogFetchRows: Int,
    onCatalogFetchRowsChanged: (Int) -> Unit
) {
    SettingsSection(
        title = "カタログ取得",
        icon = Icons.Rounded.History,
        description = "カタログ更新時に Futaba へ要求する取得量を変更します。"
    ) {
        ListItem(
            headlineContent = { Text("取得量") },
            supportingContent = {
                Text(
                    text = "監視ワードも取得できたカタログ内から探します。増やすと拾える範囲が広がりますが、通信量と更新時間も増えます。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        CATALOG_FETCH_ROW_OPTIONS.forEach { rows ->
            GlobalSettingsRadioOptionRow(
                label = "約${DEFAULT_CATALOG_FETCH_COLUMNS * rows}スレ",
                description = if (rows == catalogFetchRows) {
                    "現在の設定です。次回のカタログ取得から反映します。"
                } else {
                    "Futaba のカタログ設定を ${DEFAULT_CATALOG_FETCH_COLUMNS}列 x ${rows}行で初期化します。"
                },
                selected = catalogFetchRows == rows,
                onClick = { onCatalogFetchRowsChanged(rows) }
            )
        }
    }
}

@Composable
internal fun GlobalSettingsDisplaySection(
    themeMode: ThemeMode,
    onThemeModeChanged: (ThemeMode) -> Unit,
    themePalette: ThemePalette,
    onThemePaletteChanged: (ThemePalette) -> Unit,
    threadBodyTextSize: ThreadBodyTextSize,
    onThreadBodyTextSizeChanged: (ThreadBodyTextSize) -> Unit,
    threadPostImageSize: ThreadPostImageSize,
    onThreadPostImageSizeChanged: (ThreadPostImageSize) -> Unit,
    isCompactThreadHeaderEnabled: Boolean,
    onCompactThreadHeaderChanged: (Boolean) -> Unit,
    threadDisplayMode: ThreadDisplayMode,
    onThreadDisplayModeChanged: (ThreadDisplayMode) -> Unit,
    appIconVariant: AppIconVariant,
    onAppIconVariantChanged: (AppIconVariant) -> Unit
) {
    SettingsSection(
        title = "表示",
        icon = Icons.Rounded.Palette,
        description = "テーマ、文字、画像、スレッドの見え方をまとめています。"
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
                    text = "ふたちゃテーマを標準に、ふたばクラシック、ふたばブラック、ミッドナイトを選べます。",
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
                    ThemePalette.FutabaBlack -> "上下バーとシステムバーを黒系で統一し、本文側はふたば寄りの読みやすさを残します。"
                    ThemePalette.Midnight -> "暗所向けの高コントラスト配色です。"
                },
                selected = themePalette == palette,
                onClick = { onThemePaletteChanged(palette) }
            )
        }
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("文字サイズ") },
            supportingContent = {
                Text(
                    text = "板画面、設定画面、履歴、レス、投稿フォームなどアプリ全体の文字サイズを変更します。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        ThreadBodyTextSize.entries.forEach { size ->
            GlobalSettingsRadioOptionRow(
                label = size.label,
                description = when (size) {
                    ThreadBodyTextSize.Small -> "情報量を優先してアプリ内の文字を少し小さく表示します。"
                    ThreadBodyTextSize.Standard -> "標準の文字サイズで表示します。"
                    ThreadBodyTextSize.Large -> "読みやすさを優先してアプリ内の文字を少し大きく表示します。"
                    ThreadBodyTextSize.ExtraLarge -> "アプリ内の文字を大きく表示します。"
                },
                selected = threadBodyTextSize == size,
                onClick = { onThreadBodyTextSizeChanged(size) }
            )
        }
        Text(
            text = "カタログ画面はカード崩れを避けるため、この設定ではなく列数に合わせて文字の見え方が自動で変わります。カタログを見やすくしたい場合は表示切替の列数を調整してください。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("コンパクトヘッダー") },
            supportingContent = {
                Text(
                    text = "スレ上部とレスのヘッダー情報を1行寄せで表示し、縦の移動量を減らします。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingContent = {
                Switch(
                    checked = isCompactThreadHeaderEnabled,
                    onCheckedChange = {
                        AnalyticsTracker.uiControl(
                            "global_setting_toggle",
                            "コンパクトヘッダーを${if (it) "ON" else "OFF"}にする",
                            mapOf("setting_label" to "コンパクトヘッダー", "setting_value" to if (it) "ON" else "OFF")
                        )
                        onCompactThreadHeaderChanged(it)
                    }
                )
            },
            modifier = Modifier
                .fillMaxWidth()
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("レス画像サイズ") },
            supportingContent = {
                Text(
                    text = "スレ本文内に表示する添付画像の最大高さを変更します。タップ後の画像プレビューは影響しません。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        ThreadPostImageSize.entries.forEach { size ->
            GlobalSettingsRadioOptionRow(
                label = size.label,
                description = when (size) {
                    ThreadPostImageSize.ExtraSmall -> "最大 120px 相当で表示します。"
                    ThreadPostImageSize.Small -> "最大 200px 相当で表示します。"
                    ThreadPostImageSize.Medium -> "最大 320px 相当で表示します。"
                    ThreadPostImageSize.Large -> "最大 480px 相当で表示します。"
                },
                selected = threadPostImageSize == size,
                onClick = { onThreadPostImageSizeChanged(size) }
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
    }
}

@Composable
internal fun GlobalSettingsBackgroundSection(
    text: GlobalSettingsBehaviorText,
    isUpdateCheckEnabled: Boolean,
    onUpdateCheckChanged: (Boolean) -> Unit,
    isBackgroundRefreshEnabled: Boolean,
    onBackgroundRefreshChanged: (Boolean) -> Unit,
    isWatchAlertEnabled: Boolean,
    onWatchAlertChanged: (Boolean) -> Unit,
    isLightweightModeEnabled: Boolean,
    onLightweightModeChanged: (Boolean) -> Unit
) {
    SettingsSection(
        title = "バックグラウンド・通信",
        icon = Icons.Rounded.History,
        description = "自動更新、通信量、匿名の品質改善データに関わる動作をまとめています。"
    ) {
        ListItem(
            headlineContent = { Text("アップデート確認") },
            supportingContent = {
                Text(
                    text = "OFFでは通常の更新案内を停止します。公開から7日以上、またはAndroidでGoogle Playの重要度4以上の緊急更新はOFFでも表示します。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingContent = {
                Switch(
                    checked = isUpdateCheckEnabled,
                    onCheckedChange = {
                        AnalyticsTracker.uiControl(
                            "global_setting_toggle",
                            "アップデート確認を${if (it) "ON" else "OFF"}にする",
                            mapOf("setting_label" to "アップデート確認", "setting_value" to if (it) "ON" else "OFF")
                        )
                        onUpdateCheckChanged(it)
                    }
                )
            },
            modifier = Modifier.fillMaxWidth()
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
                    onCheckedChange = {
                        AnalyticsTracker.uiControl(
                            "global_setting_toggle",
                            "バックグラウンド更新を${if (it) "ON" else "OFF"}にする",
                            mapOf("setting_label" to "バックグラウンド更新", "setting_value" to if (it) "ON" else "OFF")
                        )
                        onBackgroundRefreshChanged(it)
                    }
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("監視ワード自動アラート") },
            supportingContent = {
                Text(
                    text = "ONにすると定期的に登録板のカタログを確認し、板ごとの監視ワードに一致した新着スレを通知します。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingContent = {
                Switch(
                    checked = isWatchAlertEnabled,
                    onCheckedChange = {
                        AnalyticsTracker.uiControl(
                            "global_setting_toggle",
                            "監視ワード自動アラートを${if (it) "ON" else "OFF"}にする",
                            mapOf("setting_label" to "監視ワード自動アラート", "setting_value" to if (it) "ON" else "OFF")
                        )
                        onWatchAlertChanged(it)
                    }
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
                    onCheckedChange = {
                        AnalyticsTracker.uiControl(
                            "global_setting_toggle",
                            "軽量モードを${if (it) "ON" else "OFF"}にする",
                            mapOf("setting_label" to "軽量モード", "setting_value" to if (it) "ON" else "OFF")
                        )
                        onLightweightModeChanged(it)
                    }
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun GlobalSettingsThreadInteractionSection(
    threadGalleryTapAction: ThreadGalleryTapAction,
    onThreadGalleryTapActionChanged: (ThreadGalleryTapAction) -> Unit,
    threadGalleryThumbnailMode: ThreadGalleryThumbnailMode,
    onThreadGalleryThumbnailModeChanged: (ThreadGalleryThumbnailMode) -> Unit
) {
    SettingsSection(
        title = "スレ操作",
        icon = Icons.Rounded.History,
        description = "スレッド閲覧中の操作方法をまとめています。"
    ) {
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
        ListItem(
            headlineContent = { Text("画像のトリミング表示") },
            supportingContent = { Text("画像を切り抜くか、全体を収めるかを選択します。") }
        )
        GalleryTapActionOptionRow(
            label = "全体を表示",
            description = "余白を許容して画像全体を表示します。",
            selected = threadGalleryThumbnailMode == ThreadGalleryThumbnailMode.Fit,
            onClick = { onThreadGalleryThumbnailModeChanged(ThreadGalleryThumbnailMode.Fit) }
        )
        GalleryTapActionOptionRow(
            label = "正方形に切り抜く",
            description = "従来どおり正方形いっぱいに表示します。",
            selected = threadGalleryThumbnailMode == ThreadGalleryThumbnailMode.CropSquare,
            onClick = { onThreadGalleryThumbnailModeChanged(ThreadGalleryThumbnailMode.CropSquare) }
        )
    }
}

@Composable
internal fun GlobalSettingsAiSection(
    aiAvailability: AiAvailability,
    isThreadSummaryModeEnabled: Boolean,
    onThreadSummaryModeChanged: (Boolean) -> Unit,
    isAiPostFilterEnabled: Boolean,
    onAiPostFilterChanged: (Boolean) -> Unit,
    isAiCommandEnabled: Boolean,
    onAiCommandChanged: (Boolean) -> Unit
) {
    SettingsSection(
        title = "AI・補助機能",
        icon = Icons.Rounded.Psychology,
        description = "端末AIを使った要約、分類、アプリ操作をまとめています。"
    ) {
        GlobalSettingsAiControls(
            aiAvailability = aiAvailability,
            isThreadSummaryModeEnabled = isThreadSummaryModeEnabled,
            onThreadSummaryModeChanged = onThreadSummaryModeChanged,
            isAiPostFilterEnabled = isAiPostFilterEnabled,
            onAiPostFilterChanged = onAiPostFilterChanged,
            isAiCommandEnabled = isAiCommandEnabled,
            onAiCommandChanged = onAiCommandChanged
        )
    }
}

@Composable
internal fun GlobalSettingsSecuritySection(
    isAppLockEnabled: Boolean,
    onAppLockPasswordChanged: (String) -> Unit,
    onAppLockCleared: () -> Unit,
    settingsEntries: List<GlobalSettingsEntry>,
    linkCallbacks: GlobalSettingsLinkCallbacks
) {
    SettingsSection(
        title = "プライバシー・セキュリティ",
        icon = Icons.Rounded.Lock,
        description = "起動ロック、Cookie、ポリシーへの導線をまとめています。"
    ) {
        GlobalSettingsAppLockControls(
            isAppLockEnabled = isAppLockEnabled,
            onAppLockPasswordChanged = onAppLockPasswordChanged,
            onAppLockCleared = onAppLockCleared
        )
        if (settingsEntries.isNotEmpty()) {
            HorizontalDivider()
            GlobalSettingsEntryRows(
                settingsEntries = settingsEntries,
                linkCallbacks = linkCallbacks
            )
        }
    }
}

@Composable
internal fun GlobalSettingsSupportPurchaseSection(
    snackbarHostState: androidx.compose.material3.SnackbarHostState,
    isAndroidPlatform: Boolean
) {
    val purchaseClient = rememberSupportPurchaseClient()
    val coroutineScope = rememberCoroutineScope()
    var products by remember { mutableStateOf<List<SupportProduct>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isPurchasing by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    val storeName = remember(isAndroidPlatform) {
        if (isAndroidPlatform) "Google Play" else "App Store"
    }

    fun loadProducts() {
        isLoading = true
        loadError = null
        coroutineScope.launch {
            val result = purchaseClient.loadProducts()
            products = result.getOrDefault(emptyList())
            loadError = result.exceptionOrNull()?.message
            isLoading = false
        }
    }

    LaunchedEffect(purchaseClient) {
        loadProducts()
    }

    SettingsSection(
        title = "開発を応援",
        icon = Icons.Rounded.Favorite,
        description = "任意の開発支援です。支援しても機能差はありません。"
    ) {
        ListItem(
            headlineContent = { Text("$storeName 決済で応援") },
            supportingContent = {
                Text(
                    text = "ふたちゃは基本機能をすべて無償のまま提供し、広告も載せない方針です。一部機能は将来見直しや廃止の可能性がありますが、支援によって機能が増えたり制限が外れたりすることはありません。開発と保守には費用がかかるため、続けて使いたいと感じた方だけ任意で応援してください。$storeName のアプリ内課金を使います。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        when {
            isLoading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            products.isEmpty() -> {
                Text(
                    text = loadError ?: "$storeName の商品を取得できません。支援アイテムが有効か確認してください。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        AnalyticsTracker.uiControl("support_purchase", "支援商品を再読み込み")
                        loadProducts()
                    },
                    enabled = !isPurchasing
                ) {
                    Text("再読み込み")
                }
            }
            else -> products.forEach { product ->
                SupportProductButton(
                    product = product,
                    enabled = !isPurchasing,
                    onClick = {
                        AnalyticsTracker.uiControl("support_purchase", "支援購入を開始")
                        coroutineScope.launch {
                            isPurchasing = true
                            val purchaseResult = purchaseClient.purchase(product)
                            val message = when (purchaseResult) {
                                SupportPurchaseResult.Success -> "応援ありがとうございます"
                                SupportPurchaseResult.Canceled -> "購入をキャンセルしました"
                                is SupportPurchaseResult.Unavailable -> purchaseResult.message
                                is SupportPurchaseResult.Failed -> "購入に失敗しました: ${purchaseResult.message}"
                            }
                            AnalyticsTracker.uiControl(
                                "support_purchase_result",
                                when (purchaseResult) {
                                    SupportPurchaseResult.Success -> "支援購入が完了"
                                    SupportPurchaseResult.Canceled -> "支援購入をキャンセル"
                                    is SupportPurchaseResult.Unavailable -> "支援購入を利用できない"
                                    is SupportPurchaseResult.Failed -> "支援購入に失敗"
                                }
                            )
                            isPurchasing = false
                            snackbarHostState.showSnackbar(message)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SupportProductButton(
    product: SupportProduct,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Rounded.Favorite,
            contentDescription = null,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text("${product.title} ${product.formattedPrice}")
    }
    Text(
        text = product.description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
internal fun GlobalSettingsEntryRows(
    settingsEntries: List<GlobalSettingsEntry>,
    linkCallbacks: GlobalSettingsLinkCallbacks
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

@Composable
private fun GlobalSettingsAppLockControls(
    isAppLockEnabled: Boolean,
    onAppLockPasswordChanged: (String) -> Unit,
    onAppLockCleared: () -> Unit
) {
    var password by rememberSaveable(isAppLockEnabled) { mutableStateOf("") }
    var confirmation by rememberSaveable(isAppLockEnabled) { mutableStateOf("") }
    var errorMessage by rememberSaveable(isAppLockEnabled) { mutableStateOf<String?>(null) }
    val passwordInputState = rememberStableTextInputState(
        text = password,
        onTextChange = {
            password = it.take(APP_LOCK_PASSWORD_MAX_LENGTH)
            errorMessage = null
        },
        analyticsFieldLabel = "起動ロック設定パスワード"
    )
    val confirmationInputState = rememberStableTextInputState(
        text = confirmation,
        onTextChange = {
            confirmation = it.take(APP_LOCK_PASSWORD_MAX_LENGTH)
            errorMessage = null
        },
        analyticsFieldLabel = "起動ロック設定確認"
    )
    val actionLabel = if (isAppLockEnabled) "変更" else "有効にする"

    fun submitPassword() {
        AnalyticsTracker.uiControl("app_lock", "起動ロック設定を保存")
        when {
            !isValidAppLockPassword(password) -> {
                errorMessage = "${APP_LOCK_PASSWORD_MIN_LENGTH}文字以上で入力してください。"
            }
            password != confirmation -> {
                errorMessage = "確認用パスワードが一致しません。"
            }
            else -> {
                AnalyticsTracker.uiControl("app_lock", if (isAppLockEnabled) "起動ロックのパスワードを変更" else "起動ロックを有効化")
                onAppLockPasswordChanged(password)
                password = ""
                confirmation = ""
                errorMessage = null
            }
        }
    }

    ListItem(
        leadingContent = {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        headlineContent = { Text("起動ロック") },
        supportingContent = {
            Text(
                text = if (isAppLockEnabled) {
                    "有効です。アプリ起動時にパスワードを要求します。"
                } else {
                    "無効です。パスワードを設定すると起動時にロックします。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            if (isAppLockEnabled) {
                TextButton(
                    onClick = {
                        AnalyticsTracker.uiControl("app_lock", "起動ロックを解除")
                        onAppLockCleared()
                        password = ""
                        confirmation = ""
                        errorMessage = null
                    }
                ) {
                    Text("解除")
                }
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = passwordInputState.value,
        onValueChange = { nextValue ->
            val wasFilled = passwordInputState.value.text.isNotBlank()
            val isFilled = nextValue.text.isNotBlank()
            if (wasFilled != isFilled) {
                AnalyticsTracker.uiControl(
                    "app_lock_setting_field_state",
                    if (isFilled) "起動ロック用パスワードの入力を開始" else "起動ロック用パスワードを消去",
                    mapOf("field_label" to "パスワード", "input_state" to if (isFilled) "入力あり" else "空")
                )
            }
            passwordInputState.onValueChange(nextValue)
        },
        label = { Text(if (isAppLockEnabled) "新しいパスワード" else "パスワード") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = confirmationInputState.value,
        onValueChange = { nextValue ->
            val wasFilled = confirmationInputState.value.text.isNotBlank()
            val isFilled = nextValue.text.isNotBlank()
            if (wasFilled != isFilled) {
                AnalyticsTracker.uiControl(
                    "app_lock_setting_field_state",
                    if (isFilled) "起動ロック用パスワード確認の入力を開始" else "起動ロック用パスワード確認を消去",
                    mapOf("field_label" to "確認", "input_state" to if (isFilled) "入力あり" else "空")
                )
            }
            confirmationInputState.onValueChange(nextValue)
        },
        label = { Text("確認") },
        singleLine = true,
        isError = errorMessage != null,
        supportingText = errorMessage?.let { message -> { Text(message) } },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth()
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Button(
            onClick = ::submitPassword,
            enabled = password.isNotEmpty() || confirmation.isNotEmpty()
        ) {
            Text(actionLabel)
        }
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
                        AnalyticsTracker.uiControl(
                            "global_setting_toggle",
                            "AIアプリ操作を${if (it) "ON" else "OFF"}にする",
                            mapOf("setting_label" to "AIアプリ操作", "setting_value" to if (it) "ON" else "OFF")
                        )
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
                onCheckedChange = {
                    AnalyticsTracker.uiControl(
                        "global_setting_toggle",
                        "スレ要約モードを${if (it) "ON" else "OFF"}にする",
                        mapOf("setting_label" to "スレ要約モード", "setting_value" to if (it) "ON" else "OFF")
                    )
                    onThreadSummaryModeChanged(it)
                }
            )
        },
        modifier = Modifier.fillMaxWidth()
    )
    ListItem(
        headlineContent = { Text("荒らし非表示（アルファ版）") },
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
                        AnalyticsTracker.uiControl(
                            "global_setting_toggle",
                            "荒らし非表示を${if (it) "ON" else "OFF"}にする",
                            mapOf("setting_label" to "荒らし非表示", "setting_value" to if (it) "ON" else "OFF")
                        )
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
                onClick = null,
                colors = globalSettingsRadioButtonColors()
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                AnalyticsTracker.uiControl(
                    "global_setting_option",
                    "設定項目「$label」を選択",
                    mapOf("setting_value_label" to label)
                )
                onClick()
            }
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
                onClick = {
                    AnalyticsTracker.uiControl(
                        "global_setting_option",
                        "アプリアイコン「${variant.label}」を選択",
                        mapOf("setting_label" to "アプリアイコン", "setting_value_label" to variant.label)
                    )
                    onClick()
                },
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
                onClick = null,
                colors = globalSettingsRadioButtonColors()
            )
        }
    }
}

@Composable
private fun globalSettingsRadioButtonColors(): RadioButtonColors {
    val selectedColor = if (LocalFutachaThemePalette.current == ThemePalette.FutabaClassic) {
        if (MaterialTheme.colorScheme.background.luminance() > 0.5f) {
            Color(0xFF4A0000)
        } else {
            Color(0xFFFFD8C8)
        }
    } else {
        MaterialTheme.colorScheme.primary
    }
    return RadioButtonDefaults.colors(
        selectedColor = selectedColor,
        unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
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
