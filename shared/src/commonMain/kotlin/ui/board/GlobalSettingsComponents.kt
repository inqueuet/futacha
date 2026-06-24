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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.valoser.futacha.shared.model.AppIconVariant
import com.valoser.futacha.shared.model.ThemeMode
import com.valoser.futacha.shared.model.ThemePalette
import com.valoser.futacha.shared.model.ThreadBodyTextSize
import com.valoser.futacha.shared.model.ThreadDisplayMode
import com.valoser.futacha.shared.model.ThreadGalleryTapAction
import com.valoser.futacha.shared.model.ThreadPostImageSize
import com.valoser.futacha.shared.state.APP_LOCK_PASSWORD_MIN_LENGTH
import com.valoser.futacha.shared.state.isValidAppLockPassword
import com.valoser.futacha.shared.ui.theme.LocalFutachaThemePalette

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
                GlobalSettingsDisplaySection(
                    themeMode = bindings.behavior.themeMode,
                    onThemeModeChanged = bindings.behavior.onThemeModeChanged,
                    themePalette = bindings.behavior.themePalette,
                    onThemePaletteChanged = bindings.behavior.onThemePaletteChanged,
                    threadBodyTextSize = bindings.behavior.threadBodyTextSize,
                    onThreadBodyTextSizeChanged = bindings.behavior.onThreadBodyTextSizeChanged,
                    threadPostImageSize = bindings.behavior.threadPostImageSize,
                    onThreadPostImageSizeChanged = bindings.behavior.onThreadPostImageSizeChanged,
                    threadDisplayMode = bindings.behavior.threadDisplayMode,
                    onThreadDisplayModeChanged = bindings.behavior.onThreadDisplayModeChanged,
                    appIconVariant = bindings.behavior.appIconVariant,
                    onAppIconVariantChanged = bindings.behavior.onAppIconVariantChanged
                )
            }
            item {
                GlobalSettingsThreadInteractionSection(
                    threadGalleryTapAction = bindings.behavior.threadGalleryTapAction,
                    onThreadGalleryTapActionChanged = bindings.behavior.onThreadGalleryTapActionChanged
                )
            }
            item {
                GlobalSettingsThreadMenuSection(
                    localThreadMenuEntries = bindings.threadMenu.localThreadMenuEntries,
                    threadMenuCallbacks = bindings.threadMenu.threadMenuCallbacks
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
                    isBackgroundRefreshEnabled = bindings.behavior.isBackgroundRefreshEnabled,
                    onBackgroundRefreshChanged = bindings.behavior.onBackgroundRefreshChanged,
                    isLightweightModeEnabled = bindings.behavior.isLightweightModeEnabled,
                    onLightweightModeChanged = bindings.behavior.onLightweightModeChanged,
                    isAdsEnabled = bindings.behavior.isAdsEnabled,
                    onAdsEnabledChanged = bindings.behavior.onAdsEnabledChanged
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
                    .clickable { isExpanded = !isExpanded },
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
internal fun GlobalSettingsDisplaySection(
    themeMode: ThemeMode,
    onThemeModeChanged: (ThemeMode) -> Unit,
    themePalette: ThemePalette,
    onThemePaletteChanged: (ThemePalette) -> Unit,
    threadBodyTextSize: ThreadBodyTextSize,
    onThreadBodyTextSizeChanged: (ThreadBodyTextSize) -> Unit,
    threadPostImageSize: ThreadPostImageSize,
    onThreadPostImageSizeChanged: (ThreadPostImageSize) -> Unit,
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
    isBackgroundRefreshEnabled: Boolean,
    onBackgroundRefreshChanged: (Boolean) -> Unit,
    isLightweightModeEnabled: Boolean,
    onLightweightModeChanged: (Boolean) -> Unit,
    isAdsEnabled: Boolean,
    onAdsEnabledChanged: (Boolean) -> Unit
) {
    SettingsSection(
        title = "バックグラウンド・通信",
        icon = Icons.Rounded.History,
        description = "自動更新、通信量、広告表示に関わる動作をまとめています。"
    ) {
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
    }
}

@Composable
internal fun GlobalSettingsThreadInteractionSection(
    threadGalleryTapAction: ThreadGalleryTapAction,
    onThreadGalleryTapActionChanged: (ThreadGalleryTapAction) -> Unit
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
            password = it
            errorMessage = null
        }
    )
    val confirmationInputState = rememberStableTextInputState(
        text = confirmation,
        onTextChange = {
            confirmation = it
            errorMessage = null
        }
    )
    val actionLabel = if (isAppLockEnabled) "変更" else "有効にする"

    fun submitPassword() {
        when {
            !isValidAppLockPassword(password) -> {
                errorMessage = "${APP_LOCK_PASSWORD_MIN_LENGTH}文字以上で入力してください。"
            }
            password != confirmation -> {
                errorMessage = "確認用パスワードが一致しません。"
            }
            else -> {
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
        onValueChange = passwordInputState.onValueChange,
        label = { Text(if (isAppLockEnabled) "新しいパスワード" else "パスワード") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = confirmationInputState.value,
        onValueChange = confirmationInputState.onValueChange,
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
