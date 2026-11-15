package com.valoser.futacha.shared.ui.compat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompatTabPanePrivacySourceTest {
    @Test
    fun theTabPaneImagesFollowThePrivacyMode() {
        // Before: the tab pane drew the thumbnails as they are (1447423691).
        val root = java.io.File("src/commonMain/kotlin/ui/compat")
        val selector = java.io.File(root, "CompatTabSelector.kt").readText()
        assertTrue(selector.contains(".compatPrivacyImageEffect(privacyAlpha)"))
        val app = java.io.File(root, "CompatibilityApp.kt").readText()
        // Both the catalog's and the thread's tab pane get the value.
        assertEquals(2, Regex("""CompatTabSelector\(\s*tabs = tabs,[^)]*?privacyAlpha = if \(\w+PrivacyEnabled\)""").findAll(app).count())
    }
}
