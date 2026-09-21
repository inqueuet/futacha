package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import kotlinx.coroutines.launch
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.ui.compat.CompatSettingsScreen
import com.valoser.futacha.shared.ui.compat.LocalCompatibilityPalette
import com.valoser.futacha.shared.ui.compat.compatPreferenceValue
import com.valoser.futacha.shared.util.FileSystem
import io.ktor.client.HttpClient
import com.valoser.futacha.shared.ui.compat.*
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.first
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.SaveLocation.Companion.toRawString

/** Shared services for the additional controls in the existing Futacha screens. */
internal data class FutachaSharedFeatures(
    val store: CompatibilityStore,
    val preferences: Map<String, String>,
    val httpClient: HttpClient?,
    val repository: BoardRepository?,
    val fileSystem: FileSystem?,
    val cookieRepository: CookieRepository?,
    val appVersion: String,
    val openSettings: (String) -> Unit,
    val onTabsClosed: (com.valoser.futacha.shared.compat.ClosedTabBatch) -> Unit = {}
) {
    fun value(path: String, key: String, vararg legacyTitles: String): String? =
        preferences.compatPreferenceValue(path, key, *legacyTitles)

    fun displayValue(path: String, key: String, vararg legacyTitles: String): String? =
        value(path, key, *legacyTitles)?.let {
            com.valoser.futacha.shared.ui.compat.compatPreferenceDisplayValue(key, it)
        }
}

internal val LocalFutachaSharedFeatures = staticCompositionLocalOf<FutachaSharedFeatures?> { null }

