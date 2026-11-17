package com.valoser.futacha.shared.ui.ftutter

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WebAsset
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow

/** スレッドのツールバーの項目のアイコン（ツールバーと編集画面で同じもの）。 */
internal fun ftutterThreadToolIcon(tool: FtutterThreadTool, active: Boolean = false): ImageVector = when (tool) {
    FtutterThreadTool.DRAWER -> Icons.Rounded.Menu
    FtutterThreadTool.TREE -> Icons.Rounded.AccountTree
    FtutterThreadTool.POPULAR -> Icons.Rounded.Star
    FtutterThreadTool.IMAGE_POSTS -> Icons.Rounded.Image
    FtutterThreadTool.TAB -> Icons.Rounded.Tab
    FtutterThreadTool.PRIVACY -> if (active) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility
    FtutterThreadTool.UP -> Icons.Rounded.KeyboardArrowUp
    FtutterThreadTool.DOWN -> Icons.Rounded.KeyboardArrowDown
    FtutterThreadTool.GALLERY -> Icons.Rounded.Collections
    FtutterThreadTool.REPLY -> Icons.Rounded.Edit
    FtutterThreadTool.SEARCH -> Icons.Rounded.Search
    FtutterThreadTool.REFRESH -> Icons.Rounded.Refresh
    FtutterThreadTool.SPACE, FtutterThreadTool.MENU -> Icons.Rounded.MoreVert
}

/** カタログのツールバーの項目のアイコン。 */
internal fun ftutterCatalogToolIcon(tool: FtutterCatalogTool, active: Boolean = false): ImageVector = when (tool) {
    FtutterCatalogTool.DRAWER -> Icons.Rounded.Menu
    FtutterCatalogTool.ORDER -> Icons.AutoMirrored.Rounded.Sort
    FtutterCatalogTool.TAB -> Icons.Rounded.WebAsset
    FtutterCatalogTool.PRIVACY -> if (active) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility
    FtutterCatalogTool.UP -> Icons.Rounded.KeyboardArrowUp
    FtutterCatalogTool.DOWN -> Icons.Rounded.KeyboardArrowDown
    FtutterCatalogTool.CREATE -> Icons.Rounded.Edit
    FtutterCatalogTool.SEARCH -> Icons.Rounded.Search
    FtutterCatalogTool.REFRESH -> Icons.Rounded.Refresh
    FtutterCatalogTool.SPACE, FtutterCatalogTool.MENU -> Icons.Rounded.MoreVert
}

/** 編集画面の行の名前（原作：「画像レス抽出 / 長押しで画像一覧」）。 */
internal fun ftutterThreadToolEditorLabel(tool: FtutterThreadTool): String =
    if (tool == FtutterThreadTool.IMAGE_POSTS) "画像レス抽出 / 長押しで画像一覧" else tool.label

/** 1つ動かす（並べ替えの純関数。範囲外は何もしない）。 */
internal fun <T> List<T>.ftutterMoved(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices || from == to) return this
    return toMutableList().also { it.add(to, it.removeAt(from)) }
}

/** ツールバーの寸法の範囲（原作のスライダー：幅・高さ 30〜80、スペース 0〜100 を5ずつ）。 */
internal const val FTUTTER_TOOLBAR_SIZE_MIN = 30
internal const val FTUTTER_TOOLBAR_SIZE_MAX = 80
internal const val FTUTTER_TOOLBAR_SPACE_MAX = 100

/**
 * M79・M29・M44：原作の「ツールバー編集」「レス長押しメニュー編集」は全画面。上のバー（←と題）、
 * 行（[toggles] の時はスイッチ、アイコン、名前、右のつまみ ≡ をドラッグして並べ替え）、下に [bottom]（寸法と見本）と「リセット」「キャンセル」「OK」。
 * 保存はOKの時だけ。リセットは画面の中で既定に戻す。
 */
