package com.valoser.futacha.shared.ui.compat

import androidx.compose.ui.Modifier
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.node.invalidateSemantics
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.stateDescription

/**
 * The separate fallback request is only useful for a distinct thumbnail.
 * Without one it was the same source URL decoded at viewport size: never
 * shown, but downloaded alongside the original request (E-10).
 */
internal fun compatViewerThumbnailFallbackUrl(thumbnailUrl: String?, mediaUrl: String?): String? =
    thumbnailUrl?.takeIf { it.isNotBlank() && it != mediaUrl }

internal fun compatViewerZoomStateDescription(transform: CompatViewerTransform): String =
    "拡大率 ${(transform.scale * 100f).toInt()}% " +
        "位置 ${transform.translation.x.toInt()},${transform.translation.y.toInt()}"

/**
 * Publishes the zoom state description without reading the transform in
 * composition. Reading it in the page body recomposed the whole viewer page
 * for every pinch event (E-11); this node observes the state itself and only
 * invalidates its semantics.
 */
internal fun Modifier.compatViewerZoomSemantics(
    enabled: Boolean,
    transform: () -> CompatViewerTransform
): Modifier = this then CompatViewerZoomSemanticsElement(enabled, transform)

private data class CompatViewerZoomSemanticsElement(
    val enabled: Boolean,
    val transform: () -> CompatViewerTransform
) : ModifierNodeElement<CompatViewerZoomSemanticsNode>() {
    override fun create() = CompatViewerZoomSemanticsNode(enabled, transform)

    override fun update(node: CompatViewerZoomSemanticsNode) {
        node.enabled = enabled
        node.transform = transform
        node.invalidateSemantics()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "compatViewerZoomSemantics"
        properties["enabled"] = enabled
    }
}

private class CompatViewerZoomSemanticsNode(
    var enabled: Boolean,
    var transform: () -> CompatViewerTransform
) : Modifier.Node(), SemanticsModifierNode, ObserverModifierNode {
    override fun SemanticsPropertyReceiver.applySemantics() {
        if (!enabled) return
        var current = CompatViewerTransform()
        observeReads { current = transform() }
        stateDescription = compatViewerZoomStateDescription(current)
    }

    override fun onObservedReadsChanged() {
        invalidateSemantics()
    }
}
