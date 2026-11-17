package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import com.valoser.futacha.shared.ui.compat.rememberCompatShareLauncher
import com.valoser.futacha.shared.ui.futaber.mht.FutaberMhtEntry
import com.valoser.futacha.shared.ui.futaber.mht.FutaberMhtLibrary
import com.valoser.futacha.shared.ui.futaber.mht.FutaberMhtOpened
import com.valoser.futacha.shared.ui.futaber.mht.futaberMhtDetail
import com.valoser.futacha.shared.ui.futaber.mht.futaberMhtSummary
import com.valoser.futacha.shared.ui.futaber.mht.futaberMhtThumbnailUri
import com.valoser.futacha.shared.ui.futaber.mht.rememberFutaberMhtPickerLauncher
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * The "MHTファイル" part of ふたちゃ's and としあき(仮)'s saved-thread list: the MHT files with their size, a button
 * that takes a file in from the files app, and per file: open (in the app), share, delete. Which screen shows an
 * opened file is the caller's choice ([onOpened]).
 */
@Composable
internal fun FutachaMhtSection(
    library: FutaberMhtLibrary,
    onOpened: (FutaberMhtOpened) -> Unit,
    /**
     * True where an opened file's pictures are kept by a tab (としあき(仮)): they are written to the folder that stays while the
     * tab refers to them, not to the viewer's cache that is cleaned up.
     */
    keepMediaForTab: Boolean = false,
    /** Where a message goes (the saved list's own bar); null shows it in this section. */
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var entries by remember(library) { mutableStateOf<List<FutaberMhtEntry>>(emptyList()) }
    var tick by remember(library) { mutableStateOf(0) }
    var busy by remember(library) { mutableStateOf<String?>(null) }
    var message by remember(library) { mutableStateOf<String?>(null) }
    var deleteTarget by remember(library) { mutableStateOf<FutaberMhtEntry?>(null) }
    val share = rememberCompatShareLauncher()
    val picker = rememberFutaberMhtPickerLauncher(
        onSelected = { picked ->
            if (busy == null) scope.launch {
                busy = "取り込んでいます…"
                library.import(picked.bytes, picked.fileName, Clock.System.now().toEpochMilliseconds())
                    .onSuccess { message = "MHTファイルを追加しました"; tick += 1 }
                    .onFailure { message = it.message ?: "MHTファイルを取り込めませんでした" }
                busy = null
            }
        },
        onError = { message = it }
    )
    LaunchedEffect(library, tick) {
        entries = try {
            library.list()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("futacha-mht-section"),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp
    ) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    futaberMhtSummary(entries, busy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).testTag("futacha-mht-summary")
                )
                TextButton(onClick = picker, enabled = busy == null, modifier = Modifier.testTag("futacha-mht-import")) {
                    Text("ファイルを開く")
                }
            }
            message?.let {
                Text(
                    it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().clickable { message = null }.padding(horizontal = 16.dp, vertical = 4.dp)
                        .testTag("futacha-mht-message")
                )
            }
            if (entries.isNotEmpty()) {
                HorizontalDivider()
                LazyColumn(Modifier.heightIn(max = 240.dp).testTag("futacha-mht-list")) {
                    items(entries, key = { it.fileName }) { entry ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable(enabled = busy == null, onClickLabel = "MHTファイルを開く") {
                                    busy = "開いています…"
                                    scope.launch {
                                        library.open(entry, keepForTab = keepMediaForTab)
                                            .onSuccess { onOpened(it) }
                                            .onFailure { message = it.message ?: "MHTファイルを開けませんでした" }
                                        busy = null
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("futacha-mht-row"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(48.dp).clip(MaterialTheme.shapes.small)) {
                                CatalogPreviewImage(
                                    thumbnailUrl = futaberMhtThumbnailUri(entry).ifBlank { null },
                                    fullImageUrl = null,
                                    targetSizePx = 150,
                                    contentDescription = "",
                                    modifier = Modifier.size(48.dp),
                                    fallbackTint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(entry.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    listOf(entry.boardName, futaberMhtDetail(entry)).filter { it.isNotBlank() }.joinToString("  "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { share(entry.title, "multipart/related", library.absolutePath(entry)) },
                                modifier = Modifier.testTag("futacha-mht-share-row")
                            ) { Icon(Icons.Rounded.IosShare, contentDescription = "${entry.title}を共有") }
                            IconButton(onClick = { deleteTarget = entry }, modifier = Modifier.testTag("futacha-mht-delete")) {
                                Icon(Icons.Rounded.Delete, contentDescription = "${entry.title}を削除")
                            }
                        }
                    }
                }
            }
        }
    }
    deleteTarget?.let { entry ->
        FutachaAppLockAwareWindow { AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("MHTファイルを削除しますか？") },
            text = { Text(entry.title) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        scope.launch {
                            library.delete(entry).onFailure { message = it.message ?: "削除できませんでした" }
                            tick += 1
                        }
                    },
                    modifier = Modifier.testTag("futacha-mht-delete-confirm")
                ) { Text("削除") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("キャンセル") } }
        ) }
    }
}
