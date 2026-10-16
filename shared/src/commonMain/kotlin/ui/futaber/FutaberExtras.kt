package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valoser.futacha.shared.compat.CompatVolumeKey
import com.valoser.futacha.shared.compat.CompatVolumeKeyBus
import com.valoser.futacha.shared.compat.runCompatAutoScroll
import kotlinx.coroutines.launch
import com.valoser.futacha.shared.ui.compat.CompatForegroundLifecycleEffect
import kotlinx.coroutines.flow.first

/**
 * The auto-scroll (an extension, off by default): the loop ふたちゃ and としあき(仮) run. It waits while the app is in the
 * background (neither scrolling nor reloading then) and while a finger drags the list, reloads when the bottom is reached,
 * and stops by itself when the thread has fallen off the board.
 */
@Composable
internal fun FutaberAutoScrollEffect(
    active: Boolean,
    pixel: Int,
    intervalMillis: Long,
    isDead: Boolean,
    listState: LazyListState,
    onReload: () -> Unit,
    onStop: () -> Unit
) {
    var foreground by remember { mutableStateOf(true) }
    CompatForegroundLifecycleEffect { foreground = it }
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    val latestDragged by rememberUpdatedState(dragged)
    val latestDead by rememberUpdatedState(isDead)
    val latestReload by rememberUpdatedState(onReload)
    val latestStop by rememberUpdatedState(onStop)
    LaunchedEffect(active, pixel, intervalMillis) {
        if (!active) return@LaunchedEffect
        runCompatAutoScroll(
            isAutoScrolling = { active },
            awaitForeground = { snapshotFlow { foreground }.first { it } },
            canScrollForward = { listState.canScrollForward },
            isDead = { latestDead },
            stepDelayMillis = intervalMillis,
            scrollStep = { if (!latestDragged) listState.scrollBy(pixel.toFloat()) },
            reload = { latestReload() },
            stopDead = latestStop
        )
    }
}

/** The pill shown while the auto-scroll runs; tapping it stops the scroll. */
@Composable
internal fun FutaberAutoScrollChip(onStop: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalFutaberColors.current
    Row(
        modifier.padding(8.dp)
            .background(colors.bar, FutaberShapes.pill)
            .border(1.dp, colors.separator, FutaberShapes.pill)
            .clickable(onClickLabel = "オートスクロールを停止", onClick = onStop)
            .heightIn(min = 40.dp)
            .padding(horizontal = 14.dp)
            .testTag("futaber-auto-scroll-chip"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("オートスクロール中", color = colors.body, fontSize = 12.sp)
        Text(" 停止", color = colors.link, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * The volume keys (an extension, off by default): the Android host hands them to whoever registered on
 * [CompatVolumeKeyBus]. [mode] says what they do: a screen back / on (the default, as before), a post back / on, or the
 * previous / next thread of the tab strip ([onSwitchThread]; nothing happens while the open thread is not a tab, and the
 * key is still taken so the volume does not change). Nothing is registered while it is off, so the keys keep changing
 * the volume.
 */
@Composable
internal fun FutaberVolumeKeyEffect(
    enabled: Boolean,
    listState: LazyListState,
    mode: FutaberVolumeKeyMode = FutaberVolumeKeyMode.Screen,
    onSwitchThread: ((forward: Boolean) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val owner = remember { Any() }
    val latestSwitch by rememberUpdatedState(onSwitchThread)
    // The list of the thread shown now (re-audit M18: after moving to another thread the keys kept scrolling the first
    // thread's list, which is no longer on screen).
    val currentListState by rememberUpdatedState(listState)
    DisposableEffect(enabled, mode) {
        if (enabled) {
            CompatVolumeKeyBus.register(owner) { key ->
                val up = key == CompatVolumeKey.UP
                val listState = currentListState
                when (mode) {
                    FutaberVolumeKeyMode.Thread -> {
                        latestSwitch?.invoke(!up)
                        true
                    }
                    FutaberVolumeKeyMode.Response -> {
                        val info = listState.layoutInfo
                        if (info.viewportSize.height <= 0 || info.totalItemsCount <= 0) return@register false
                        val target = futaberResponseStepTarget(
                            listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, info.totalItemsCount - 1, up
                        )
                        scope.launch { listState.animateScrollToItem(target) }
                        true
                    }
                    FutaberVolumeKeyMode.Screen -> {
                        if (listState.layoutInfo.viewportSize.height <= 0) return@register false
                        // The same page the operation menu's "1ページ上へ／下へ" scrolls.
                        scope.launch { futaberScrollPage(listState, up = up) }
                        true
                    }
                }
            }
        }
        onDispose { CompatVolumeKeyBus.unregister(owner) }
    }
}
