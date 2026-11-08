package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valoser.futacha.shared.ui.util.PlatformBackHandler

/**
 * One item of a sheet ([FutaberActionSheet]). A disabled one is shown in grey and cannot be pressed.
 *
 * [destructive] draws it in the destructive red (futaber: 「履歴から削除」, 「タブをすべて削除」). [extra] marks an item that
 * comes after the original's last item (this app's own entries and the extensions): the sheet stops at the original's
 * items and the extras scroll inside it (docs/futaber-evolution-look-2026-10-31.md §2.1).
 */
internal data class FutaberPostAction(
    val id: String,
    val label: String,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
    val extra: Boolean = false,
    val onClick: () -> Unit
)

/** The post's text as plain lines, for the clipboard. */
internal fun futaberPostPlainText(post: com.valoser.futacha.shared.model.Post): String =
    com.valoser.futacha.shared.ui.board.messageHtmlToPlainText(post.messageHtml)

// ---- The measurements of the original's action sheet (iPhone SE 3, P/out-press/60-01-sheet.txt) ----

/** An item's row (57pt; the hairline between rows is drawn inside the row). */
internal const val FUTABER_SHEET_ROW_DP = 57

/** The heading's band (44pt, the text grey and small). */
internal const val FUTABER_SHEET_TITLE_DP = 44

/** Left and right of the groups (8pt). */
internal const val FUTABER_SHEET_SIDE_DP = 8

/** Between the items' group and キャンセル's group (8pt). */
internal const val FUTABER_SHEET_GAP_DP = 8

/** Under キャンセル (over the system's bottom area: the home indicator, the navigation bar or the keyboard), and the least room left above the sheet under the status area (8pt). */
internal const val FUTABER_SHEET_EDGE_DP = 8

/** The share of the items' group a long heading may take before it scrolls in its own band (a catalog thread's whole title). */
internal const val FUTABER_SHEET_TITLE_MAX_FRACTION = 0.4f

internal const val FUTABER_SHEET_CANCEL_LABEL = "キャンセル"

/** The row that opens the extras as a second step (`futaber-action-more`), and that step's heading. */
internal const val FUTABER_MORE_ACTION_ID = "more"
internal const val FUTABER_MORE_LABEL = "その他の機能"

/** The heights of a sheet's parts, in px: the heading, the rows' viewport, the gap and キャンセル. */
internal data class FutaberSheetHeights(val title: Int, val rows: Int, val gap: Int, val cancel: Int) {
    /** The items' group: the heading over the rows. */
    val group: Int get() = title + rows

    /** The whole sheet from the top of the group to the bottom of キャンセル. */
    val total: Int get() = group + gap + cancel
}

/**
 * The height of the rows the sheet shows without scrolling: every row up to the last one that is not
 * [FutaberPostAction.extra] (the original's items, with what this app keeps among them), at their natural heights.
 * The extras after them scroll inside the group. With no extra (or no item of the original) every row counts.
 */
internal fun futaberSheetOwnRowsHeight(rowHeights: List<Int>, extra: List<Boolean>): Int {
    val lastOwn = extra.indexOfLast { !it }
    val shown = if (lastOwn < 0 || extra.none { it }) rowHeights.size else lastOwn + 1
    return rowHeights.take(shown).sum()
}

/**
 * Where the parts of a sheet go in [available] px (the window less the status area, the system's bottom area or the
 * keyboard, and the margins; M47 / §2.11): キャンセル first, at its natural height ([cancel], 57 or more with large
 * text), so it is always on screen; the heading up to [FUTABER_SHEET_TITLE_MAX_FRACTION] of the rest; the rows of
 * the original ([ownRows], [futaberSheetOwnRowsHeight]) in what is left. A group that cannot hold them scrolls.
 */
