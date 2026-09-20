@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.valoser.futacha.shared.ui.compat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Help
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import futacha.shared.generated.resources.Res
import com.valoser.futacha.shared.ui.util.PlatformBackHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.util.rememberUrlLauncher
import com.valoser.futacha.shared.util.isAndroid
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation
import com.valoser.futacha.shared.version.isNewerVersion

// CompatibilityStore reserves the compat.* namespace. This is the namespaced
// equivalent of the reference SharedPreferences key commonUsedVersion.
internal const val COMPAT_USED_VERSION_KEY = "compat.commonUsedVersion"
internal fun compatCurrentStoreUrl(isAndroidPlatform: Boolean = isAndroid()): String =
    if (com.valoser.futacha.shared.util.isDesktop()) "https://github.com/inqueuet/futacha/releases"
    else if (isAndroidPlatform) "https://play.google.com/store/apps/details?id=com.valoser.futacha"
    else "https://apps.apple.com/jp/app/id6756841201"
internal const val COMPAT_REFERENCE_AUTHOR_URL = "https://twitter.com/AndosanDev"
internal const val FUTACHA_AUTHOR_URL = "https://x.com/create_app_null"

internal fun shouldOpenCompatChangeLog(savedVersion: String?, currentVersion: String): Boolean =
    !savedVersion.isNullOrBlank() && isNewerVersion(savedVersion, currentVersion)

internal suspend fun consumeCompatChangeLogUpdate(
    store: CompatibilityStore,
    currentVersion: String
): Boolean {
    if (currentVersion.isBlank()) return false
    // Read durable state rather than the UI Flow's possibly initial/stale map.
    // An absent marker establishes a baseline; it is not evidence of an update.
    val savedVersion = store.loadPreference(COMPAT_USED_VERSION_KEY)
    val shouldOpen = shouldOpenCompatChangeLog(savedVersion, currentVersion)
    if (savedVersion.isNullOrBlank() || shouldOpen) {
        // Commit before navigation so recreation cannot repeat the same notice.
        store.savePreference(COMPAT_USED_VERSION_KEY, currentVersion)
    }
    return shouldOpen
}

@Composable
internal fun CompatChangeLogScreen(
    appVersion: String,
    store: CompatibilityStore,
    onOpenHelp: () -> Unit,
    onBack: () -> Unit
) {
    val openUrl = rememberUrlLauncher()
    LaunchedEffect(store, appVersion) {
        if (appVersion.isNotBlank()) {
            runSuspendCatchingPreservingCancellation {
                store.savePreference(COMPAT_USED_VERSION_KEY, appVersion)
            }.onFailure { failure ->
                Logger.e("CompatChangeLog", "Failed to save the displayed version", failure)
            }
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("更新履歴") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    IconButton(onClick = { openUrl(compatCurrentStoreUrl()) }) {
                        Icon(Icons.Filled.BusinessCenter, contentDescription = "ストア")
                    }
                    IconButton(onClick = onOpenHelp) {
                        Icon(Icons.Filled.Help, contentDescription = "ヘルプ")
                    }
                }
            )
        },
        containerColor = LocalCompatibilityPalette.current.background
    ) { padding ->
        CompatChangeLogContent(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("compat-change-log-content")
        )
    }
}

internal fun compatibilityChangeLogHeadingColor(palette: CompatibilityPalette) =
    if (palette.chrome == palette.background) palette.uiPrimaryText else palette.chrome

@Composable
internal fun CompatChangeLogContent(modifier: Modifier = Modifier) {
    val palette = LocalCompatibilityPalette.current
    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            top = 12.dp,
            end = 16.dp,
            bottom = 28.dp
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        items(FUTACHA_CHANGE_LOG_ENTRIES, key = FutachaChangeLogEntry::version) { entry ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("compat-change-log-version-${entry.version}"),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = entry.version,
                    color = compatibilityChangeLogHeadingColor(palette),
                    fontSize = 24.sp,
                    lineHeight = 30.sp
                )
                entry.changes.forEachIndexed { index, change ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "•",
                            color = palette.uiPrimaryText,
                            fontSize = 17.sp,
                            lineHeight = 26.sp,
                            modifier = Modifier.width(20.dp)
                        )
                        Text(
                            text = change,
                            color = palette.uiPrimaryText,
                            fontSize = 17.sp,
                            lineHeight = 26.sp,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("compat-change-log-body-${entry.version}-$index")
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun CompatLicenseScreen(onBack: () -> Unit) {
    var selected by remember { mutableStateOf<FutachaLicenseAsset?>(null) }
    var paragraphs by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(selected) {
        paragraphs = null
        selected?.resourcePath?.let { path ->
            paragraphs = runSuspendCatchingPreservingCancellation {
                Res.readBytes(path).decodeToString().lines().chunked(32).map { it.joinToString("\n") }
            }.getOrElse { listOf("ライセンスを読み込めませんでした。画面を開き直してください。") }
        }
    }
    fun back() { if (selected != null) selected = null else onBack() }
    PlatformBackHandler(onBack = ::back)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ライセンス") },
                navigationIcon = {
                    IconButton(onClick = ::back) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("compat-license-list")
        ) {
            if (selected != null) {
                item { Text(selected!!.text, Modifier.padding(10.dp)) }
                items(paragraphs ?: listOf("読み込み中…")) { Text(it, Modifier.padding(horizontal = 10.dp)) }
            } else {
                items(FUTACHA_LICENSE_ASSETS, key = { it.id }) { asset ->
                    if (asset.resourcePath != null) {
                        TextButton(onClick = { selected = asset }, modifier = Modifier.fillMaxWidth().testTag("compat-license-${asset.id}"),
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                            Text(asset.text, Modifier.fillMaxWidth())
                        }
                    } else {
                        Text(text = asset.text, modifier = Modifier.padding(10.dp).testTag("compat-license-${asset.id}"))
                    }
                }
            }
        }
    }
}

@Composable
internal expect fun CompatReferenceChangeLogView(
    html: String,
    modifier: Modifier,
    onLinkClicked: (String) -> Unit
)
