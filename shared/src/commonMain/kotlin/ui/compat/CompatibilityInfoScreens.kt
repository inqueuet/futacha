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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.util.rememberUrlLauncher

// CompatibilityStore reserves the compat.* namespace. This is the namespaced
// equivalent of the reference SharedPreferences key commonUsedVersion.
internal const val COMPAT_USED_VERSION_KEY = "compat.commonUsedVersion"
internal const val COMPAT_CURRENT_STORE_URL =
    "https://play.google.com/store/apps/details?id=com.valoser.futacha"
internal const val COMPAT_REFERENCE_AUTHOR_URL = "https://twitter.com/AndosanDev"
internal const val FUTACHA_AUTHOR_URL = "https://x.com/create_app_null"

internal fun shouldOpenCompatChangeLog(savedVersion: String?, currentVersion: String): Boolean =
    currentVersion.isNotBlank() && savedVersion != currentVersion

@Composable
internal fun CompatChangeLogScreen(
    appVersion: String,
    store: CompatibilityStore,
    onOpenHelp: () -> Unit,
    onBack: () -> Unit
) {
    val openUrl = rememberUrlLauncher()
    LaunchedEffect(store, appVersion) {
        if (appVersion.isNotBlank()) store.savePreference(COMPAT_USED_VERSION_KEY, appVersion)
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
                    IconButton(onClick = { openUrl(COMPAT_CURRENT_STORE_URL) }) {
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
                    color = palette.chrome,
                    fontSize = 24.sp,
                    lineHeight = 30.sp
                )
                entry.changes.forEachIndexed { index, change ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "•",
                            color = palette.text,
                            fontSize = 17.sp,
                            lineHeight = 26.sp,
                            modifier = Modifier.width(20.dp)
                        )
                        Text(
                            text = change,
                            color = palette.text,
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ライセンス") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        },
        containerColor = LocalCompatibilityPalette.current.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("compat-license-list")
        ) {
            items(FUTACHA_LICENSE_ASSETS, key = { it.id }) { asset ->
                Text(
                    text = asset.text,
                    modifier = Modifier
                        .padding(10.dp)
                        .testTag("compat-license-${asset.id}")
                )
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
