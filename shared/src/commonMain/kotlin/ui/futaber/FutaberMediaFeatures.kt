package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.ui.image.PromptSettingsSection
import com.valoser.futacha.shared.ui.media.DeviceImageEditorSettings
import com.valoser.futacha.shared.ui.media.DeviceVideoEditorSettings
import com.valoser.futacha.shared.ui.media.MediaHelpButton

/**
 * The media features' settings page of this mode: prompt display, image editing, video editing and their help, the
 * same four parts ふたちゃ and としあき(仮) show (the switches are shared, all off at first). They are drawn on a card
 * of the mode's own colours: the parts take their text from the surface they sit on.
 */
@Composable
internal fun FutaberMediaSettingsPage() {
    val colors = LocalFutaberColors.current
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp).testTag("futaber-media-settings"),
        shape = FutaberShapes.card,
        color = colors.bar,
        contentColor = colors.body
    ) {
        Column {
            PromptSettingsSection()
            HorizontalDivider(color = colors.separator, thickness = 0.5.dp)
            DeviceImageEditorSettings()
            HorizontalDivider(color = colors.separator, thickness = 0.5.dp)
            DeviceVideoEditorSettings()
            HorizontalDivider(color = colors.separator, thickness = 0.5.dp)
            MediaHelpButton()
        }
    }
}

/**
 * The "画像編集" / "動画編集" entries of the operation menu, after the original app's entries. An entry shows only when
 * the matching editor is switched on in the settings ([image] / [video] are the editor launchers, null while it is off),
 * so with the defaults the menu is the one the original app has. [onDismiss] closes the menu before the picker opens.
 */
internal fun futaberMediaEditMenuItems(
    image: (() -> Unit)?,
    video: (() -> Unit)?,
    onDismiss: () -> Unit
): List<FutaberMenuItem> = listOfNotNull(
    image?.let { launch ->
        FutaberMenuItem("image-edit", "画像編集", Icons.Outlined.Image) { onDismiss(); launch() }
    },
    video?.let { launch ->
        FutaberMenuItem("video-edit", "動画編集", Icons.Outlined.Videocam) { onDismiss(); launch() }
    }
)
