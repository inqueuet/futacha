package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
import com.valoser.futacha.shared.model.CatalogNavEntryConfig
import com.valoser.futacha.shared.model.CatalogNavEntryId
import com.valoser.futacha.shared.model.CatalogNavEntryPlacement
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadMenuEntryConfig
import com.valoser.futacha.shared.model.ThreadMenuEntryId
import com.valoser.futacha.shared.model.ThreadMenuEntryPlacement
import com.valoser.futacha.shared.model.defaultCatalogNavEntries
import com.valoser.futacha.shared.model.defaultThreadMenuEntries
import com.valoser.futacha.shared.model.normalizeCatalogNavEntries
import com.valoser.futacha.shared.model.normalizeThreadMenuEntries
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.service.AUTO_SAVE_DIRECTORY
import com.valoser.futacha.shared.service.DEFAULT_MANUAL_SAVE_ROOT
import com.valoser.futacha.shared.service.MANUAL_SAVE_DIRECTORY
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.ui.image.resolveImageCacheDirectory
import com.valoser.futacha.shared.ui.util.PlatformBackHandler
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import com.valoser.futacha.shared.util.SaveDirectorySelection
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.isAndroid
import com.valoser.futacha.shared.util.rememberUrlLauncher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class GlobalSettingsEntry(
    val label: String,
    val description: String,
    val icon: ImageVector,
    val action: GlobalSettingsAction
)

private val cookieSettingsEntry = GlobalSettingsEntry(
    label = "Cookie",
    description = "送信する Cookie を確認・削除",
    icon = Icons.Rounded.History,
    action = GlobalSettingsAction.Cookies
)