@Composable
internal fun <T> FtutterOrderEditorScreen(
    title: String,
    tag: String,
    entries: List<FtutterToolbarEntry<T>>,
    defaults: List<FtutterToolbarEntry<T>>,
    label: (T) -> String,
    icon: (T) -> ImageVector?,
    toggles: Boolean,
    onCancel: () -> Unit,
    onReset: () -> Unit = {},
    onOk: (List<FtutterToolbarEntry<T>>) -> Unit,
    bottom: @Composable (List<FtutterToolbarEntry<T>>) -> Unit
) {
    val colors = LocalFtutterColors.current
    var current by remember { mutableStateOf(entries) }
    FutachaAppLockAwareWindow {
        Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(Modifier.fillMaxSize().background(colors.background).testTag(tag)) {
                FtutterTopBar(title = title, onBack = onCancel)
                FtutterReorderList(current, label, icon, toggles, Modifier.weight(1f)) { current = it }
                HorizontalDivider(color = colors.outline, thickness = 2.dp)
                bottom(current)
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { current = defaults; onReset() }, modifier = Modifier.testTag("ftutter-order-reset")) { Text("リセット") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onCancel, modifier = Modifier.testTag("ftutter-order-cancel")) { Text("キャンセル") }
                    TextButton(onClick = { onOk(current) }, modifier = Modifier.testTag("ftutter-order-save")) { Text("OK") }
                }
            }
        }
    }
}

private val FTUTTER_REORDER_ROW_HEIGHT = 56.dp

