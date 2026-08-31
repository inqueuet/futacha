package com.valoser.futacha.shared.ui.compat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Exact path shipped as catalog_undo by sample/1.apk. */
internal const val COMPAT_REFERENCE_CATALOG_UNDO_PATH =
    "M151.17,196.03C199.9,196.58 219.97,160.68 220.19,131C220.03,100.13 200.64,64.9 " +
        "150.35,64.77L70.36,64.6L70.02,35.26L26.36,75.6L70.03,120.93L69.52,89.6L150.71,89.81C182.82," +
        "90.14 193.64,110.68 193.79,130.09C193.2,150.22 184.02,171.09 150.22,170.3L80.1,170.47L80.16," +
        "196.29L151.17,196.03Z"

/** Exact three paths shipped as menu_ico_catalog_toolbar_dropped by sample/1.apk. */
internal val COMPAT_REFERENCE_CATALOG_DROPPED_PATHS = listOf(
    "M 8.515 1.019 A 7 7 0 0 0 8 1 V 0 a 8 8 0 0 1 0.589 0.022 z m 2.004 0.45 a 7 7 0 0 0 " +
        "-0.985 -0.299 l 0.219 -0.976 q 0.576 0.129 1.126 0.342 z m 1.37 0.71 a 7 7 0 0 0 -0.439 -0.27 l " +
        "0.493 -0.87 a 8 8 0 0 1 0.979 0.654 l -0.615 0.789 a 7 7 0 0 0 -0.418 -0.302 z m 1.834 1.79 a " +
        "7 7 0 0 0 -0.653 -0.796 l 0.724 -0.69 q 0.406 0.429 0.747 0.91 z m 0.744 1.352 a 7 7 0 0 0 " +
        "-0.214 -0.468 l 0.893 -0.45 a 8 8 0 0 1 0.45 1.088 l -0.95 0.313 a 7 7 0 0 0 -0.179 -0.483 m " +
        "0.53 2.507 a 7 7 0 0 0 -0.1 -1.025 l 0.985 -0.17 q 0.1 0.58 0.116 1.17 z m -0.131 1.538 q 0.05 " +
        "-0.254 0.081 -0.51 l 0.993 0.123 a 8 8 0 0 1 -0.23 1.155 l -0.964 -0.267 q 0.069 -0.247 0.12 -0.501 m " +
        "-0.952 2.379 q 0.276 -0.436 0.486 -0.908 l 0.914 0.405 q -0.24 0.54 -0.555 1.038 z m -0.964 1.205 q " +
        "0.183 -0.183 0.35 -0.378 l 0.758 0.653 a 8 8 0 0 1 -0.401 0.432 z",
    "M 8 1 a 7 7 0 1 0 4.95 11.95 l 0.707 0.707 A 8.001 8.001 0 1 1 8 0 z",
    "M 7.5 3 a 0.5 0.5 0 0 1 0.5 0.5 v 5.21 l 3.248 1.856 a 0.5 0.5 0 0 1 -0.496 0.868 l -3.5 " +
        "-2 A 0.5 0.5 0 0 1 7 9 V 3.5 a 0.5 0.5 0 0 1 0.5 -0.5"
)

/** White silhouettes shipped as the thread scroll-bar and viewer screen-mode PNGs in sample/1.apk. */
internal const val COMPAT_REFERENCE_THREAD_SCROLL_PATH =
    "M18,84H85V108H18Z M122,61A35.5,35.5 0,1 1,122,132A35.5,35.5 0,1 1,122,61Z M160,84H173V108H160Z"

internal const val COMPAT_REFERENCE_VIEWER_SCREEN_PATH =
    "M38,38H154Q167,38 167,51V141Q167,154 154,154H38Q25,154 25,141V51Q25,38 38,38Z " +
        "M39,51H153V141H39Z " +
        "M43,96L57,84V108Z M149,96L135,84V108Z M96,56L84,70H108Z M96,136L84,122H108Z"

internal val CompatReferenceCatalogUndoIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ReferenceCatalogUndo",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 256f,
        viewportHeight = 256f
    ).apply {
        addPath(
            pathData = PathParser().parsePathString(COMPAT_REFERENCE_CATALOG_UNDO_PATH).toNodes(),
            pathFillType = PathFillType.EvenOdd,
            fill = SolidColor(Color.White)
        )
    }.build()
}

internal val CompatReferenceCatalogDroppedIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ReferenceCatalogDropped",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 16f,
        viewportHeight = 16f
    ).apply {
        addGroup(pivotX = 8f, pivotY = 8f, scaleX = 0.67f, scaleY = 0.67f)
        COMPAT_REFERENCE_CATALOG_DROPPED_PATHS.forEach { path ->
            addPath(
                pathData = PathParser().parsePathString(path).toNodes(),
                fill = SolidColor(Color.White)
            )
        }
        clearGroup()
    }.build()
}

internal val CompatReferenceThreadScrollIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ReferenceThreadScroll",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 192f,
        viewportHeight = 192f
    ).apply {
        addPath(
            pathData = PathParser().parsePathString(COMPAT_REFERENCE_THREAD_SCROLL_PATH).toNodes(),
            fill = SolidColor(Color.White)
        )
    }.build()
}

internal val CompatReferenceViewerScreenIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ReferenceViewerScreen",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 192f,
        viewportHeight = 192f
    ).apply {
        addPath(
            pathData = PathParser().parsePathString(COMPAT_REFERENCE_VIEWER_SCREEN_PATH).toNodes(),
            pathFillType = PathFillType.EvenOdd,
            fill = SolidColor(Color.White)
        )
    }.build()
}
