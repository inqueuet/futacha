@file:OptIn(ExperimentalTime::class)

package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.network.StoredCookie
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.ui.util.PlatformBackHandler
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant as KotlinxInstant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun CookieManagementScreen(
    onBack: () -> Unit,
    repository: CookieRepository
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var cookies by remember { mutableStateOf<List<StoredCookie>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    fun reload() {
        scope.launch {
            isLoading = true
            cookies = runCatching { repository.listCookies() }.getOrElse { emptyList() }
            isLoading = false
        }
    }

    LaunchedEffect(repository) {
        reload()
    }

    PlatformBackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Cookie") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("戻る")
                    }
                },
                actions = {
                    if (cookies.isNotEmpty()) {
                        IconButton(onClick = {
                            scope.launch {
                                repository.clearAll()
                                reload()
                                snackbarHostState.showSnackbar("すべてのCookieを削除しました")
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = "すべて削除"
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("読み込み中...")
            }
            return@Scaffold
        }

        if (cookies.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("Cookie はありません")
            }
            return@Scaffold
        }

        val grouped = cookies.groupBy { it.domain }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            grouped.forEach { (domain, domainCookies) ->
                item(key = "header-$domain") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = domain,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${domainCookies.size} 件",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(domainCookies, key = { "${it.domain}-${it.path}-${it.name}" }) { cookie ->
                    CookieRow(
                        cookie = cookie,
                        onDelete = {
                            scope.launch {
                                repository.deleteCookie(cookie.domain, cookie.path, cookie.name)
                                reload()
                                snackbarHostState.showSnackbar("削除しました: ${cookie.name}")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CookieRow(
    cookie: StoredCookie,
    onDelete: () -> Unit
) {
    val expiresLabel = cookie.expiresAtMillis?.let {
        val instant = KotlinxInstant.fromEpochMilliseconds(it)
        val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        "${local.year}/${local.monthNumber.toString().padStart(2, '0')}/${local.dayOfMonth.toString().padStart(2, '0')} ${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
    } ?: "セッション"

    ListItem(
        leadingContent = {
            Icon(
                imageVector = Icons.Rounded.History,
                contentDescription = null
            )
        },
        headlineContent = {
            Text(cookie.name)
        },
        supportingContent = {
            Column {
                Text("${cookie.path} / $expiresLabel")
                Text(
                    text = cookie.value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Rounded.DeleteSweep,
                    contentDescription = "削除"
                )
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}