@Composable
internal fun ProvideFutachaSharedFeatures(
    store: CompatibilityStore?,
    httpClient: HttpClient?,
    repository: BoardRepository?,
    fileSystem: FileSystem?,
    cookieRepository: CookieRepository?,
    appVersion: String,
    appStateStore: com.valoser.futacha.shared.state.AppStateStore? = null,
    catalogImageLoader: coil3.ImageLoader? = null,
    content: @Composable () -> Unit
) {
    if (store == null) {
        content()
        return
    }
    val preferences by store.preferences.collectAsState(emptyMap())
    // Use the existing shared destination for every modern save action as well.
    // Migrate the modern destination only when the shared setting has never been saved.
    LaunchedEffect(store, appStateStore) {
        if (appStateStore == null) return@LaunchedEffect
        store.preferences.collect { values ->
            try {
            val raw = values.compatPreferenceValue("storage", "dummyDownloadDir", "保存ファイルの保存先")
            if (raw == null) {
                store.savePreference(compatPreferenceStorageKey("storage", "dummyDownloadDir"),
                    appStateStore.manualSaveLocation.first().toRawString())
            } else {
                val location = parseCompatSaveLocation(raw) ?: SaveLocation.Path(com.valoser.futacha.shared.service.DEFAULT_MANUAL_SAVE_ROOT)
                if (appStateStore.manualSaveLocation.first() != location) appStateStore.setManualSaveLocation(location)
            }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                com.valoser.futacha.shared.util.Logger.e("FutachaSharedFeatures", "Shared save destination could not be applied", failure)
            }
        }
    }
    val ownedRepository = remember(repository, httpClient) {
        if (repository == null && httpClient != null) com.valoser.futacha.shared.repo.createRemoteBoardRepository(httpClient) else null
    }
    DisposableEffect(ownedRepository) { onDispose { ownedRepository?.close() } }
    val activeRepository = repository ?: ownedRepository
    var settingsPaths by remember { mutableStateOf<List<String>>(emptyList()) }
    var closedBatch by remember { mutableStateOf<com.valoser.futacha.shared.compat.ClosedTabBatch?>(null) }
    var notification by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val features = FutachaSharedFeatures(store, preferences, httpClient, activeRepository, fileSystem,
        cookieRepository, appVersion, openSettings = { settingsPaths = listOf(it) }, onTabsClosed = { closedBatch = it })
    LaunchedEffect(closedBatch, notification) {
        if (closedBatch != null || notification != null) {
            delay(features.intValue("control", "controlCloseToastDuration", 1000..30000)?.toLong() ?: 7000L)
            closedBatch = null; notification = null
        }
    }
    var foreground by remember { mutableStateOf(true) }
    CompatForegroundLifecycleEffect { foreground = it }
    val refreshEnabled = com.valoser.futacha.shared.compat.sharedFeatureRefreshEnabled(preferences)
    val context = coil3.compose.LocalPlatformContext.current
    LaunchedEffect(httpClient, preferences[COMPAT_CACHE_ENABLED_KEY], foreground) {
        val client = httpClient ?: return@LaunchedEffect
        if (!foreground || preferences[COMPAT_CACHE_ENABLED_KEY] != "ON") return@LaunchedEffect
        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        if (shouldProbeCompatCacheServer(now, preferences[COMPAT_CACHE_STATUS_DATE_KEY],
                preferences[COMPAT_CACHE_AVAILABLE_KEY] == "ON", preferences[COMPAT_CACHE_CHECK_TIME_KEY]?.toLongOrNull() ?: 0)) {
            try {
                val result = probeCompatCacheServer(client, effectiveCompatCacheBaseUrl(preferences[COMPAT_CACHE_BASE_URL_KEY]), now)
                val date = formatCompatCacheStatusDate(result.checkedAtEpochMillis)
                store.savePreference(COMPAT_CACHE_AVAILABLE_KEY, if (result.available) "ON" else "OFF")
                store.savePreference(COMPAT_CACHE_CHECK_TIME_KEY, result.checkedAtEpochMillis.toString())
                store.savePreference(COMPAT_CACHE_STATUS_DATE_KEY, date)
                store.savePreference(COMPAT_CACHE_STATUS_KEY, formatCompatCacheStatusSummary(date, result.message))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { com.valoser.futacha.shared.util.Logger.e("FutachaSharedFeatures", "Cache status check failed", failure) }
        }
    }
    LaunchedEffect(store, activeRepository, foreground, refreshEnabled) {
        if (!foreground || !refreshEnabled || activeRepository == null) return@LaunchedEffect
        while (isActive) {
            try {
                com.valoser.futacha.shared.compat.refreshSharedFeatures(store, activeRepository, isCompatWifiConnected(context),
                    onNewMatches = { notification = "巡回で${it.size}件のスレッドが見つかりました" })
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { com.valoser.futacha.shared.util.Logger.e("FutachaSharedFeatures", "Shared refresh failed", failure) }
            delay(com.valoser.futacha.shared.compat.COMPAT_FOREGROUND_TICK_MILLIS)
        }
    }
    var fontPath by remember { mutableStateOf<String?>(null) }
    val selectedFont = features.value("design", "dummyCustomFont")
    LaunchedEffect(fileSystem, selectedFont) {
        fontPath = if (fileSystem == null || selectedFont.isNullOrBlank() || selectedFont == "デフォルト") null
        else withContext(AppDispatchers.io) {
            listOf("private/compat_font/font.ttf", "private/compat_font/font.otf").firstOrNull { path ->
                try { fileSystem.exists(path) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { false }
            }?.let(fileSystem::resolveAbsolutePath)
        }
    }
    val font = rememberCompatCustomFontFamily(fontPath)
    val colors = MaterialTheme.colorScheme
    val palette = futachaSharedPalette(
        colors,
        com.valoser.futacha.shared.ui.theme.LocalFutachaChromeColors.current,
        com.valoser.futacha.shared.ui.theme.LocalFutachaThemePalette.current
    )
    CompositionLocalProvider(
        LocalFutachaSharedFeatures provides features,
        com.valoser.futacha.shared.ui.image.LocalFutachaCatalogImageLoader provides
            (catalogImageLoader ?: com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader.current),
        LocalCompatibilityPalette provides palette
    ) {
        MaterialTheme(typography = compatibilityTypography(font, MaterialTheme.typography)) {
        Box(Modifier.fillMaxSize()) {
            content()
            if (closedBatch != null || notification != null) Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(8.dp),
                action = { if (closedBatch != null) TextButton(
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.inversePrimary),
                    onClick = {
                    val batch = closedBatch ?: return@TextButton
                    scope.launch {
                        try { store.restoreClosedTabs(batch); closedBatch = null }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { notification = "タブを復元できませんでした"; closedBatch = null }
                    }
                }) { Text("元に戻す") } }
            ) { Text(if (closedBatch != null) "タブを閉じました" else notification.orEmpty()) }
        }
        settingsPaths.lastOrNull()?.let { path ->
            val close = { settingsPaths = settingsPaths.dropLast(1) }
            if (path == "watcher") {
                val watcher = rememberCompatExternalWatcher(store)
                CompatWatcherManager(store, activeRepository, onDismiss = close, onResultsChanged = {},
                    onOpenExternal = if (com.valoser.futacha.shared.util.isAndroid()) watcher::openManager else null,
                    onOpenHelp = { settingsPaths = settingsPaths + "help" })
            } else Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                androidx.compose.material3.Surface(Modifier.fillMaxSize()) {
                    com.valoser.futacha.shared.ui.util.PlatformBackHandler(onBack = close)
                    if (path == "help") CompatHelpScreen(onBack = close,
                        onOpenChangeLog = { settingsPaths = settingsPaths + "changelog" })
                    else if (path == "changelog") CompatChangeLogScreen(appVersion = appVersion, store = store,
                        onOpenHelp = { settingsPaths = settingsPaths + "help" }, onBack = close)
                    else CompatSettingsScreen(
                        path = path, store = store, preferences = preferences,
                        fileSystem = fileSystem, httpClient = httpClient,
                        cookieRepository = cookieRepository, appVersion = appVersion,
                        modernPresentation = true,
                        onOpenHelp = { settingsPaths = settingsPaths + "help" },
                        onNavigate = { settingsPaths = settingsPaths + it }, onBack = close
                    )
                }
            }
        }
        }
    }
}
