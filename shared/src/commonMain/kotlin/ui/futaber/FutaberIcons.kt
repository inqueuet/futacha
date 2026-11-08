package com.valoser.futacha.shared.ui.futaber

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The icons of the mode (V04): thin outlines, as the original draws them (about 1pt lines). They are drawn here on a
 * 24-unit grid with [FUTABER_ICON_STROKE]-unit lines, after the shapes the original uses (a box with an arrow leaving
 * it for the operation menu, three bars for the sort, a cube for the saved box, the words "+NG", "NG OFF" and "↓New" in
 * the operation menu), not copied from its artwork. A button that cannot be pressed draws the same shape in a lighter
 * colour ([FutaberColors.iconDisabled]). The screens keep naming the outlined Material glyph they mean; [FutaberIcon]
 * draws the line glyph of that name instead.
 */
internal const val FUTABER_ICON_STROKE = 1.2f

private fun lineIcon(
    name: String,
    strokes: List<String>,
    fills: List<String> = emptyList(),
    autoMirror: Boolean = false,
    /** The fills cut holes where their shapes overlap (the clock's hands in its face, the cube's edges). */
    evenOdd: Boolean = false
): ImageVector = ImageVector.Builder(
    name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f, autoMirror = autoMirror
).apply {
    strokes.forEach { data ->
        addPath(
            pathData = PathParser().parsePathString(data).toNodes(), fill = null, stroke = SolidColor(Color.Black),
            strokeLineWidth = FUTABER_ICON_STROKE, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        )
    }
    // Dots and the filled star: shapes, not lines.
    fills.forEach { data ->
        addPath(
            pathData = PathParser().parsePathString(data).toNodes(), fill = SolidColor(Color.Black),
            pathFillType = if (evenOdd) androidx.compose.ui.graphics.PathFillType.EvenOdd else androidx.compose.ui.graphics.PathFillType.NonZero
        )
    }
}.build()

/** The panel button of the original: an outlined rectangle split in two by a vertical line. */
internal val FutaberTwoPaneIcon: ImageVector by lazy {
    lineIcon("FutaberTwoPane", listOf("M3.5 4.5h17v15h-17z", "M12 4.5v15"))
}

/** The thread's operation menu button of the original: a box with an arrow curving out of it to the upper right. */
internal val FutaberOperationMenuIcon: ImageVector by lazy {
    lineIcon("FutaberOperationMenu", listOf("M10 6.5H4.5v13.5h13.5v-5", "M8.5 15c0.8-4.6 4.4-7.5 11-7.5", "M16.5 4.5l3 3-3 3"))
}

/** "Close this bubble" of the original app: a speech bubble holding a cross. */
internal val FutaberCloseBubbleIcon: ImageVector by lazy {
    lineIcon("FutaberCloseBubble", listOf("M3 4h18v12H11l-4 4v-4H3z", "M9 7.5l5 5M14 7.5l-5 5"))
}

/** S11: the viewer's way back to the post: a speech bubble holding an ellipsis. */
internal val FutaberBubbleDotsIcon: ImageVector by lazy {
    lineIcon(
        "FutaberBubbleDots", listOf("M3 4h18v12H11l-4 4v-4H3z"),
        fills = listOf("M6.5 10a1 1 0 1 0 2 0a1 1 0 1 0 -2 0", "M11 10a1 1 0 1 0 2 0a1 1 0 1 0 -2 0", "M15.5 10a1 1 0 1 0 2 0a1 1 0 1 0 -2 0")
    )
}

/** "Close every bubble": the same bubble with stacked bars beside the cross. */
internal val FutaberCloseAllBubblesIcon: ImageVector by lazy {
    lineIcon("FutaberCloseAllBubbles", listOf("M3 4h18v12H11l-4 4v-4H3z", "M8 7.5l5 5M13 7.5l-5 5", "M16.2 7v6M18.2 7v6"))
}

/**
 * V22 (L-B10): the manage panel's chosen category, filled as futaber fills it (the cube, the bell, the star, the clock and
 * the two squares), tinted with the category's colour by the panel. The others stay the thin lines.
 */
internal val FutaberPanelCubeFilledIcon: ImageVector by lazy {
    lineIcon(
        "FutaberPanelCubeFilled", emptyList(),
        fills = listOf(
            "M12 3.5l8 4.5v8.5l-8 4.5-8-4.5V8z" +
                "M4.3 8.6l7.7 4.33 7.7-4.33-0.5-0.85-7.2 4.05-7.2-4.05z" +
                "M11.5 13.2h1v7.2h-1z"
        ),
        evenOdd = true
    )
}