@Composable
private fun <T> FtutterReorderList(
    entries: List<FtutterToolbarEntry<T>>,
    label: (T) -> String,
    icon: (T) -> ImageVector?,
    toggles: Boolean,
    modifier: Modifier,
    onChange: (List<FtutterToolbarEntry<T>>) -> Unit
) {
    val colors = LocalFtutterColors.current
    val latest by rememberUpdatedState(entries)
    val change by rememberUpdatedState(onChange)
    val rowPx = with(LocalDensity.current) { FTUTTER_REORDER_ROW_HEIGHT.toPx() }
    var dragging by remember { mutableIntStateOf(-1) }
    var offset by remember { mutableFloatStateOf(0f) }
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        entries.forEachIndexed { index, entry ->
            val name = label(entry.tool)
            Column(
                Modifier.graphicsLayer { translationY = if (index == dragging) offset else 0f }
                    .background(colors.background)
                    .testTag("ftutter-order-row")
                    .semantics {
                        customActions = listOfNotNull(
                            if (index > 0) CustomAccessibilityAction("上へ") { change(latest.ftutterMoved(index, index - 1)); true } else null,
                            if (index < entries.lastIndex) CustomAccessibilityAction("下へ") { change(latest.ftutterMoved(index, index + 1)); true } else null
                        )
                    }
            ) {
                Row(Modifier.fillMaxWidth().height(FTUTTER_REORDER_ROW_HEIGHT).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (toggles) {
                        Switch(
                            checked = entry.enabled,
                            onCheckedChange = { on -> change(latest.toMutableList().also { it[index] = entry.copy(enabled = on) }) },
                            modifier = Modifier.testTag("ftutter-order-switch-$index")
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        icon(entry.tool)?.let { Icon(it, contentDescription = null, tint = colors.text) }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(name, color = colors.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Box(
                        Modifier.size(48.dp).testTag("ftutter-order-handle-$index").pointerInput(index) {
                            detectDragGestures(
                                onDragStart = { dragging = index; offset = 0f },
                                onDragEnd = { dragging = -1; offset = 0f },
                                onDragCancel = { dragging = -1; offset = 0f }
                            ) { change, amount ->
                                change.consume()
                                offset += amount.y
                                val from = dragging
                                if (from < 0) return@detectDragGestures
                                val step = when {
                                    offset > rowPx / 2 && from < latest.lastIndex -> 1
                                    offset < -rowPx / 2 && from > 0 -> -1
                                    else -> 0
                                }
                                if (step != 0) {
                                    change(latest.ftutterMoved(from, from + step))
                                    dragging = from + step
                                    offset -= step * rowPx
                                }
                            }
                        },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.DragHandle, contentDescription = "並べ替え", tint = colors.text)
                    }
                }
                HorizontalDivider(color = colors.outline)
            }
        }
    }
}

/**
 * ツールバー編集の下：寸法のスライダー4本（ボタンの幅／ボタン高さ／スペース（左端）／スペース（右端））と、
 * 今の並び・寸法で描いた見本のツールバー。
 */
@Composable
internal fun <T> FtutterToolbarEditorBottom(
    entries: List<FtutterToolbarEntry<T>>,
    metrics: FtutterToolbarMetrics,
    onMetrics: (FtutterToolbarMetrics) -> Unit,
    isSpace: (T) -> Boolean,
    isOrder: (T) -> Boolean,
    icon: (T) -> ImageVector?
) {
    val colors = LocalFtutterColors.current
    Column(Modifier.fillMaxWidth()) {
        @Composable
        fun row(name: String, tag: String, value: Int, max: Int, min: Int, steps: Int, set: (Int) -> Unit) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name, color = colors.text, fontSize = 14.sp, modifier = Modifier.width(112.dp))
                Slider(
                    value = value.toFloat(), onValueChange = { set(it.toInt().coerceIn(min, max)) },
                    valueRange = min.toFloat()..max.toFloat(), steps = steps,
                    modifier = Modifier.weight(1f).testTag("ftutter-toolbar-$tag")
                )
            }
        }
        row("ボタンの幅", "width", metrics.iconWidth, FTUTTER_TOOLBAR_SIZE_MAX, FTUTTER_TOOLBAR_SIZE_MIN, 0) { onMetrics(metrics.copy(iconWidth = it)) }
        row("ボタン高さ", "height", metrics.iconHeight, FTUTTER_TOOLBAR_SIZE_MAX, FTUTTER_TOOLBAR_SIZE_MIN, 0) { onMetrics(metrics.copy(iconHeight = it)) }
        row("スペース（左端）", "left", metrics.leftSpace, FTUTTER_TOOLBAR_SPACE_MAX, 0, 19) { onMetrics(metrics.copy(leftSpace = it / 5 * 5)) }
        row("スペース（右端）", "right", metrics.rightSpace, FTUTTER_TOOLBAR_SPACE_MAX, 0, 19) { onMetrics(metrics.copy(rightSpace = it / 5 * 5)) }
        Box(Modifier.testTag("ftutter-toolbar-preview")) {
            FtutterBottomToolbarPreview(entries, metrics, isSpace, isOrder, icon)
        }
    }
}

@Composable
private fun <T> FtutterBottomToolbarPreview(
    entries: List<FtutterToolbarEntry<T>>,
    metrics: FtutterToolbarMetrics,
    isSpace: (T) -> Boolean,
    isOrder: (T) -> Boolean,
    icon: (T) -> ImageVector?
) {
    val colors = LocalFtutterColors.current
    val shown = entries.filter { it.enabled && !isSpace(it.tool) }
    if (shown.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().background(colors.toolBar).height(metrics.iconHeight.dp)
            .padding(start = metrics.leftSpace.dp, end = metrics.rightSpace.dp),
        horizontalArrangement = if (entries.any { it.enabled && isSpace(it.tool) }) Arrangement.Start else Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        entries.filter { it.enabled }.forEach { entry ->
            when {
                isSpace(entry.tool) -> Spacer(Modifier.weight(1f))
                isOrder(entry.tool) -> Text("カタ", color = colors.onToolBar, fontSize = 15.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.width(FTUTTER_TOOLBAR_ORDER_WIDTH.dp))
                else -> Box(Modifier.width(metrics.iconWidth.dp), contentAlignment = Alignment.Center) {
                    icon(entry.tool)?.let { Icon(it, contentDescription = null, tint = colors.onToolBar) }
                }
            }
        }
    }
}

