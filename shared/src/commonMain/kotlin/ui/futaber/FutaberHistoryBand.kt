package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valoser.futacha.shared.ui.board.HistoryLifeFilter
import com.valoser.futacha.shared.ui.board.HistorySortDirection
import com.valoser.futacha.shared.ui.board.HistorySortOption
import com.valoser.futacha.shared.ui.board.HistoryViewSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** The next entry of [entries] after [current] (wrapping around), for a chip that cycles through its choices. */
internal fun <T> futaberNextChoice(entries: List<T>, current: T): T =
    entries[(entries.indexOf(current) + 1).mod(entries.size)]

/**
 * The band above the history list (an extension, off by default): a title search, the sort and filter choices ふたちゃ has,
 * and "一括更新" (the same history refresh ふたちゃ runs from its drawer). Each chip cycles through its choices when tapped.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FutaberHistoryBand(
    settings: HistoryViewSettings,
    onSettingsChange: (HistoryViewSettings) -> Unit,
    shownCount: Int,
    totalCount: Int,
    onRefresh: (suspend () -> Unit)?,
    /** False when only the archive extension is on (or nothing is listed yet): the search and the chips are not shown. */
    showViewTools: Boolean = true,
    /** "全件削除" (asks first); null hides it. */
    onClearAll: (() -> Unit)? = null,
    /** "アーカイブ" (the history archive's export / import); null hides it. */
    onOpenArchive: (() -> Unit)? = null
) {
    val colors = LocalFutaberColors.current
    // The refresh runs in the app's scope and its state lives there (re-audit M19: closing the panel stopped it without a
    // word, and opening it again offered a second run beside the first). Without the app's tasks (previews) it is local.
    val appTasks = LocalFutaberAppTasks.current
    val localScope = rememberCoroutineScope()
    val tasks = appTasks ?: remember(localScope) { FutaberAppTasks(localScope) {} }
    var bandShown by remember { mutableStateOf(true) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        // A band opened again starts without the last result, as before.
        if (!tasks.historyRefreshing) tasks.historyRefreshMessage = null
        onDispose { bandShown = false }
    }
    val refreshing = tasks.historyRefreshing
    val refreshMessage = tasks.historyRefreshMessage
    Column(Modifier.fillMaxWidth().background(colors.bar).padding(horizontal = 12.dp, vertical = 8.dp).testTag("futaber-history-band")) {
        if (showViewTools) Row(
            Modifier.fillMaxWidth().clip(FutaberShapes.pill).background(colors.catalogGap)
                .heightIn(min = 40.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FutaberIcon(Icons.Outlined.Search, contentDescription = null, tint = colors.meta)
            Box(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                BasicTextField(
                    value = settings.titleQuery,
                    onValueChange = { onSettingsChange(settings.copy(titleQuery = it)) },
                    singleLine = true,
                    textStyle = TextStyle(color = colors.body, fontSize = 14.sp, fontFamily = LocalFutaberCustomFont.current),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {}),
                    modifier = Modifier.fillMaxWidth()
                        .semantics { contentDescription = "履歴をスレタイで検索" }
                        .testTag("futaber-history-search"),
                    decorationBox = { inner ->
                        if (settings.titleQuery.isEmpty()) Text("スレタイで検索", color = colors.meta, fontSize = 14.sp)
                        inner()
                    }
                )
            }
        }
        FlowRow(
            Modifier.padding(top = if (showViewTools) 8.dp else 0.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (showViewTools) BandChip("並び：${settings.sortOption.label}", "futaber-history-sort", false) {
                onSettingsChange(settings.copy(sortOption = futaberNextChoice(HistorySortOption.entries, settings.sortOption)))
            }
            if (showViewTools) BandChip(settings.sortDirection.label, "futaber-history-direction", false) {
                onSettingsChange(settings.copy(sortDirection = futaberNextChoice(HistorySortDirection.entries, settings.sortDirection)))
            }
            if (showViewTools) BandChip("自分が書き込んだスレ", "futaber-history-self", settings.selfPostsOnly) {
                onSettingsChange(settings.copy(selfPostsOnly = !settings.selfPostsOnly))
            }
            if (showViewTools) BandChip("状態：${settings.lifeFilter.label}", "futaber-history-life", settings.lifeFilter != HistoryLifeFilter.All) {
                onSettingsChange(settings.copy(lifeFilter = futaberNextChoice(HistoryLifeFilter.entries, settings.lifeFilter)))
            }
            if (onRefresh != null) {
                BandChip(if (refreshing) "更新中…" else "一括更新", "futaber-history-refresh", refreshing) {
                    if (!tasks.historyRefreshing) {
                        tasks.historyRefreshing = true
                        tasks.historyRefreshMessage = null
                        tasks.scope.launch {
                            val message = try {
                                onRefresh()
                                "履歴を更新しました"
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Throwable) {
                                "更新できませんでした：" + futaberFriendlyLoadError(error, "スレッド", "通信を確認してください")
                            } finally {
                                tasks.historyRefreshing = false
                            }
                            // Shown in the band while it is open; told as the app's notice once the panel has closed.
                            tasks.historyRefreshMessage = message
                            if (!bandShown) tasks.notify(message)
                        }
                    }
                }
            }
            // The extensions of the batch B4 (FutaberParityB45.kt): after the chips that were there before.
            if (onOpenArchive != null) BandChip("アーカイブ", "futaber-history-archive", false, onOpenArchive)
            if (onClearAll != null) BandChip("全件削除", "futaber-history-clear-all", false, onClearAll)
        }
        val status = refreshMessage ?: if (settings.isDefault || !showViewTools) null else "${shownCount}件 / 全${totalCount}件"
        status?.let {
            Text(it, color = colors.meta, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp).testTag("futaber-history-status"))
        }
    }
}

@Composable
private fun BandChip(label: String, tag: String, active: Boolean, onClick: () -> Unit) {
    val colors = LocalFutaberColors.current
    Text(
        label,
        color = if (active) colors.onAction else colors.body,
        fontSize = 12.sp,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .clip(FutaberShapes.pill)
            .background(if (active) colors.action else colors.background)
            .border(1.dp, if (active) colors.action else colors.separator, FutaberShapes.pill)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .heightIn(min = 32.dp)
            .padding(horizontal = 12.dp, vertical = 7.dp)
            .testTag(tag)
    )
}

/** The marks of a history row (an extension): the thread fell off the board, the person wrote in it, a copy is saved. */
internal fun futaberHistoryMarks(entry: com.valoser.futacha.shared.model.ThreadHistoryEntry): String =
    listOfNotNull(
        "落ちた".takeIf { entry.isAutoRefreshDisabled },
        "書き込み済み".takeIf { entry.hasSelfPost },
        "保存済み".takeIf { entry.hasAutoSave }
    ).joinToString("・")