internal fun futaberSheetHeights(available: Int, cancel: Int, gap: Int, title: Int, ownRows: Int): FutaberSheetHeights {
    val room = available.coerceAtLeast(0)
    val cancelHeight = cancel.coerceIn(0, room)
    val groupMax = (room - cancelHeight - gap).coerceAtLeast(0)
    val titleHeight = title.coerceIn(0, (groupMax * FUTABER_SHEET_TITLE_MAX_FRACTION).toInt())
    val rows = ownRows.coerceIn(0, groupMax - titleHeight)
    val shownGap = if (titleHeight + rows > 0) gap.coerceAtMost(room - cancelHeight).coerceAtLeast(0) else 0
    return FutaberSheetHeights(titleHeight, rows, shownGap, cancelHeight)
}

/**
 * The action sheet of the original (V15, M47): over the whole screen (the bottom bar and the tab strip under the
 * veil too; the caller draws it in a layer that covers the screen), 8 from the sides, a group of the heading and the
 * items with corners of 13, and キャンセル in a group of its own 8 under it, 8 over the system's bottom area. The
 * keyboard counts as that area, so a sheet opened while typing is above the keyboard (the writing screen's tools).
 *
 * The group holds the original's items at their natural height and stops there; the extras ([FutaberPostAction.extra])
 * scroll inside it, and its scroll bar flashes once when it opens to show there is more (§2.1). On a screen too short
 * for even the original's items the group scrolls and キャンセル stays on screen (large text included).
 *
 * An item runs when the finger is lifted on the item it went down on; sliding onto another item and lifting selects
 * nothing (G33: iOS's slide-to-select is not copied, it sent "del" by mistake while the original was surveyed).
 * Tapping outside, キャンセル, the system's Back all close it. The item tags (`futaber-action-<id>`), the sheet's
 * (`futaber-post-actions`) and the veil's (`futaber-action-scrim`) are the ones of the sheet this replaces.
 */
@Composable
internal fun FutaberActionSheet(
    title: String?,
    actions: List<FutaberPostAction>,
    onDismiss: () -> Unit,
    /** The tag of キャンセル is `futaber-action-<cancelId>` (the sheets kept their own: "cancel", "tab-cancel", "tool-cancel"). */
    cancelId: String = "cancel",
    /** Drawn before the heading's text (a thread's thumbnail, B5). */
    titleLeading: (@Composable () -> Unit)? = null
) {
    val colors = LocalFutaberColors.current
    // F-B2 (features §4.1 / plan 4.1 の1 案A): the extras ([FutaberPostAction.extra]) are one row, 「その他の機能」, after the
    // original's items; it opens them as the second step (a sheet of its own), so the first sheet is the original's.
    var showMore by remember { mutableStateOf(false) }
    val hasExtras = LocalFutaberDisplaySettings.current.foldExtras && actions.any { it.extra }
    val shown = when {
        !hasExtras -> actions
        showMore -> actions.filter { it.extra }.map { it.copy(extra = false) }
        else -> actions.filterNot { it.extra } + FutaberPostAction(FUTABER_MORE_ACTION_ID, FUTABER_MORE_LABEL, extra = true) { showMore = true }
    }
    val shownTitle = if (showMore) FUTABER_MORE_LABEL else title
    // Registered when the sheet appears, so it is the newest handler: Back closes the sheet before the screen under it
    // (from the second step it goes back to the first). With the keyboard up, Android's first Back puts the keyboard
    // away, as everywhere (FutaberBackHandler).
    FutaberBackHandler(onBack = { if (showMore) showMore = false else onDismiss() })
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(colors.sheetScrim)
                .clickable(interactionSource = null, indication = null, onClickLabel = "閉じる", onClick = onDismiss)
                .testTag("futaber-action-scrim")
        )
        Box(
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime).only(WindowInsetsSides.Bottom))
                .padding(horizontal = FUTABER_SHEET_SIDE_DP.dp, vertical = FUTABER_SHEET_EDGE_DP.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            FutaberActionSheetBody(shownTitle, if (showMore) null else titleLeading, shown, cancelId, onDismiss)
        }
    }
}

