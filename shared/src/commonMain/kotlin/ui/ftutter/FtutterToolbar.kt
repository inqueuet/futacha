package com.valoser.futacha.shared.ui.ftutter

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.ui.util.TOOLBAR_MIN_ITEM_WIDTH_DP

/**
 * 原作のツールバーの項目。並び・ON/OFFは `catTBOrderName`／`catTBOrderBool`（スレッドは `sureTB…`）に保存する。
 * 保存名はこのアプリ独自の固定ID。原作の `.ftt` を取り込む時は、原作の列挙名からこのIDへ対応付ける（第8段階）。
 */
internal enum class FtutterCatalogTool(val stored: String, val label: String) {
    DRAWER("drawer", "ドロワーを開く"),
    ORDER("order", "並び順"),
    TAB("tab", "タブ表示切り替え"),
    SPACE("space", "スペース（調整用）"),
    PRIVACY("privacy", "プライバシーモード"),
    UP("up", "一番上へ"),
    DOWN("down", "一番下へ"),
    CREATE("edit", "スレ立て"),
    SEARCH("search", "検索"),
    REFRESH("refresh", "更新"),
    MENU("menu", "メニュー");
}

internal enum class FtutterThreadTool(val stored: String, val label: String) {
    DRAWER("drawer", "ドロワーを開く"),
    TREE("tree", "ツリー表示切り替え"),
    POPULAR("ninki", "人気レス抽出"),
    IMAGE_POSTS("gazou", "画像レス抽出（長押しで画像一覧）"),
    TAB("tab", "タブ表示切り替え"),
    SPACE("space", "スペース（調整用）"),
    PRIVACY("privacy", "プライバシーモード"),
    UP("up", "一番上へ"),
    DOWN("down", "一番下へ"),
    GALLERY("collection", "画像一覧"),
    REPLY("edit", "書き込み"),
    SEARCH("search", "レス検索"),
    REFRESH("refresh", "更新"),
    MENU("menu", "メニュー");
}

/** 並べた項目とON/OFF。 */
internal data class FtutterToolbarEntry<T>(val tool: T, val enabled: Boolean)

/** 原作の既定（設定の調査 §2）：カタログ11項目、スレッド14項目。 */
internal val FTUTTER_CATALOG_TOOLBAR_DEFAULT: List<FtutterToolbarEntry<FtutterCatalogTool>> = listOf(
    FtutterCatalogTool.DRAWER to true, FtutterCatalogTool.ORDER to true, FtutterCatalogTool.TAB to true,
    FtutterCatalogTool.SPACE to true, FtutterCatalogTool.PRIVACY to false, FtutterCatalogTool.UP to false,
    FtutterCatalogTool.DOWN to false, FtutterCatalogTool.CREATE to true, FtutterCatalogTool.SEARCH to true,
    FtutterCatalogTool.REFRESH to true, FtutterCatalogTool.MENU to true
).map { (tool, enabled) -> FtutterToolbarEntry(tool, enabled) }

internal val FTUTTER_THREAD_TOOLBAR_DEFAULT: List<FtutterToolbarEntry<FtutterThreadTool>> = listOf(
    FtutterThreadTool.DRAWER to false, FtutterThreadTool.TREE to true, FtutterThreadTool.POPULAR to true,
    FtutterThreadTool.IMAGE_POSTS to false, FtutterThreadTool.TAB to true, FtutterThreadTool.SPACE to true,
    FtutterThreadTool.PRIVACY to false, FtutterThreadTool.UP to false, FtutterThreadTool.DOWN to false,
    FtutterThreadTool.GALLERY to true, FtutterThreadTool.REPLY to true, FtutterThreadTool.SEARCH to true,
    FtutterThreadTool.REFRESH to true, FtutterThreadTool.MENU to true
).map { (tool, enabled) -> FtutterToolbarEntry(tool, enabled) }

/** 文字列のリストの保存形式（改行区切り）。 */
internal fun encodeFtutterList(values: List<String>): String = values.joinToString("\n")
internal fun decodeFtutterList(raw: String?): List<String>? =
    raw?.takeIf { it.isNotEmpty() }?.split("\n")

