package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.model.ThreadMenuEntryConfig
import com.valoser.futacha.shared.model.ThreadMenuEntryId
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThreadSettingsSheet(
    onDismiss: () -> Unit,
    menuEntries: List<ThreadMenuEntryConfig>,
    onAction: (ThreadMenuEntryId) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val visibleItems = remember(menuEntries) {
        resolveThreadSettingsMenuEntries(menuEntries)
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "設定メニュー",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            visibleItems.forEach { menuItem ->
                val meta = menuItem.id.toMeta()
                ListItem(
                    leadingContent = { Icon(imageVector = meta.icon, contentDescription = null) },
                    headlineContent = { Text(meta.label) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAction(menuItem.id) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThreadFilterSheet(
    selectedOptions: Set<ThreadFilterOption>,
    activeSortOption: ThreadFilterSortOption?,
    keyword: String,
    onOptionToggle: (ThreadFilterOption) -> Unit,
    onKeywordChange: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "レスフィルター",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "絞り込みたい条件をタップしてオン／オフしてください",
                style = MaterialTheme.typography.bodySmall
            )
            HorizontalDivider()
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ThreadFilterOption.entries.forEachIndexed { index, option ->
                    val selected = option in selectedOptions
                    val isActiveSort = option.sortOption != null && activeSortOption == option.sortOption
                    ListItem(
                        leadingContent = {
                            Icon(imageVector = option.icon, contentDescription = null)
                        },
                        headlineContent = {
                            Text(option.label)
                        },
                        supportingContent = {
                            if (isActiveSort) {
                                Text(
                                    text = "表示: ${option.sortOption.displayLabel}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        },
                        trailingContent = {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "選択済み",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isActiveSort) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                } else {
                                    Color.Transparent
                                }
                            )
                            .clickable { onOptionToggle(option) }
                    )
                    if (index < ThreadFilterOption.entries.lastIndex) {
                        HorizontalDivider()
                    }
                    if (option == ThreadFilterOption.Keyword && selected) {
                        OutlinedTextField(
                            value = keyword,
                            onValueChange = onKeywordChange,
                            label = { Text("キーワード") },
                            placeholder = { Text("表示したいキーワードをカンマ区切りで") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )
                    }
                }
            }
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onClear) {
                    Text("フィルターをクリア")
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text("閉じる")
                }
            }
        }
    }
}

@Composable
internal fun ReadAloudIndicator(
    segment: ReadAloudSegment,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .wrapContentHeight(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)
    ) {
        Text(
            text = "読み上げ中: No.${segment.postId}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReadAloudControlSheet(
    segments: List<ReadAloudSegment>,
    currentIndex: Int,
    visibleSegmentIndex: Int,
    status: ReadAloudStatus,
    onSeek: (Int) -> Unit,
    onSeekToVisible: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val controlState = remember(segments, currentIndex, visibleSegmentIndex, status) {
        resolveReadAloudControlState(
            segments = segments,
            currentIndex = currentIndex,
            visibleSegmentIndex = visibleSegmentIndex,
            status = status
        )
    }
    var sliderValue by remember { mutableFloatStateOf(controlState.sliderValue) }
    LaunchedEffect(currentIndex, segments.size) {
        sliderValue = controlState.sliderValue
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "読み上げプレーヤー",
                style = MaterialTheme.typography.titleMedium
            )
            if (controlState.totalSegments > 0) {
                Text(
                    text = "進捗 ${controlState.completedSegments} / ${controlState.totalSegments}",
                    style = MaterialTheme.typography.bodyMedium
                )
                LinearProgressIndicator(
                    progress = { controlState.completedSegments / controlState.totalSegments.toFloat() },
                    modifier = Modifier.fillMaxWidth()
                )
                if (controlState.canSeek) {
                    val maxIndex = (controlState.totalSegments - 1).coerceAtLeast(0)
                    Slider(
                        value = sliderValue.coerceIn(0f, maxIndex.toFloat()),
                        onValueChange = { sliderValue = it },
                        onValueChangeFinished = {
                            onSeek(sliderValue.roundToInt())
                        },
                        valueRange = 0f..maxIndex.toFloat(),
                        steps = (maxIndex - 1).coerceAtLeast(0),
                        colors = SliderDefaults.colors(
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "No.${segments.getOrNull(sliderValue.roundToInt())?.postId ?: "-"} から",
                            style = MaterialTheme.typography.bodySmall
                        )
                        val visibleLabel = controlState.visiblePostId
                        if (controlState.canSeekToVisible && visibleLabel != null) {
                            TextButton(onClick = onSeekToVisible) {
                                Text("表示位置 (No.$visibleLabel) へ移動")
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                controlState.currentSegment?.let {
                    Text(
                        text = "現在: No.${it.postId}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                Text(
                    text = "読み上げ対象スレッドがありません",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onPlay,
                    enabled = controlState.isPlayEnabled,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(controlState.playLabel)
                }
                OutlinedButton(
                    onClick = onPause,
                    enabled = controlState.isPauseEnabled,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("一時停止")
                }
                OutlinedButton(
                    onClick = onStop,
                    enabled = controlState.isStopEnabled,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("停止")
                }
            }
        }
    }
}
