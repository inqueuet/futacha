package com.valoser.futacha.shared.ui.futaber

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * C12 (L-B9): the EULA row shows this app's own terms, the same text as the first-launch screen. That screen is the
 * iOS host's (`UserGeneratedContentEulaView` in ContentView.swift), so every text of the settings' copy must be there.
 */
class FutaberSettingsEulaSourceTest {
    @Test
    fun theSettingsEulaIsTheFirstLaunchScreensText() {
        val file = File("../iosApp/iosApp/ContentView.swift")
        assertTrue(file.isFile, "ContentView.swift not found from ${File(".").absolutePath}")
        val swift = file.readText()
        val texts = listOf(FUTABER_EULA_TITLE, FUTABER_EULA_SUBTITLE, FUTABER_EULA_INTRO, FUTABER_EULA_FOOTER) +
            FUTABER_EULA_SECTIONS.flatMap { (title, body) -> listOf(title, body) }
        for (text in texts) assertTrue(swift.contains("\"$text\""), "not on the first-launch screen: $text")
    }
}
