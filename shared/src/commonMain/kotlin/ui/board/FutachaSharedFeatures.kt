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

/**
 * Shared services for the additional controls in the existing Futacha screens.
 *
 * Provided through a static composition local, so the instance must stay the
 * same while preferences change: a new instance per preference save made every
 * consumer recompose, even for unrelated keys such as cache check times.
 * [preferences] reads the underlying state, so only readers recompose.
 */
internal class FutachaSharedFeatures(
    val store: CompatibilityStore,
    private val preferencesState: State<Map<String, String>>,
    val httpClient: HttpClient?,
    val repository: BoardRepository?,
    val fileSystem: FileSystem?,
    val cookieRepository: CookieRepository?,
    val appVersion: String,
    val openSettings: (String) -> Unit,
    val onTabsClosed: (com.valoser.futacha.shared.compat.ClosedTabBatch) -> Unit = {},
    val ngRulesState: State<List<com.valoser.futacha.shared.compat.CompatNgRule>?>? = null
) {
    val catalogCache = FutachaCatalogMemoryCache()
    val preferences: Map<String, String> get() = preferencesState.value

    private val preferenceReads = mutableMapOf<String, State<String?>>()

    fun value(path: String, key: String, vararg legacyTitles: String): String? =
        preferenceReads.getOrPut(if (legacyTitles.isEmpty()) "$path/$key" else "$path/$key/${legacyTitles.joinToString("\u0000")}") {
            derivedStateOf(structuralEqualityPolicy()) {
                preferencesState.value.compatPreferenceValue(path, key, *legacyTitles)
            }
        }.value

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
    val preferencesState = store.preferences.collectAsState(emptyMap())
    // null until the store has loaded: an empty placeholder list would show
    // posts that shared NG rules hide.
    val ngRulesState = remember(store) {
        kotlinx.coroutines.flow.combine(store.isLoaded, store.ngRules) { loaded, rules -> rules.takeIf { loaded } }
    }.collectAsState(initial = null)
    // Use the existing shared destination for every modern save action as well.
    // Migrate the modern destination only when the shared setting has never been saved.
    LaunchedEffect(store, appStateStore) {
        if (appStateStore == null) return@LaunchedEffect
        // Placeholder preferences lack the stored location; writing the modern
        // one then would replace the location chosen in compatibility mode.
        store.isLoaded.first { it }
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
    val features = remember(store, preferencesState, httpClient, activeRepository, fileSystem, cookieRepository, appVersion) {
        FutachaSharedFeatures(store, preferencesState, httpClient, activeRepository, fileSystem,
            cookieRepository, appVersion, openSettings = { settingsPaths = listOf(it) }, onTabsClosed = { closedBatch = it }, ngRulesState = ngRulesState)
    }
    LaunchedEffect(closedBatch, notification) {
        if (closedBatch != null || notification != null) {
            delay(features.intValue("control", "controlCloseToastDuration", 1000..30000)?.toLong() ?: 7000L)
            closedBatch = null; notification = null
        }
    }
    var foreground by remember { mutableStateOf(true) }
    CompatForegroundLifecycleEffect { foreground = it }
    val refreshEnabled by remember(preferencesState) {
        derivedStateOf(structuralEqualityPolicy()) {
            com.valoser.futacha.shared.compat.sharedFeatureRefreshEnabled(preferencesState.value)
        }
    }
    val cacheEnabled by remember(preferencesState) {
        derivedStateOf(structuralEqualityPolicy()) { preferencesState.value[COMPAT_CACHE_ENABLED_KEY] }
    }
    val context = coil3.compose.LocalPlatformContext.current
    LaunchedEffect(httpClient, cacheEnabled, foreground) {
        val preferences = preferencesState.value
        val client = httpClient ?: return@LaunchedEffect
        if (!foreground || preferences[COMPAT_CACHE_ENABLED_KEY] != "ON") return@LaunchedEffect
        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        if (shouldProbeCompatCacheServer(now, preferences[COMPAT_CACHE_STATUS_DATE_KEY],
                preferences[COMPAT_CACHE_AVAILABLE_KEY] == "ON", preferences[COMPAT_CACHE_CHECK_TIME_KEY]?.toLongOrNull() ?: 0)) {
            try {
                val result = probeCompatCacheServer(client, effectiveCompatCacheBaseUrl(preferences[COMPAT_CACHE_BASE_URL_KEY]), now)
                val date = formatCompatCacheStatusDate(result.checkedAtEpochMillis)
                store.savePreferences(mapOf(
                    COMPAT_CACHE_AVAILABLE_KEY to if (result.available) "ON" else "OFF",
                    COMPAT_CACHE_CHECK_TIME_KEY to result.checkedAtEpochMillis.toString(),
                    COMPAT_CACHE_STATUS_DATE_KEY to date,
                    COMPAT_CACHE_STATUS_KEY to formatCompatCacheStatusSummary(date, result.message)
                ))
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
                        path = path, store = store, preferences = preferencesState.value,
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
