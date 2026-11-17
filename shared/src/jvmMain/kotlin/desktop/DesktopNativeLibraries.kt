package com.valoser.futacha.shared.desktop

import java.io.File

internal fun desktopResource(relative: String): File {
    val root = System.getProperty("compose.application.resources.dir")
    val roots = listOfNotNull(root?.let(::File), System.getProperty("futacha.resourcesDir")?.let(::File),
        File("app-desktop/resources/${DesktopPlatform.resourceDirectory}"), File("resources/${DesktopPlatform.resourceDirectory}"))
    return roots.map { File(it, relative) }.firstOrNull(File::exists)
        ?: error("必要なライブラリが見つかりません: $relative")
}