/**
 * 保存された並びを読む。原作どおり、名前とON/OFFの数がそろっている時だけ採用する。さらに、未知の名前・重複は落とし、
 * 保存に無い項目（版が上がって増えた項目）は既定の位置関係のまま末尾に足す。どちらも原作には無い修復。
 */
internal fun <T> parseFtutterToolbar(
    names: List<String>?,
    enabled: List<String>?,
    defaults: List<FtutterToolbarEntry<T>>,
    storedName: (T) -> String
): List<FtutterToolbarEntry<T>> {
    if (names == null || enabled == null || names.size != enabled.size || names.isEmpty()) return defaults
    val byName = defaults.associateBy { storedName(it.tool) }
    val seen = mutableSetOf<String>()
    val parsed = names.zip(enabled).mapNotNull { (name, on) ->
        val entry = byName[name] ?: return@mapNotNull null
        if (!seen.add(name)) return@mapNotNull null
        FtutterToolbarEntry(entry.tool, on.trim() == "true")
    }
    if (parsed.isEmpty()) return defaults
    val missing = defaults.filter { storedName(it.tool) !in seen }
    return parsed + missing
}

internal fun <T> encodeFtutterToolbar(
    entries: List<FtutterToolbarEntry<T>>,
    storedName: (T) -> String
): Pair<String, String> =
    encodeFtutterList(entries.map { storedName(it.tool) }) to encodeFtutterList(entries.map { it.enabled.toString() })

/** ボタンの寸法（設定の調査 §2）。 */
internal data class FtutterToolbarMetrics(
    val iconWidth: Int = 45,
    val iconHeight: Int = 45,
    val leftSpace: Int = 5,
    val rightSpace: Int = 5
)

internal fun parseFtutterCatalogToolbarMetrics(p: Map<String, String>) = FtutterToolbarMetrics(
    iconWidth = p.ftutterInt(FtutterKeys.CATALOG_ICON_WIDTH, 45, 30..80).coerceAtLeast(TOOLBAR_MIN_ITEM_WIDTH_DP),
    iconHeight = p.ftutterInt(FtutterKeys.CATALOG_ICON_HEIGHT, 45, 30..80),
    leftSpace = p.ftutterInt(FtutterKeys.CATALOG_TOOLBAR_LEFT, 5, 0..100),
    rightSpace = p.ftutterInt(FtutterKeys.CATALOG_TOOLBAR_RIGHT, 5, 0..100)
)

internal fun parseFtutterThreadToolbarMetrics(p: Map<String, String>) = FtutterToolbarMetrics(
    iconWidth = p.ftutterInt(FtutterKeys.THREAD_ICON_WIDTH, 45, 30..80).coerceAtLeast(TOOLBAR_MIN_ITEM_WIDTH_DP),
    iconHeight = p.ftutterInt(FtutterKeys.THREAD_ICON_HEIGHT, 45, 30..80),
    leftSpace = p.ftutterInt(FtutterKeys.THREAD_TOOLBAR_LEFT, 5, 0..100),
    rightSpace = p.ftutterInt(FtutterKeys.THREAD_TOOLBAR_RIGHT, 5, 0..100)
)

internal fun parseFtutterCatalogToolbar(p: Map<String, String>) = parseFtutterToolbar(
    decodeFtutterList(p[FtutterKeys.CATALOG_TOOLBAR_ORDER]),
    decodeFtutterList(p[FtutterKeys.CATALOG_TOOLBAR_ENABLED]),
    FTUTTER_CATALOG_TOOLBAR_DEFAULT
) { it.stored }

internal fun parseFtutterThreadToolbar(p: Map<String, String>) = parseFtutterToolbar(
    decodeFtutterList(p[FtutterKeys.THREAD_TOOLBAR_ORDER]),
    decodeFtutterList(p[FtutterKeys.THREAD_TOOLBAR_ENABLED]),
    FTUTTER_THREAD_TOOLBAR_DEFAULT
) { it.stored }

/** 並び順の欄（カタログだけ）の幅。原作は DropdownButton2 に64を取る。 */
internal const val FTUTTER_TOOLBAR_ORDER_WIDTH = 64
/** スペース項目がONの時に最低限残す幅。 */
internal const val FTUTTER_TOOLBAR_SPACE_MIN = 20

