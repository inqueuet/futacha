package com.valoser.futacha.shared.util

import com.valoser.futacha.shared.model.AppIconVariant
import com.valoser.futacha.shared.desktop.desktopResource
import java.awt.Taskbar
import javax.imageio.ImageIO

actual fun applyAppIconVariant(platformContext: Any?, variant: AppIconVariant) {
    if (Taskbar.isTaskbarSupported()) {
        val taskbar = Taskbar.getTaskbar()
        if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
            taskbar.iconImage = ImageIO.read(desktopResource("icons/${variant.name}.png"))
        }
    }
}