/** レス長押しメニュー編集の下：3列の見本（並べたとおり、隠した項目は出さない）。 */
@Composable
internal fun FtutterPostMenuEditorBottom(entries: List<FtutterToolbarEntry<FtutterPostMenuItem>>) {
    val colors = LocalFtutterColors.current
    val shown = entries.filter { it.enabled }.map { it.tool }
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp).testTag("ftutter-post-menu-preview"), horizontalAlignment = Alignment.CenterHorizontally) {
        shown.chunked(3).forEach { line ->
            Row {
                line.forEach { item ->
                    Column(Modifier.width(96.dp).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(item.icon(), contentDescription = null, tint = colors.text)
                        Text(item.label, color = colors.text, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/**
 * 3つの編集画面をまとめて開く。「従来の動き」の「設定のダイアログを前の形にする」の時は、前のダイアログ（チェックと↑↓）。
 * レス長押しメニュー編集は原作どおり並べ替えだけで、隠した項目（その他の機能で選ぶ）はそのまま残す。
 */
@Composable
internal fun FtutterOrderEditorHost(
    kind: FtutterOrderEditorKind,
    preferences: Map<String, String>,
    save: (String, String) -> Unit,
    onClose: () -> Unit
) {
    val legacy = preferences.ftutterBool(FTUTTER_LEGACY_DIALOGS_KEY, false)
    fun <T> saveOrder(orderKey: String, enabledKey: String, entries: List<FtutterToolbarEntry<T>>, name: (T) -> String) {
        val (names, flags) = encodeFtutterToolbar(entries, name)
        save(orderKey, names); save(enabledKey, flags)
    }
    when (kind) {
        FtutterOrderEditorKind.POST_MENU -> if (legacy) {
            FtutterOrderEditorDialog(
                title = "レス長押しメニューの並び", entries = parseFtutterPostMenu(preferences), label = { it.label }, onDismiss = onClose,
                onReset = { onClose(); save(FtutterKeys.POST_MENU_ORDER, ""); save(FtutterKeys.POST_MENU_ENABLED, "") },
                onSave = { onClose(); saveOrder(FtutterKeys.POST_MENU_ORDER, FtutterKeys.POST_MENU_ENABLED, it) { tool -> tool.stored } }
            )
        } else {
            val entries = parseFtutterPostMenu(preferences)
            // リセットは並びだけを既定に戻す（隠した項目は保つ）。
            val hidden = entries.filterNot { it.enabled }.map { it.tool }.toSet()
            FtutterOrderEditorScreen(
                title = "レス長押しメニュー編集", tag = "ftutter-post-menu-editor", entries = entries,
                defaults = FTUTTER_POST_MENU_DEFAULT.map { it.copy(enabled = it.tool !in hidden) },
                label = { it.label }, icon = { it.icon() }, toggles = false, onCancel = onClose,
                onOk = { onClose(); saveOrder(FtutterKeys.POST_MENU_ORDER, FtutterKeys.POST_MENU_ENABLED, it) { tool -> tool.stored } }
            ) { FtutterPostMenuEditorBottom(it) }
        }
        FtutterOrderEditorKind.CATALOG -> if (legacy) {
            FtutterOrderEditorDialog(
                title = "ツールバー編集（カタログ）", entries = parseFtutterCatalogToolbar(preferences), label = { it.label }, onDismiss = onClose,
                onReset = { onClose(); save(FtutterKeys.CATALOG_TOOLBAR_ORDER, ""); save(FtutterKeys.CATALOG_TOOLBAR_ENABLED, "") },
                onSave = { onClose(); saveOrder(FtutterKeys.CATALOG_TOOLBAR_ORDER, FtutterKeys.CATALOG_TOOLBAR_ENABLED, it) { tool -> tool.stored } }
            )
        } else {
            var metrics by remember { mutableStateOf(parseFtutterCatalogToolbarMetrics(preferences)) }
            FtutterOrderEditorScreen(
                title = "ツールバー編集（カタログ）", tag = "ftutter-toolbar-editor", entries = parseFtutterCatalogToolbar(preferences),
                defaults = FTUTTER_CATALOG_TOOLBAR_DEFAULT, label = { it.label }, icon = { tool -> if (tool == FtutterCatalogTool.SPACE) null else ftutterCatalogToolIcon(tool) },
                toggles = true, onCancel = onClose, onReset = { metrics = FtutterToolbarMetrics() },
                onOk = {
                    onClose()
                    saveOrder(FtutterKeys.CATALOG_TOOLBAR_ORDER, FtutterKeys.CATALOG_TOOLBAR_ENABLED, it) { tool -> tool.stored }
                    saveToolbarMetrics(save, metrics, FtutterKeys.CATALOG_ICON_WIDTH, FtutterKeys.CATALOG_ICON_HEIGHT, FtutterKeys.CATALOG_TOOLBAR_LEFT, FtutterKeys.CATALOG_TOOLBAR_RIGHT)
                }
            ) { current ->
                FtutterToolbarEditorBottom(current, metrics, { metrics = it }, { it == FtutterCatalogTool.SPACE }, { it == FtutterCatalogTool.ORDER },
                    { tool -> ftutterCatalogToolIcon(tool) })
            }
        }
        FtutterOrderEditorKind.THREAD -> if (legacy) {
            FtutterOrderEditorDialog(
                title = "ツールバー編集（スレッド）", entries = parseFtutterThreadToolbar(preferences), label = { it.label }, onDismiss = onClose,
                onReset = { onClose(); save(FtutterKeys.THREAD_TOOLBAR_ORDER, ""); save(FtutterKeys.THREAD_TOOLBAR_ENABLED, "") },
                onSave = { onClose(); saveOrder(FtutterKeys.THREAD_TOOLBAR_ORDER, FtutterKeys.THREAD_TOOLBAR_ENABLED, it) { tool -> tool.stored } }
            )
        } else {
            var metrics by remember { mutableStateOf(parseFtutterThreadToolbarMetrics(preferences)) }
            FtutterOrderEditorScreen(
                title = "ツールバー編集（スレッド）", tag = "ftutter-toolbar-editor", entries = parseFtutterThreadToolbar(preferences),
                defaults = FTUTTER_THREAD_TOOLBAR_DEFAULT, label = { ftutterThreadToolEditorLabel(it) },
                icon = { tool -> if (tool == FtutterThreadTool.SPACE) null else ftutterThreadToolIcon(tool) },
                toggles = true, onCancel = onClose, onReset = { metrics = FtutterToolbarMetrics() },
                onOk = {
                    onClose()
                    saveOrder(FtutterKeys.THREAD_TOOLBAR_ORDER, FtutterKeys.THREAD_TOOLBAR_ENABLED, it) { tool -> tool.stored }
                    saveToolbarMetrics(save, metrics, FtutterKeys.THREAD_ICON_WIDTH, FtutterKeys.THREAD_ICON_HEIGHT, FtutterKeys.THREAD_TOOLBAR_LEFT, FtutterKeys.THREAD_TOOLBAR_RIGHT)
                }
            ) { current ->
                FtutterToolbarEditorBottom(current, metrics, { metrics = it }, { it == FtutterThreadTool.SPACE }, { false },
                    { tool -> ftutterThreadToolIcon(tool) })
            }
        }
    }
}

private fun saveToolbarMetrics(save: (String, String) -> Unit, m: FtutterToolbarMetrics, width: String, height: String, left: String, right: String) {
    save(width, m.iconWidth.toString()); save(height, m.iconHeight.toString())
    save(left, m.leftSpace.toString()); save(right, m.rightSpace.toString())
}