private val globalSettingsEntries = listOf(
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

private enum class GlobalSettingsAction {
    Cookies,
    Email,
    X,
    Developer,
    PrivacyPolicy
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GlobalSettingsScreen(
    onBack: () -> Unit,
    appVersion: String,
    isBackgroundRefreshEnabled: Boolean,
    onBackgroundRefreshChanged: (Boolean) -> Unit,
    isLightweightModeEnabled: Boolean,
    onLightweightModeChanged: (Boolean) -> Unit,
    manualSaveDirectory: String = DEFAULT_MANUAL_SAVE_ROOT,
    resolvedManualSaveDirectory: String? = null,
    onManualSaveDirectoryChanged: (String) -> Unit = {},
    saveDirectorySelection: SaveDirectorySelection = SaveDirectorySelection.MANUAL_INPUT,
    onSaveDirectorySelectionChanged: (SaveDirectorySelection) -> Unit = {},
    onOpenSaveDirectoryPicker: (() -> Unit)? = null,
    onOpenCookieManager: (() -> Unit)? = null,
    preferredFileManagerLabel: String? = null,
    onFileManagerSelected: ((packageName: String, label: String) -> Unit)? = null,
    onClearPreferredFileManager: (() -> Unit)? = null,
    historyEntries: List<ThreadHistoryEntry>,
    fileSystem: com.valoser.futacha.shared.util.FileSystem? = null,
    autoSavedThreadRepository: SavedThreadRepository? = null,
    threadMenuEntries: List<ThreadMenuEntryConfig> = defaultThreadMenuEntries(),
    onThreadMenuEntriesChanged: (List<ThreadMenuEntryConfig>) -> Unit = {},
    catalogNavEntries: List<CatalogNavEntryConfig> = defaultCatalogNavEntries(),
    onCatalogNavEntriesChanged: (List<CatalogNavEntryConfig>) -> Unit = {}
) {
    val urlLauncher = rememberUrlLauncher()
    var isFileManagerPickerVisible by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val imageLoader = LocalFutachaImageLoader.current
    val platformContext = LocalPlatformContext.current
    val historyCount = historyEntries.size
    var autoSavedCount by remember { mutableStateOf<Int?>(null) }
    var autoSavedSize by remember { mutableStateOf<Long?>(null) }
    var localThreadMenuEntries by remember(threadMenuEntries) {
        mutableStateOf(normalizeThreadMenuEntries(threadMenuEntries))
    }
    var localCatalogNavEntries by remember(catalogNavEntries) {
        mutableStateOf(normalizeCatalogNavEntries(catalogNavEntries))
    }

    val effectiveAutoSavedRepository = remember(fileSystem, autoSavedThreadRepository) {
        autoSavedThreadRepository ?: fileSystem?.let { SavedThreadRepository(it, baseDirectory = AUTO_SAVE_DIRECTORY) }
    }

    LaunchedEffect(effectiveAutoSavedRepository) {
        if (effectiveAutoSavedRepository == null) {
            autoSavedCount = null
            autoSavedSize = null
            return@LaunchedEffect
        }
        autoSavedCount = runCatching { effectiveAutoSavedRepository.getThreadCount() }.getOrNull()
        autoSavedSize = runCatching { effectiveAutoSavedRepository.getTotalSize() }.getOrNull()
    }

    fun normalizeManualSaveInput(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return MANUAL_SAVE_DIRECTORY
        if (trimmed.startsWith("/")) return trimmed

        val lower = trimmed.lowercase()
        return when {
            lower == "download" || lower == "downloads" -> "Download"
            lower.startsWith("download/") || lower.startsWith("downloads/") -> "Download/${trimmed.substringAfter('/')}"
            lower == "documents" -> "Documents"
            lower.startsWith("documents/") -> "Documents/${trimmed.substringAfter('/')}"
            else -> trimmed
        }
    }
    fun fallbackResolvedPath(manualSaveDir: String): String {
        val normalized = normalizeManualSaveInput(manualSaveDir)
        if (normalized.startsWith("/")) return normalized

        val lower = normalized.lowercase()
        return when {
            lower == "download" || lower == "downloads" -> "Download/futacha/$MANUAL_SAVE_DIRECTORY"
            lower.startsWith("download/") || lower.startsWith("downloads/") -> "Download/${normalized.substringAfter('/')}"
            lower == "documents" -> "$DEFAULT_MANUAL_SAVE_ROOT/futacha/$MANUAL_SAVE_DIRECTORY"
            lower.startsWith("documents/") -> "$DEFAULT_MANUAL_SAVE_ROOT/${normalized.substringAfter('/')}"
            else -> "$DEFAULT_MANUAL_SAVE_ROOT/futacha/$normalized"
        }
    }
    val isAndroidPlatform = remember { isAndroid() }
    val availableSaveDirectorySelections = remember(isAndroidPlatform) {
        if (isAndroidPlatform) {
            listOf(SaveDirectorySelection.PICKER)
        } else {
            SaveDirectorySelection.entries.toList()
        }
    }
    val effectiveSaveDirectorySelection = if (isAndroidPlatform) {
        SaveDirectorySelection.PICKER
    } else {
        saveDirectorySelection
    }

    LaunchedEffect(isAndroidPlatform, saveDirectorySelection) {
        if (isAndroidPlatform && saveDirectorySelection != SaveDirectorySelection.PICKER) {
            onSaveDirectorySelectionChanged(SaveDirectorySelection.PICKER)
        }
    }

    fun formatSizeMb(bytes: Long?): String {
        if (bytes == null) return "不明"
        val mbTimesTen = (bytes / (1024.0 * 1024.0)) * 10
        val rounded = kotlin.math.round(mbTimesTen) / 10.0
        return "${rounded} MB"
    }
    var manualSaveInput by rememberSaveable(manualSaveDirectory) {
        mutableStateOf(manualSaveDirectory)
    }
    val resolvedManualPath = remember(manualSaveDirectory, resolvedManualSaveDirectory) {
        resolvedManualSaveDirectory ?: fallbackResolvedPath(manualSaveDirectory)
    }
    val settingsEntries = remember(onOpenCookieManager) {
        buildList {
            if (onOpenCookieManager != null) {
                add(cookieSettingsEntry)
            }
            addAll(globalSettingsEntries)
        }
    }

    @Composable
    fun SettingsSection(
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
    PlatformBackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("設定") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "戻る"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            item {
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
            item {
                val localCatalogEntriesState = remember(localCatalogNavEntries) { localCatalogNavEntries }
                val catalogBarEntries = remember(localCatalogNavEntries) {
                    localCatalogNavEntries.filter { it.placement == CatalogNavEntryPlacement.BAR }.sortedBy { it.order }
                }
                val catalogHiddenEntries = remember(localCatalogNavEntries) {
                    localCatalogNavEntries.filter { it.placement == CatalogNavEntryPlacement.HIDDEN }
                }
                fun updateCatalogEntries(newConfig: List<CatalogNavEntryConfig>) {
                    val normalized = normalizeCatalogNavEntries(newConfig)
                    localCatalogNavEntries = normalized
                    onCatalogNavEntriesChanged(normalized)
                }
                fun resetCatalogEntries() {
                    updateCatalogEntries(defaultCatalogNavEntries())
                }
                fun moveCatalogEntry(id: CatalogNavEntryId, delta: Int) {
                    val sorted = localCatalogNavEntries
                        .filter { it.placement == CatalogNavEntryPlacement.BAR }
                        .sortedBy { it.order }
                        .toMutableList()
                    val index = sorted.indexOfFirst { it.id == id }
                    if (index == -1) return
                    val target = (index + delta).coerceIn(0, sorted.lastIndex)
                    if (target == index) return
                    val item = sorted.removeAt(index)
                    sorted.add(target, item)
                    val merged = localCatalogNavEntries.toMutableList()
                    sorted.forEachIndexed { idx, config ->
                        val origin = merged.indexOfFirst { it.id == config.id }
                        if (origin >= 0) {
                            merged[origin] = config.copy(order = idx)
                        }
                    }
                    updateCatalogEntries(merged)
                }
                fun setCatalogPlacement(id: CatalogNavEntryId, placement: CatalogNavEntryPlacement) {
                    val updated = localCatalogNavEntries.map {
                        if (it.id == id) it.copy(placement = placement) else it
                    }
                    updateCatalogEntries(updated)
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
                        TextButton(onClick = { resetCatalogEntries() }) {
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
                        if (catalogBarEntries.isEmpty()) {
                            Text("なし", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        } else {
                            catalogBarEntries.forEach { entry ->
                                val meta = entry.id.toMeta()
                                Icon(imageVector = meta.icon, contentDescription = meta.label, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    if (catalogHiddenEntries.isNotEmpty()) {
                        Text(
                            text = "非表示: ${catalogHiddenEntries.size} 件",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val allCatalogEntries = remember(localCatalogEntriesState) {
                        localCatalogNavEntries.sortedBy { it.id.name }
                    }
                    allCatalogEntries.forEach { item ->
                        val meta = item.id.toMeta()
                        val barIndex = catalogBarEntries.indexOfFirst { it.id == item.id }
                        val canMoveLeft = item.placement == CatalogNavEntryPlacement.BAR && barIndex > 0
                        val canMoveRight = item.placement == CatalogNavEntryPlacement.BAR && barIndex in 0 until catalogBarEntries.lastIndex
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
                                    onClick = { moveCatalogEntry(item.id, -1) },
                                    enabled = canMoveLeft
                                ) {
                                    Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "左へ移動")
                                }
                                IconButton(
                                    onClick = { moveCatalogEntry(item.id, 1) },
                                    enabled = canMoveRight
                                ) {
                                    Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "右へ移動")
                                }
                                AssistChip(
                                    onClick = { setCatalogPlacement(item.id, CatalogNavEntryPlacement.BAR) },
                                    label = { Text("バー") },
                                    leadingIcon = if (item.placement == CatalogNavEntryPlacement.BAR) {
                                        { Icon(Icons.Rounded.Check, contentDescription = null) }
                                    } else null,
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = if (item.placement == CatalogNavEntryPlacement.BAR) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                )
                                AssistChip(
                                    onClick = { setCatalogPlacement(item.id, CatalogNavEntryPlacement.HIDDEN) },
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
            item {
                val barEntries = remember(localThreadMenuEntries) {
                    localThreadMenuEntries.filter { it.placement == ThreadMenuEntryPlacement.BAR }.sortedBy { it.order }
                }
                val sheetEntries = remember(localThreadMenuEntries) {
                    localThreadMenuEntries.filter { it.placement == ThreadMenuEntryPlacement.SHEET }.sortedBy { it.order }
                }
                val hiddenEntries = remember(localThreadMenuEntries) {
                    localThreadMenuEntries.filter { it.placement == ThreadMenuEntryPlacement.HIDDEN }
                }
                fun updateMenuEntries(newConfig: List<ThreadMenuEntryConfig>) {
                    val normalized = normalizeThreadMenuEntries(newConfig)
                    localThreadMenuEntries = normalized
                    onThreadMenuEntriesChanged(normalized)
                }
                fun resetMenuEntries() {
                    updateMenuEntries(defaultThreadMenuEntries())
                }
                fun moveWithinPlacement(id: ThreadMenuEntryId, delta: Int, placement: ThreadMenuEntryPlacement) {
                    val sorted = localThreadMenuEntries
                        .filter { it.placement == placement }
                        .sortedBy { it.order }
                        .toMutableList()
                    val index = sorted.indexOfFirst { it.id == id }
                    if (index == -1) return
                    val target = (index + delta).coerceIn(0, sorted.lastIndex)
                    if (target == index) return
                    val item = sorted.removeAt(index)
                    sorted.add(target, item)
                    val merged = localThreadMenuEntries.toMutableList()
                    sorted.forEachIndexed { idx, config ->
                        val originIndex = merged.indexOfFirst { it.id == config.id }
                        if (originIndex >= 0) {
                            merged[originIndex] = config.copy(order = idx)
                        }
                    }
                    updateMenuEntries(merged)
                }
                fun setPlacement(id: ThreadMenuEntryId, placement: ThreadMenuEntryPlacement) {
                    val updated = localThreadMenuEntries.map {
                        if (it.id == id) it.copy(placement = placement) else it
                    }
                    val normalized = normalizeThreadMenuEntries(updated)
                    localThreadMenuEntries = normalized
                    onThreadMenuEntriesChanged(normalized)
                }
                SettingsSection(
                    title = "スレッドメニュー構成",
                    icon = run {
                        @Suppress("DEPRECATION")
                        Icons.Rounded.ViewList
                    },
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
                        TextButton(onClick = { resetMenuEntries() }) {
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
                        if (barEntries.isEmpty()) {
                            Text("なし", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        } else {
                            barEntries.forEach { entry ->
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
                        if (sheetEntries.isEmpty()) {
                            Text("なし", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        } else {
                            sheetEntries.forEach { entry ->
                                val meta = entry.toMeta()
                                Icon(imageVector = meta.icon, contentDescription = meta.label, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    val allEntries = remember(localThreadMenuEntries) {
                        localThreadMenuEntries.sortedBy { it.id.name }
                    }
                    allEntries.forEach { item ->
                        val meta = item.toMeta()
                        val placement = item.placement
                        val barIndex = barEntries.indexOfFirst { it.id == item.id }
                        val sheetIndex = sheetEntries.indexOfFirst { it.id == item.id }
                        val canMoveLeft = placement == ThreadMenuEntryPlacement.BAR && barIndex > 0 ||
                            placement == ThreadMenuEntryPlacement.SHEET && sheetIndex > 0
                        val canMoveRight = placement == ThreadMenuEntryPlacement.BAR && barIndex in 0 until (barEntries.lastIndex) ||
                            placement == ThreadMenuEntryPlacement.SHEET && sheetIndex in 0 until (sheetEntries.lastIndex)
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
                                            ThreadMenuEntryPlacement.BAR -> moveWithinPlacement(item.id, -1, ThreadMenuEntryPlacement.BAR)
                                            ThreadMenuEntryPlacement.SHEET -> moveWithinPlacement(item.id, -1, ThreadMenuEntryPlacement.SHEET)
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
                                            ThreadMenuEntryPlacement.BAR -> moveWithinPlacement(item.id, 1, ThreadMenuEntryPlacement.BAR)
                                            ThreadMenuEntryPlacement.SHEET -> moveWithinPlacement(item.id, 1, ThreadMenuEntryPlacement.SHEET)
                                            else -> {}
                                        }
                                    },
                                    enabled = canMoveRight
                                ) {
                                    Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "右へ移動")
                                }
                                AssistChip(
                                    onClick = { setPlacement(item.id, ThreadMenuEntryPlacement.BAR) },
                                    label = { Text("バー") },
                                    leadingIcon = if (placement == ThreadMenuEntryPlacement.BAR) {
                                        { Icon(Icons.Rounded.Check, contentDescription = null) }
                                    } else null,
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = if (placement == ThreadMenuEntryPlacement.BAR) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                )
                                AssistChip(
                                    onClick = { setPlacement(item.id, ThreadMenuEntryPlacement.SHEET) },
                                    label = { Text("設定") },
                                    leadingIcon = if (placement == ThreadMenuEntryPlacement.SHEET) {
                                        { Icon(Icons.Rounded.Check, contentDescription = null) }
                                    } else null,
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = if (placement == ThreadMenuEntryPlacement.SHEET) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                )
                                AssistChip(
                                    onClick = { setPlacement(item.id, ThreadMenuEntryPlacement.HIDDEN) },
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
                    if (hiddenEntries.isNotEmpty()) {
                        Text(
                            text = "非表示: ${hiddenEntries.size} 件",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item {
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
                                if (preferredFileManagerLabel != null) {
                                    Text(
                                        text = "現在の設定: $preferredFileManagerLabel",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Text(
                                        text = "未設定(システムのデフォルト)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { isFileManagerPickerVisible = true }
                                    ) {
                                        Text("ファイラーを選択")
                                    }
                                    if (preferredFileManagerLabel != null) {
                                        OutlinedButton(
                                            onClick = { onClearPreferredFileManager?.invoke() }
                                        ) {
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
                                when (effectiveSaveDirectorySelection) {
                                    SaveDirectorySelection.MANUAL_INPUT -> {
                                        OutlinedTextField(
                                            value = manualSaveInput,
                                            onValueChange = { manualSaveInput = it },
                                            singleLine = true,
                                            placeholder = { Text(DEFAULT_MANUAL_SAVE_ROOT) },
                                            modifier = Modifier.fillMaxWidth(),
                                            label = { Text("フォルダ名またはパス") }
                                        )
                                        Text(
                                            text = "保存先: $resolvedManualPath",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            TextButton(onClick = {
                                                manualSaveInput = DEFAULT_MANUAL_SAVE_ROOT
                                                onManualSaveDirectoryChanged(DEFAULT_MANUAL_SAVE_ROOT)
                                            }) {
                                                Text("デフォルトに戻す")
                                            }
                                            Button(onClick = {
                                                val normalized = normalizeManualSaveInput(manualSaveInput)
                                                manualSaveInput = normalized
                                                onManualSaveDirectoryChanged(normalized)
                                            }) {
                                                Text("保存先を更新")
                                            }
                                        }
                                    }
                                    SaveDirectorySelection.PICKER -> {
                                        val pickerDescription = if (isAndroidPlatform) {
                                            "AndroidではSAFで選んだフォルダのみ使用できます。"
                                        } else {
                                            "ファイラーで選んだディレクトリを保存先に使います。パスが取得できない場合は手入力に切り替えてください。"
                                        }
                                        Text(
                                            text = pickerDescription,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (isAndroidPlatform) {
                                            Text(
                                                text = "AndroidではSAF経由の保存先のみ使用できます。",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = "※ SAF のフォルダー選択 (OPEN_DOCUMENT_TREE) に非対応のファイラーでは選択できません。その場合は標準ファイラーを使うか手入力を選んでください。",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        Text(
                                            text = "保存先: $resolvedManualPath",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Button(
                                                onClick = { onOpenSaveDirectoryPicker?.invoke() },
                                                enabled = onOpenSaveDirectoryPicker != null
                                            ) {
                                                Text("フォルダを選択")
                                            }
                                            if (!isAndroidPlatform) {
                                                OutlinedButton(onClick = {
                                                    manualSaveInput = DEFAULT_MANUAL_SAVE_ROOT
                                                    onManualSaveDirectoryChanged(DEFAULT_MANUAL_SAVE_ROOT)
                                                    onSaveDirectorySelectionChanged(SaveDirectorySelection.MANUAL_INPUT)
                                                }) {
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
            item {
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
                                onClick = {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("画像キャッシュを削除中...")
                                        val result = runCatching {
                                            withContext(AppDispatchers.io) {
                                                imageLoader.diskCache?.clear()
                                                imageLoader.memoryCache?.clear()
                                                Unit
                                            }
                                        }
                                        if (result.isSuccess) {
                                            snackbarHostState.showSnackbar("画像キャッシュを削除しました")
                                        } else {
                                            val reason = result.exceptionOrNull()?.message ?: "不明なエラー"
                                            snackbarHostState.showSnackbar("削除に失敗しました: $reason")
                                        }
                                    }
                                }
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
                                onClick = {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("一時キャッシュを削除中...")
                                        val result = runCatching {
                                            withContext(AppDispatchers.io) {
                                                val fs = fileSystem
                                                if (fs != null) {
                                                    resolveImageCacheDirectory(platformContext)
                                                        ?.toString()
                                                        ?.let { pathString ->
                                                            fs.deleteRecursively(pathString).getOrThrow()
                                                        }
                                                }
                                                Unit
                                            }
                                        }
                                        if (result.isSuccess) {
                                            snackbarHostState.showSnackbar("一時キャッシュを削除しました")
                                        } else {
                                            val reason = result.exceptionOrNull()?.message ?: "不明なエラー"
                                            snackbarHostState.showSnackbar("削除に失敗しました: $reason")
                                        }
                                    }
                                }
                            ) {
                                Text("掃除する")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            item {
                val isWarning = historyCount >= 50
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
                                    text = "履歴: ${historyCount}件",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "自動保存: ${autoSavedCount ?: 0}件 / ${formatSizeMb(autoSavedSize)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (isWarning) {
                                    Text(
                                        text = "※件数が多いと更新に時間がかかることがあります。不要な履歴は既存の削除・クリア操作をご利用ください。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            item {
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
                                    when (entry.action) {
                                        GlobalSettingsAction.Cookies -> {
                                            onOpenCookieManager?.invoke()
                                        }
                                        GlobalSettingsAction.Email -> {
                                            urlLauncher("mailto:admin@valoser.com?subject=お問い合わせ")
                                        }
                                        GlobalSettingsAction.X -> {
                                            urlLauncher("https://x.com/may_012345")
                                        }
                                        GlobalSettingsAction.Developer -> {
                                            urlLauncher("https://github.com/inqueuet/futacha")
                                        }
                                        GlobalSettingsAction.PrivacyPolicy -> {
                                            urlLauncher("https://note.com/inqueuet/n/nc6ebcc1d6a67")
                                        }
                                    }
                                    onBack()
                                }
                        )
                        if (index != settingsEntries.lastIndex) {
                            HorizontalDivider()
                        }
                    }
                }
            }
            item {
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
        }
    }

    if (isFileManagerPickerVisible) {
        FileManagerPickerDialog(
            onDismiss = { isFileManagerPickerVisible = false },
            onFileManagerSelected = { packageName, label ->
                isFileManagerPickerVisible = false
                onFileManagerSelected?.invoke(packageName, label)
            }
        )
    }
}

/**
 * Platform-specific file manager picker dialog
 */
@Composable
expect fun FileManagerPickerDialog(
    onDismiss: () -> Unit,
    onFileManagerSelected: (packageName: String, label: String) -> Unit
)