/** The group and キャンセル, laid out by [futaberSheetHeights]. */
@Composable
private fun FutaberActionSheetBody(
    title: String?,
    titleLeading: (@Composable () -> Unit)?,
    actions: List<FutaberPostAction>,
    cancelId: String,
    onDismiss: () -> Unit
) {
    val colors = LocalFutaberColors.current
    val rowsScroll = rememberScrollState()
    val titleScroll = rememberScrollState()
    val corner = FUTABER_SHEET_CORNER_DP.dp
    val hasTitle = title != null
    val titleShape = RoundedCornerShape(topStart = corner, topEnd = corner)
    val rowsShape = if (hasTitle) RoundedCornerShape(bottomStart = corner, bottomEnd = corner) else FutaberShapes.sheet
    val indicator = rememberFutaberScrollIndicator(rowsScroll, flashWhenScrollable = true)
    Layout(
        modifier = Modifier.fillMaxWidth().testTag("futaber-post-actions"),
        content = {
            Box(Modifier.clip(FutaberShapes.sheet).background(colors.sheetGroup))
            Box(Modifier.clip(titleShape).verticalScroll(titleScroll)) {
                if (title != null) FutaberSheetTitle(title, titleLeading)
            }
            Box(Modifier.clip(rowsShape).then(indicator).verticalScroll(rowsScroll).testTag("futaber-sheet-items")) {
                FutaberSheetRows(actions, separatorOnTop = hasTitle)
            }
            FutaberSheetCancel(cancelId, onDismiss)
        }
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val cancel = measurables[3].measure(Constraints(minWidth = width, maxWidth = width, maxHeight = constraints.maxHeight))
        val heights = futaberSheetHeights(
            available = constraints.maxHeight,
            cancel = cancel.height,
            gap = FUTABER_SHEET_GAP_DP.dp.roundToPx(),
            title = if (hasTitle) measurables[1].maxIntrinsicHeight(width) else 0,
            // The rows' own measure policy answers "the original's items" for the least height (FutaberSheetRowsPolicy).
            ownRows = measurables[2].minIntrinsicHeight(width)
        )
        val ground = measurables[0].measure(Constraints.fixed(width, heights.group))
        val heading = measurables[1].measure(Constraints.fixed(width, heights.title))
        val rows = measurables[2].measure(Constraints.fixed(width, heights.rows))
        layout(width, heights.total) {
            ground.place(0, 0)
            heading.place(0, 0)
            rows.place(0, heights.title)
            cancel.place(0, heights.group + heights.gap)
        }
    }
}

/** The corners of the groups (iOS: about 13pt), as [FutaberShapes.sheet]. */
private const val FUTABER_SHEET_CORNER_DP = 13

/**
 * The heading: grey, small, centred, as many lines as it needs (a whole thread title). With a thumbnail before it (the
 * tab sheet, `F:catalog-015`) the original puts the picture 20 from the group's left edge and the title left-aligned 12
 * after it.
 */
@Composable
private fun FutaberSheetTitle(title: String, leading: (@Composable () -> Unit)?) {
    val colors = LocalFutaberColors.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = FUTABER_SHEET_TITLE_DP.dp)
            .padding(start = if (leading != null) 20.dp else 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
            .testTag("futaber-sheet-title"),
        horizontalArrangement = if (leading != null) Arrangement.spacedBy(12.dp) else Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading?.invoke()
        Text(
            title,
            color = colors.sheetTitle,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = if (leading != null) TextAlign.Start else TextAlign.Center,
            modifier = if (leading != null) Modifier.weight(1f) else Modifier
        )
    }
}

/** The items, one row each, in their order. */
@Composable
private fun FutaberSheetRows(actions: List<FutaberPostAction>, separatorOnTop: Boolean) {
    val extras = actions.map { it.extra }
    val policy = remember(extras) { FutaberSheetRowsPolicy(extras) }
    Layout(
        content = {
            actions.forEachIndexed { index, action -> FutaberSheetRow(action, separatorAbove = index > 0 || separatorOnTop) }
        },
        measurePolicy = policy
    )
}

