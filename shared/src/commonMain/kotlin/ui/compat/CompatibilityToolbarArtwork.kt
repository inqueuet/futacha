package com.valoser.futacha.shared.ui.compat

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import com.valoser.futacha.shared.compat.CompatToolbarSurface
import futacha.shared.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

internal sealed interface CompatToolbarArtwork {
    data class Resource(val drawable: DrawableResource) : CompatToolbarArtwork
    data class Vector(val image: ImageVector) : CompatToolbarArtwork
}

/**
 * Resolves the exact surface-specific artwork declared by the four ToolbarData
 * classes in sample/1.apk. Keys alone are insufficient: `gallery`, for
 * example, deliberately uses different PNGs on Thread and Viewer.
 */
internal fun compatToolbarArtwork(
    surface: CompatToolbarSurface,
    key: String,
    selected: Boolean = false
): CompatToolbarArtwork = CompatToolbarArtwork.Resource(
    when (surface) {
        CompatToolbarSurface.CATALOG -> when (key) {
            "post" -> Res.drawable.menu_ico_catalog_toolbar_post
            "reload" -> Res.drawable.menu_ico_catalog_toolbar_reload
            "search" -> Res.drawable.menu_ico_catalog_toolbar_search
            "sort" -> Res.drawable.menu_ico_catalog_sort
            "board" -> Res.drawable.menu_ico_catalog_toolbar_board
            "tab" -> if (selected) {
                Res.drawable.menu_ico_catalog_toolbar_tab_update
            } else {
                Res.drawable.menu_ico_catalog_toolbar_tab
            }
            "privacy" -> Res.drawable.menu_ico_catalog_toolbar_privacy
            "bypass" -> if (selected) {
                Res.drawable.menu_ico_catalog_toolbar_server_bypass_on
            } else {
                Res.drawable.menu_ico_catalog_toolbar_server_bypass_off
            }
            "check" -> Res.drawable.menu_ico_catalog_toolbar_check
            "undo" -> Res.drawable.catalog_undo
            "dropped" -> Res.drawable.menu_ico_catalog_toolbar_dropped
            "quickng" -> if (selected) Res.drawable.ngon else Res.drawable.ngoff
            "drawer" -> Res.drawable.drawer
            "other" -> Res.drawable.menu_ico_post_toolbar_other
            else -> return CompatToolbarArtwork.Vector(compatToolbarIcon(key))
        }

        CompatToolbarSurface.THREAD -> when (key) {
            "post" -> Res.drawable.menu_ico_thread_toolbar_post
            "reload" -> Res.drawable.menu_ico_thread_toolbar_reload
            "undo" -> Res.drawable.catalog_undo
            "search" -> Res.drawable.menu_ico_thread_toolbar_search
            "top" -> Res.drawable.menu_ico_thread_toolbar_vertical_align_top
            "page_up" -> Res.drawable.menu_ico_thread_toolbar_arrow_upward
            "page_down" -> Res.drawable.menu_ico_thread_toolbar_arrow_downward
            "bottom" -> Res.drawable.menu_ico_thread_toolbar_vertical_align_bottom
            "gallery" -> Res.drawable.menu_ico_thread_toolbar_gallery
            "tab" -> if (selected) {
                Res.drawable.menu_ico_thread_toolbar_tab_update
            } else {
                Res.drawable.menu_ico_thread_toolbar_tab
            }
            "privacy" -> Res.drawable.menu_ico_thread_toolbar_privacy
            "extract" -> Res.drawable.menu_ico_thread_toolbar_extract
            "bypass" -> if (selected) {
                Res.drawable.menu_ico_thread_toolbar_server_bypass_on
            } else {
                Res.drawable.menu_ico_thread_toolbar_server_bypass_off
            }
            "scroll" -> Res.drawable.menu_ico_thread_toolbar_scroll
            "check" -> Res.drawable.menu_ico_thread_toolbar_check
            "close" -> Res.drawable.menu_ico_thread_toolbar_close
            "quickng" -> if (selected) Res.drawable.ngon else Res.drawable.ngoff
            "drawer" -> Res.drawable.drawer
            "autoscroll" -> if (selected) Res.drawable.pause else Res.drawable.autoscroll
            "other" -> Res.drawable.menu_ico_post_toolbar_other
            else -> return CompatToolbarArtwork.Vector(compatToolbarIcon(key))
        }

        CompatToolbarSurface.VIEWER -> when (key) {
            "download" -> Res.drawable.menu_ico_viewer_toolbar_dl
            "search" -> Res.drawable.menu_ico_viewer_toolbar_search
            "back" -> Res.drawable.menu_ico_viewer_toolbar_back
            "gallery" -> Res.drawable.menu_ico_viewer_toolbar_gallery
            "left" -> Res.drawable.menu_ico_viewer_toolbar_left
            "right" -> Res.drawable.menu_ico_viewer_toolbar_right
            "share" -> Res.drawable.menu_ico_viewer_toolbar_share
            "info" -> Res.drawable.menu_ico_viewer_toolbar_info
            "screen" -> Res.drawable.menu_ico_viewer_toolbar_screen
            "privacy" -> Res.drawable.menu_ico_catalog_toolbar_privacy
            "other" -> Res.drawable.menu_ico_post_toolbar_other
            else -> return CompatToolbarArtwork.Vector(compatToolbarIcon(key))
        }

        CompatToolbarSurface.POST -> when (key) {
            "send" -> Res.drawable.menu_ico_post_send
            "attach" -> Res.drawable.menu_ico_post_attach
            "attach_clear" -> Res.drawable.menu_ico_post_attach_clear
            "pallete" -> Res.drawable.menu_ico_post_pallete
            "sio" -> Res.drawable.menu_ico_post_ups
            "voice_input" -> Res.drawable.menu_ico_post_voice_input
            "network_info" -> Res.drawable.menu_ico_post_network_info
            "model_info" -> Res.drawable.menu_ico_post_model_info
            "reset" -> Res.drawable.menu_ico_post_reset
            "discard" -> Res.drawable.menu_ico_post_discard
            "other" -> Res.drawable.menu_ico_post_toolbar_other
            else -> return CompatToolbarArtwork.Vector(compatToolbarIcon(key))
        }
    }
)

@Composable
internal fun CompatToolbarArtworkIcon(
    artwork: CompatToolbarArtwork,
    contentDescription: String?,
    tint: Color,
    preserveResourceColors: Boolean = true,
    modifier: Modifier = Modifier
) {
    when (artwork) {
        is CompatToolbarArtwork.Resource -> Icon(
            painter = painterResource(artwork.drawable),
            contentDescription = contentDescription,
            // The reference PNGs are not all monochrome: NG-on and the tab
            // update badge contain intentional red pixels.  Applying a
            // Compose tint here would silently turn them white/yellow.
            tint = if (preserveResourceColors) Color.Unspecified else tint,
            // Preserve the source RGB values while still honoring the
            // reference toolbar's disabled/inactive alpha.
            modifier = if (preserveResourceColors) {
                modifier.graphicsLayer { alpha = tint.alpha }
            } else {
                modifier
            }
        )

        is CompatToolbarArtwork.Vector -> Icon(
            imageVector = artwork.image,
            contentDescription = contentDescription,
            tint = tint,
            modifier = modifier
        )
    }
}
