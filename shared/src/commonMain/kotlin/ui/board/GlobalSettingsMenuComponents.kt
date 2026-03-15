package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.model.CatalogNavEntryConfig
import com.valoser.futacha.shared.model.CatalogNavEntryPlacement
import com.valoser.futacha.shared.model.ThreadMenuEntryConfig
import com.valoser.futacha.shared.model.ThreadMenuEntryPlacement

@Composable
internal fun GlobalSettingsCatalogMenuSection(
    localCatalogNavEntries: List<CatalogNavEntryConfig>,
    catalogMenuCallbacks: GlobalSettingsCatalogMenuCallbacks
) {
    val catalogMenuState = remember(localCatalogNavEntries) {
        resolveCatalogMenuConfigState(localCatalogNavEntries)
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
            TextButton(onClick = catalogMenuCallbacks.resetEntries) {
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
            if (catalogMenuState.barEntries.isEmpty()) {
                Text("なし", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            } else {
                catalogMenuState.barEntries.forEach { entry ->
                    val meta = entry.id.toMeta()
                    Icon(imageVector = meta.icon, contentDescription = meta.label, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        if (catalogMenuState.hiddenEntries.isNotEmpty()) {
            Text(
                text = "非表示: ${catalogMenuState.hiddenEntries.size} 件",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        catalogMenuState.allEntries.forEach { item ->
            val meta = item.id.toMeta()
            val barIndex = catalogMenuState.barEntries.indexOfFirst { it.id == item.id }
            val canMoveLeft = item.placement == CatalogNavEntryPlacement.BAR && barIndex > 0
            val canMoveRight =
                item.placement == CatalogNavEntryPlacement.BAR &&
                    barIndex in 0 until catalogMenuState.barEntries.lastIndex
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
                        onClick = { catalogMenuCallbacks.moveEntry(item.id, -1) },
                        enabled = canMoveLeft
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "左へ移動")
                    }
                    IconButton(
                        onClick = { catalogMenuCallbacks.moveEntry(item.id, 1) },
                        enabled = canMoveRight
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "右へ移動")
                    }
                    AssistChip(
                        onClick = { catalogMenuCallbacks.setPlacement(item.id, CatalogNavEntryPlacement.BAR) },
                        label = { Text("バー") },
                        leadingIcon = if (item.placement == CatalogNavEntryPlacement.BAR) {
                            { Icon(Icons.Rounded.Check, contentDescription = null) }
                        } else {
                            null
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (item.placement == CatalogNavEntryPlacement.BAR) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    )
                    AssistChip(
                        onClick = { catalogMenuCallbacks.setPlacement(item.id, CatalogNavEntryPlacement.HIDDEN) },
                        label = { Text("非表示") },
                        leadingIcon = if (item.placement == CatalogNavEntryPlacement.HIDDEN) {
                            { Icon(Icons.Rounded.Check, contentDescription = null) }
                        } else {
                            null
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (item.placement == CatalogNavEntryPlacement.HIDDEN) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    )
                }
            }
            HorizontalDivider()
        }
    }
}

@Composable
internal fun GlobalSettingsThreadMenuSection(
    localThreadMenuEntries: List<ThreadMenuEntryConfig>,
    threadMenuCallbacks: GlobalSettingsThreadMenuCallbacks
) {
    val threadMenuState = remember(localThreadMenuEntries) {
        resolveThreadMenuConfigState(localThreadMenuEntries)
    }
    SettingsSection(
        title = "スレッドメニュー構成",
        icon = Icons.AutoMirrored.Rounded.ViewList,
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
            TextButton(onClick = threadMenuCallbacks.resetEntries) {
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
            if (threadMenuState.barEntries.isEmpty()) {
                Text("なし", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            } else {
                threadMenuState.barEntries.forEach { entry ->
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
            if (threadMenuState.sheetEntries.isEmpty()) {
                Text("なし", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            } else {
                threadMenuState.sheetEntries.forEach { entry ->
                    val meta = entry.toMeta()
                    Icon(imageVector = meta.icon, contentDescription = meta.label, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        threadMenuState.allEntries.forEach { item ->
            val meta = item.toMeta()
            val placement = item.placement
            val barIndex = threadMenuState.barEntries.indexOfFirst { it.id == item.id }
            val sheetIndex = threadMenuState.sheetEntries.indexOfFirst { it.id == item.id }
            val canMoveLeft = placement == ThreadMenuEntryPlacement.BAR && barIndex > 0 ||
                placement == ThreadMenuEntryPlacement.SHEET && sheetIndex > 0
            val canMoveRight =
                placement == ThreadMenuEntryPlacement.BAR && barIndex in 0 until threadMenuState.barEntries.lastIndex ||
                    placement == ThreadMenuEntryPlacement.SHEET && sheetIndex in 0 until threadMenuState.sheetEntries.lastIndex
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
                                ThreadMenuEntryPlacement.BAR -> threadMenuCallbacks.moveWithinPlacement(item.id, -1, ThreadMenuEntryPlacement.BAR)
                                ThreadMenuEntryPlacement.SHEET -> threadMenuCallbacks.moveWithinPlacement(item.id, -1, ThreadMenuEntryPlacement.SHEET)
                                ThreadMenuEntryPlacement.HIDDEN -> Unit
                            }
                        },
                        enabled = canMoveLeft
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "左へ移動")
                    }
                    IconButton(
                        onClick = {
                            when (placement) {
                                ThreadMenuEntryPlacement.BAR -> threadMenuCallbacks.moveWithinPlacement(item.id, 1, ThreadMenuEntryPlacement.BAR)
                                ThreadMenuEntryPlacement.SHEET -> threadMenuCallbacks.moveWithinPlacement(item.id, 1, ThreadMenuEntryPlacement.SHEET)
                                ThreadMenuEntryPlacement.HIDDEN -> Unit
                            }
                        },
                        enabled = canMoveRight
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "右へ移動")
                    }
                    AssistChip(
                        onClick = { threadMenuCallbacks.setPlacement(item.id, ThreadMenuEntryPlacement.BAR) },
                        label = { Text("バー") },
                        leadingIcon = if (placement == ThreadMenuEntryPlacement.BAR) {
                            { Icon(Icons.Rounded.Check, contentDescription = null) }
                        } else {
                            null
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (placement == ThreadMenuEntryPlacement.BAR) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    )
                    AssistChip(
                        onClick = { threadMenuCallbacks.setPlacement(item.id, ThreadMenuEntryPlacement.SHEET) },
                        label = { Text("設定") },
                        leadingIcon = if (placement == ThreadMenuEntryPlacement.SHEET) {
                            { Icon(Icons.Rounded.Check, contentDescription = null) }
                        } else {
                            null
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (placement == ThreadMenuEntryPlacement.SHEET) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    )
                    AssistChip(
                        onClick = { threadMenuCallbacks.setPlacement(item.id, ThreadMenuEntryPlacement.HIDDEN) },
                        label = { Text("非表示") },
                        leadingIcon = if (placement == ThreadMenuEntryPlacement.HIDDEN) {
                            { Icon(Icons.Rounded.Check, contentDescription = null) }
                        } else {
                            null
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (placement == ThreadMenuEntryPlacement.HIDDEN) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    )
                }
            }
            HorizontalDivider()
        }
        if (threadMenuState.hiddenEntries.isNotEmpty()) {
            Text(
                text = "非表示: ${threadMenuState.hiddenEntries.size} 件",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