internal val FutaberPanelBellFilledIcon: ImageVector by lazy {
    lineIcon(
        "FutaberPanelBellFilled", listOf("M12 5V3.5"),
        fills = listOf("M6 16.5V11a6 6 0 0 1 12 0v5.5l1.5 2h-15z", "M10 19.8h4a2 2 0 0 1-4 0z")
    )
}

internal val FutaberPanelStarFilledIcon: ImageVector by lazy {
    lineIcon(
        "FutaberPanelStarFilled", emptyList(),
        fills = listOf("M12.00 3.40 L14.29 9.44 L20.75 9.76 L15.71 13.81 L17.41 20.04 L12.00 16.50 L6.59 20.04 L8.29 13.81 L3.25 9.76 L9.71 9.44Z")
    )
}

internal val FutaberPanelClockFilledIcon: ImageVector by lazy {
    lineIcon(
        "FutaberPanelClockFilled", emptyList(),
        fills = listOf("M3.5 12a8.5 8.5 0 1 0 17 0a8.5 8.5 0 1 0 -17 0z" + "M11.3 6.6h1.4v6.1h-1.4z" + "M12.7 11.3h3.7v1.4h-3.7z"),
        evenOdd = true
    )
}

internal val FutaberPanelTabsFilledIcon: ImageVector by lazy {
    lineIcon("FutaberPanelTabsFilled", listOf("M8 8V4h12v12h-4"), fills = listOf("M4 8h12v12H4z"))
}

/** The mode's own icons (drawn as lines from the start), which [futaberIconFor] keeps as they are. */
internal val FUTABER_OWN_ICONS: List<ImageVector> by lazy {
    listOf(FutaberTwoPaneIcon, FutaberOperationMenuIcon, FutaberCloseBubbleIcon, FutaberCloseAllBubblesIcon, FutaberBubbleDotsIcon)
}

/**
 * The line glyph for each outlined Material glyph the screens of the mode name, by the Material glyph's name
 * ("Outlined.Close", "AutoMirrored.Outlined.ArrowBack"). `FutaberLineIconCoverageTest` checks that every glyph a
 * screen of the mode names is here.
 */