/** The original's width calculation, with a usable minimum for crowded and split-screen toolbars. */
internal fun ftutterToolbarButtonWidth(
    totalWidth: Float,
    metrics: FtutterToolbarMetrics,
    buttonCount: Int,
    hasSpace: Boolean,
    hasOrder: Boolean
): Float {
    val preferred = metrics.iconWidth.coerceAtLeast(TOOLBAR_MIN_ITEM_WIDTH_DP).toFloat()
    if (buttonCount <= 0) return preferred
    val usable = totalWidth - metrics.leftSpace - metrics.rightSpace -
        (if (hasSpace) FTUTTER_TOOLBAR_SPACE_MIN else 0) - (if (hasOrder) FTUTTER_TOOLBAR_ORDER_WIDTH else 0)
    return minOf(preferred, usable / buttonCount).coerceAtLeast(TOOLBAR_MIN_ITEM_WIDTH_DP.toFloat())
}

/** Keep at least one button visible initially, even with 100dp margins in a tiny pane. */
internal fun ftutterToolbarMetricsForWidth(width: Float, metrics: FtutterToolbarMetrics): FtutterToolbarMetrics {
    val margins = metrics.leftSpace + metrics.rightSpace
    val budget = (width - TOOLBAR_MIN_ITEM_WIDTH_DP).coerceAtLeast(0f)
    if (margins <= budget || margins == 0) return metrics
    val scale = budget / margins
    return metrics.copy(leftSpace = (metrics.leftSpace * scale).toInt(), rightSpace = (metrics.rightSpace * scale).toInt())
}

/** 下部の平らなツールバー。[slot] がスペースか並び順か、それ以外のボタンかで描き分ける。 */
@Composable
internal fun <T> FtutterBottomToolbar(
    entries: List<FtutterToolbarEntry<T>>,
    metrics: FtutterToolbarMetrics,
    isSpace: (T) -> Boolean,
    isOrder: (T) -> Boolean,
    modifier: Modifier = Modifier,
    slot: @Composable (tool: T, width: Dp) -> Unit
) {
    val colors = LocalFtutterColors.current
    val shown = entries.filter { it.enabled }.map { it.tool }
    if (shown.none { !isSpace(it) }) {
        // Only the system inset remains; no empty 45dp toolbar body.
        Box(modifier.fillMaxWidth().background(colors.toolBar).navigationBarsPadding())
        return
    }
    BoxWithConstraints(
        modifier.fillMaxWidth().background(colors.toolBar).navigationBarsPadding()
    ) {
        val fittedMetrics = ftutterToolbarMetricsForWidth(maxWidth.value, metrics)
        val hasSpace = shown.any(isSpace)
        val hasOrder = shown.any(isOrder)
        val buttonCount = shown.count { !isSpace(it) && !isOrder(it) }
        val width = ftutterToolbarButtonWidth(
            totalWidth = maxWidth.value,
            metrics = fittedMetrics,
            buttonCount = buttonCount,
            hasSpace = hasSpace,
            hasOrder = hasOrder
        ).dp
        val contentWidth = maxOf(maxWidth.value, fittedMetrics.leftSpace + fittedMetrics.rightSpace +
            buttonCount * width.value + (if (hasSpace) FTUTTER_TOOLBAR_SPACE_MIN else 0) +
            (if (hasOrder) FTUTTER_TOOLBAR_ORDER_WIDTH else 0)).dp
        Row(
            Modifier.horizontalScroll(rememberScrollState()).width(contentWidth).height(metrics.iconHeight.dp),
            horizontalArrangement = if (hasSpace) Arrangement.Start else Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(fittedMetrics.leftSpace.dp))
            shown.forEach { tool ->
                when {
                    isSpace(tool) -> Spacer(Modifier.weight(1f))
                    isOrder(tool) -> Box(Modifier.width(FTUTTER_TOOLBAR_ORDER_WIDTH.dp)) { slot(tool, FTUTTER_TOOLBAR_ORDER_WIDTH.dp) }
                    else -> Box(Modifier.width(width), contentAlignment = Alignment.Center) { slot(tool, width) }
                }
            }
            Spacer(Modifier.width(fittedMetrics.rightSpace.dp))
        }
    }
}