/**
 * The rows stacked at their natural heights. The least intrinsic height is the part the sheet shows without
 * scrolling (the original's items, [futaberSheetOwnRowsHeight]); the greatest is all of them.
 */
private class FutaberSheetRowsPolicy(private val extras: List<Boolean>) : MeasurePolicy {
    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val width = constraints.maxWidth
        val childConstraints = Constraints(minWidth = width, maxWidth = width)
        val placeables = measurables.map { it.measure(childConstraints) }
        val height = placeables.sumOf { it.height }
        return layout(width, height) {
            var y = 0
            placeables.forEach { placeable -> placeable.place(0, y); y += placeable.height }
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        futaberSheetOwnRowsHeight(measurables.map { it.maxIntrinsicHeight(width) }, extras)

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        measurables.sumOf { it.maxIntrinsicHeight(width) }
}

/** One item: 57 high, the label centred in the system blue (red when destructive, grey when it cannot run). */
@Composable
private fun FutaberSheetRow(action: FutaberPostAction, separatorAbove: Boolean) {
    val colors = LocalFutaberColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        Modifier.fillMaxWidth().heightIn(min = FUTABER_SHEET_ROW_DP.dp)
            .background(if (pressed && action.enabled) colors.sheetPressed else Color.Transparent)
            .drawBehind {
                if (separatorAbove) {
                    val stroke = 0.5.dp.toPx()
                    drawLine(colors.sheetSeparator, Offset(0f, stroke / 2f), Offset(size.width, stroke / 2f), strokeWidth = stroke)
                }
            }
            // Runs on lifting the finger on this row only (a slide onto another row and lifting runs nothing, G33).
            .clickable(
                interactionSource = interaction, indication = null, enabled = action.enabled,
                onClickLabel = action.label, role = Role.Button, onClick = action.onClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("futaber-action-${action.id}"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            action.label,
            color = when {
                !action.enabled -> colors.sheetTitle
                action.destructive -> colors.destructiveOnSheet
                else -> colors.iosBlueOnSheet
            },
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** キャンセル in its own white group, bold. */
@Composable
private fun FutaberSheetCancel(cancelId: String, onDismiss: () -> Unit) {
    val colors = LocalFutaberColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        Modifier.fillMaxWidth().heightIn(min = FUTABER_SHEET_ROW_DP.dp)
            .clip(FutaberShapes.sheet)
            .background(if (pressed) colors.sheetPressed else colors.sheetCancel)
            .clickable(
                interactionSource = interaction, indication = null,
                onClickLabel = FUTABER_SHEET_CANCEL_LABEL, role = Role.Button, onClick = onDismiss
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("futaber-action-$cancelId"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            FUTABER_SHEET_CANCEL_LABEL,
            color = colors.iosBlueOnSheet,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

/**
 * A layer of its own over the screen it is drawn in (the sheets of the thread over its tab strip and bottom bar): state
 * read in [content] composes only [content] again, and the lambda is compiled as its own method (the thread screen's
 * composable stays under the JVM's method size).
 */
@Composable
internal fun FutaberScreenLayer(content: @Composable () -> Unit) {
    content()
}

/**
 * The system's Back for a sheet, an alert, a popover or a menu over the screen: it runs [onBack]; but on Android, while
 * the keyboard is up, the first Back only puts the keyboard away (Android's convention, as the writing screen does: a
 * handler registered after the keyboard's would otherwise take that Back). iOS has no Back key; its edge swipe runs
 * [onBack] unless [iosEdgeGestureEnabled] is false (the operation menu, G34: futaber's does not close by an edge swipe).
 */
@Composable
internal fun FutaberBackHandler(onBack: () -> Unit, iosEdgeGestureEnabled: Boolean = true) {
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val ime = WindowInsets.ime
    val density = androidx.compose.ui.platform.LocalDensity.current
    PlatformBackHandler(iosEdgeGestureEnabled = iosEdgeGestureEnabled) {
        if (com.valoser.futacha.shared.util.isAndroid() && ime.getBottom(density) > 0) keyboard?.hide() else onBack()
    }
}