private val FUTABER_LINE_ICONS: Map<String, ImageVector> by lazy {
    fun line(name: String, strokes: List<String>, fills: List<String> = emptyList(), autoMirror: Boolean = false) =
        name to lineIcon("FutaberLine.$name", strokes, fills, autoMirror)
    mapOf(
        line("AutoMirrored.Outlined.ArrowBack", listOf("M20 12H4.5M11 5.5L4.5 12l6.5 6.5"), autoMirror = true),
        line("AutoMirrored.Outlined.FormatListBulleted", listOf("M8.5 6h12M8.5 12h12M8.5 18h12"), fills = listOf("M3.40 6a1.1 1.1 0 1 0 2.2 0a1.1 1.1 0 1 0 -2.2 0", "M3.40 12a1.1 1.1 0 1 0 2.2 0a1.1 1.1 0 1 0 -2.2 0", "M3.40 18a1.1 1.1 0 1 0 2.2 0a1.1 1.1 0 1 0 -2.2 0"), autoMirror = true),
        line("AutoMirrored.Outlined.ArrowForward", listOf("M4 12h15.5M13 5.5L19.5 12 13 18.5"), autoMirror = true),
        line("AutoMirrored.Outlined.KeyboardArrowRight", listOf("M9 5.5l6.5 6.5-6.5 6.5"), autoMirror = true),
        line("AutoMirrored.Outlined.KeyboardArrowLeft", listOf("M15 4.5l-7.5 7.5 7.5 7.5"), autoMirror = true),
        line("AutoMirrored.Outlined.Undo", listOf("M9 5L4.5 9.5 9 14", "M4.5 9.5h10a5 5 0 0 1 0 10h-3"), autoMirror = true),
        line("AutoMirrored.Outlined.VolumeOff", listOf("M4 9.5h3.5L12 5.5v13l-4.5-4H4z", "M15.5 9.5l5 5M20.5 9.5l-5 5"), autoMirror = true),
        line("AutoMirrored.Outlined.VolumeUp", listOf("M4 9.5h3.5L12 5.5v13l-4.5-4H4z", "M15 9a4 4 0 0 1 0 6M17.5 6.5a7.5 7.5 0 0 1 0 11"), autoMirror = true),
        line("Outlined.Add", listOf("M12 4.5v15M4.5 12h15")),
        line("Outlined.Archive", listOf("M3.5 4.5h17v4h-17z", "M5 8.5v11h14v-11", "M10 12h4")),
        line("Outlined.BarChart", listOf("M5 9.5h3.5v10.5H5zM10.25 13h3.5v7h-3.5zM15.5 4H19v16h-3.5z")),
        line("Outlined.Block", listOf("M2.3 12h4.4M4.5 9.8v4.4", "M8.2 15.5v-7l4 7v-7", "M19.7 9.9A3.5 3.5 0 1 0 20.4 12H17.4")),
        line("Outlined.ChatBubbleOutline", listOf("M4 5h16v10.5h-9.5L6.5 19v-3.5H4z")),
        line("Outlined.Check", listOf("M4.5 12.5l5 5L19.5 7")),
        line("Outlined.Close", listOf("M6 6l12 12M18 6L6 18")),
        line("Outlined.ContentCopy", listOf("M8.5 5H6v15.5h12V5h-2.5", "M8.5 3.5h7v3h-7z", "M9 11h6M9 14h6M9 17h4")),
        line("Outlined.ContentPaste", listOf("M8.5 5H6v15.5h12V5h-2.5", "M8.5 3.5h7v3h-7z", "M9 11.5h6v6H9z")),
        line("Outlined.Description", listOf("M6 3.5h8l4.5 4.5v12.5H6z", "M14 3.5V8h4.5", "M8.5 12h7M8.5 15h7M8.5 18h5")),
        line("Outlined.Edit", listOf("M4 20l1-4.5L15.5 5l3.5 3.5L8.5 19z", "M13.5 7l3.5 3.5")),
        // L-B8 (M39): the pasteboard's folder row and the edit mode's grip (three bars, as iOS's).
        line("Outlined.DragHandle", listOf("M4.5 8.5h15M4.5 12h15M4.5 15.5h15")),
        line("Outlined.FilterList", listOf("M4 7h16M7 12h10M10 17h4")),
        line("Outlined.FilterNone", listOf("M8 8V4h12v12h-4", "M4 8h12v12H4z")),
        line("Outlined.Folder", listOf("M3.5 6h6l2 2.5h9v10.5h-17z")),
        line("Outlined.FormatQuote", listOf("M4.80 9.5a2.2 2.2 0 1 0 4.4 0a2.2 2.2 0 1 0 -4.4 0", "M9.2 9.8c0 3.8-1.8 6.2-4.7 7.2", "M12.80 9.5a2.2 2.2 0 1 0 4.4 0a2.2 2.2 0 1 0 -4.4 0", "M17.2 9.8c0 3.8-1.8 6.2-4.7 7.2")),
        line("Outlined.Forum", listOf("M14.5 9.5V4h-11v9.5h2v2.5l2.5-2.5h2", "M9.5 9.5h11v7.5H19v3l-3-3H9.5z")),
        line("Outlined.GridView", listOf("M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z")),
        line("Outlined.History", listOf("M3.50 12a8.5 8.5 0 1 0 17.0 0a8.5 8.5 0 1 0 -17.0 0", "M12 7v5h4")),
        line("Outlined.Image", listOf("M3.5 5h17v14h-17z", "M3.5 16.5l5-5 4.5 4.5 3-3 4.5 4.5", "M14.10 9a1.4 1.4 0 1 0 2.8 0a1.4 1.4 0 1 0 -2.8 0")),
        line("Outlined.IosShare", listOf("M12 3.5v11M8 7.5l4-4 4 4M9 10H6v10.5h12V10h-3")),
        line("Outlined.KeyboardArrowDown", listOf("M6 9l6 6 6-6")),
        line("Outlined.KeyboardArrowUp", listOf("M6 15l6-6 6 6")),
        line("Outlined.KeyboardDoubleArrowDown", listOf("M6 5.5l6 6 6-6M6 12.5l6 6 6-6")),
        line("Outlined.ManageSearch", listOf("M3 6.5h6M3 11.5h4M3 16.5h6", "M10.50 11.5a4.5 4.5 0 1 0 9.0 0a4.5 4.5 0 1 0 -9.0 0", "M18.3 14.8l3.2 3.2")),
        line("Outlined.NewReleases", listOf("M12 3.5v6M9.5 7l2.5 2.5L14.5 7", "M3.5 19v-6l3.5 6v-6", "M8.6 17h4a2 2 0 1 0-0.6 1.45", "M14 15l1.6 4 1.65-4 1.65 4 1.6-4")),
        line("Outlined.Notifications", listOf("M6 16.5V11a6 6 0 0 1 12 0v5.5l1.5 2h-15z", "M10 20.5a2 2 0 0 0 4 0", "M12 5V3.5")),
        line("Outlined.OpenInBrowser", listOf("M3.50 12a8.5 8.5 0 1 0 17.0 0a8.5 8.5 0 1 0 -17.0 0", "M12 3.5c-2.4 2.4-3.4 5.4-3.4 8.5s1 6.1 3.4 8.5c2.4-2.4 3.4-5.4 3.4-8.5s-1-6.1-3.4-8.5", "M3.5 12h17M5 8h14M5 16h14")),
        line("Outlined.PhotoCamera", listOf("M3.5 7.5h4l1.5-2.5h6l1.5 2.5h4v12h-17z", "M8.40 13.2a3.6 3.6 0 1 0 7.2 0a3.6 3.6 0 1 0 -7.2 0")),
        // M10: "画像を大きくする": the small picture in its corner with the arrow going out of it (42-02).
        line("Outlined.PhotoSizeSelectLarge", listOf("M4 11V4h16v16h-7", "M4 15h5v5H4z", "M10.5 13.5L17 7", "M12.5 7H17v4.5")),
        line("Outlined.PhotoSizeSelectSmall", listOf("M9.5 4h10.5v10.5h-4", "M4 15h5v5H4z", "M17 7l-6.5 6.5", "M10.5 9.5v4h4")),
        line("Outlined.RecordVoiceOver", listOf("M9.5 6a2.5 2.5 0 0 1 5 0v5.5a2.5 2.5 0 0 1-5 0z", "M6.5 11a5.5 5.5 0 0 0 11 0", "M12 16.5v4M9 20.5h6")),
        line("Outlined.Refresh", listOf("M19.06 15.76A8 8 0 1 0 12.28 20.00 M9.68 17.60L12.28 20.00L9.88 22.00")),
        line("Outlined.Remove", listOf("M4.5 12h15")),
        line("Outlined.RemoveCircleOutline", listOf("M3.50 12a8.5 8.5 0 1 0 17.0 0a8.5 8.5 0 1 0 -17.0 0", "M7.5 12h9")),
        line("Outlined.SaveAlt", listOf("M12 3.5v11.5M7.5 10.5L12 15l4.5-4.5M4.5 14.5v5.5h15v-5.5")),
        line("Outlined.PlayArrow", listOf("M8 5.5l11 6.5l-11 6.5z")),
        line("Outlined.Search", listOf("M4.00 10.5a6.5 6.5 0 1 0 13.0 0a6.5 6.5 0 1 0 -13.0 0", "M15.3 15.3l5.2 5.2")),
        line("Outlined.Settings", listOf("M19.07 9.81 L21.37 9.90 L21.37 14.10 L19.07 14.19 L18.55 15.45 L20.11 17.14 L17.14 20.11 L15.45 18.55 L14.19 19.07 L14.10 21.37 L9.90 21.37 L9.81 19.07 L8.55 18.55 L6.86 20.11 L3.89 17.14 L5.45 15.45 L4.93 14.19 L2.63 14.10 L2.63 9.90 L4.93 9.81 L5.45 8.55 L3.89 6.86 L6.86 3.89 L8.55 5.45 L9.81 4.93 L9.90 2.63 L14.10 2.63 L14.19 4.93 L15.45 5.45 L17.14 3.89 L20.11 6.86 L18.55 8.55Z M8.80 12a3.2 3.2 0 1 0 6.4 0a3.2 3.2 0 1 0 -6.4 0")),
        // M13: 「URLスキームアクション」: three dots joined by two lines (the original's share mark, 40-01).
        line("Outlined.Share", listOf("M7.8 11l8.4-4.2M7.8 13l8.4 4.2", "M3.80 12a2.2 2.2 0 1 0 4.4 0a2.2 2.2 0 1 0 -4.4 0",
            "M15.80 5.8a2.2 2.2 0 1 0 4.4 0a2.2 2.2 0 1 0 -4.4 0", "M15.80 18.2a2.2 2.2 0 1 0 4.4 0a2.2 2.2 0 1 0 -4.4 0")),
        line("Outlined.Star", listOf("M12.00 3.40 L14.29 9.44 L20.75 9.76 L15.71 13.81 L17.41 20.04 L12.00 16.50 L6.59 20.04 L8.29 13.81 L3.25 9.76 L9.71 9.44Z"), fills = listOf("M12.00 3.40 L14.29 9.44 L20.75 9.76 L15.71 13.81 L17.41 20.04 L12.00 16.50 L6.59 20.04 L8.29 13.81 L3.25 9.76 L9.71 9.44Z")),
        line("Outlined.StarOutline", listOf("M12.00 3.40 L14.29 9.44 L20.75 9.76 L15.71 13.81 L17.41 20.04 L12.00 16.50 L6.59 20.04 L8.29 13.81 L3.25 9.76 L9.71 9.44Z")),
        line("Outlined.Tab", listOf("M3.5 8h17v11.5h-17z", "M3.5 8V5h7v3")),
        line("Outlined.Tune", listOf("M4 7h3.2M10.8 7H20M4 12h9.2M16.8 12H20M4 17h1.2M8.8 17H20", "M7.20 7a1.8 1.8 0 1 0 3.6 0a1.8 1.8 0 1 0 -3.6 0", "M13.20 12a1.8 1.8 0 1 0 3.6 0a1.8 1.8 0 1 0 -3.6 0", "M5.20 17a1.8 1.8 0 1 0 3.6 0a1.8 1.8 0 1 0 -3.6 0")),
        line("Outlined.VerticalAlignBottom", listOf("M12 3.5v13M7 11.5l5 5 5-5M4.5 20.5h15")),
        line("Outlined.VerticalAlignTop", listOf("M4.5 3.5h15M12 20.5v-13M7 12.5l5-5 5 5")),
        line("Outlined.Videocam", listOf("M3.5 7h11.5v10H3.5z", "M15 10.5l5.5-3v9l-5.5-3")),
        line("Outlined.ViewInAr", listOf("M12 3.5l8 4.5v8.5l-8 4.5-8-4.5V8z", "M4 8l8 4.5L20 8", "M12 12.5V21")),
        // M17: "NG ON" after 「NGを無効にする」 was pressed (49-02): the same "NG" over "ON".
        line("Outlined.Visibility", listOf("M8 10.5v-5l3 5v-5", "M17.2 6.5A2.5 2.5 0 1 0 17.6 8H15.6", "M6.4 16a2.3 2.5 0 1 0 4.6 0a2.3 2.5 0 1 0 -4.6 0", "M13.2 18.5v-5l3.4 5v-5")),
        line("Outlined.AccountTree", listOf("M3.5 4.5h5v4h-5z", "M15.5 4.5h5v4h-5z", "M15.5 15.5h5v4h-5z", "M8.5 6.5h7M12 6.5v11h3.5")),
        line("Outlined.PushPin", listOf("M9 4.5h6M10 4.5v6L7 14h10l-3-3.5v-6M12 14v6")),
        line("Outlined.VisibilityOff", listOf("M8 10.5v-5l3 5v-5", "M17.2 6.5A2.5 2.5 0 1 0 17.6 8H15.6", "M5.2 16a2.3 2.5 0 1 0 4.6 0a2.3 2.5 0 1 0 -4.6 0", "M14.3 13.5h-2.8v5M11.5 16h2.3", "M18.8 13.5H16v5M16 16h2.3"))
    )
}

/** The line glyph drawn for the Material glyph named [materialName], or null when the mode has none for it. */
internal fun futaberLineIconFor(materialName: String): ImageVector? = FUTABER_LINE_ICONS[materialName]

/** The names of the Material glyphs the line set covers. */
internal val futaberLineIconNames: Set<String> get() = FUTABER_LINE_ICONS.keys

/** [vector] as it is drawn: the line glyph of an outlined Material glyph, else itself (the mode's own icons). */
internal fun futaberIconFor(vector: ImageVector): ImageVector = FUTABER_LINE_ICONS[vector.name] ?: vector

/** The icon every button of this mode draws: the line glyph for the given one. */
@Composable
internal fun FutaberIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    androidx.compose.material3.Icon(
        imageVector = remember(imageVector) { futaberIconFor(imageVector) },
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint
    )
}
