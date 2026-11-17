package com.valoser.futacha.shared.ui.futaber

import androidx.compose.ui.graphics.Color
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * L-B2: what the screens of ふたばー風 ask for is drawn the original's way. Every glyph a screen names has a line glyph
 * (none falls through to a Material glyph), no screen draws a Material icon around [FutaberIcon], the floating bar and
 * the redesign's four-step shapes are gone, and no colour of any theme is one of the 2026-10-06 redesign's.
 */
class FutaberLookSourceContractTest {
    private val sources: List<File> =
        File("src/commonMain/kotlin/ui/futaber").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    @Test
    fun everyGlyphTheScreensNameHasALineGlyph() {
        assertTrue(sources.size > 50, "the mode's sources are found (${sources.size})")
        val glyph = Regex("""Icons\.((?:AutoMirrored\.)?[A-Z][A-Za-z]+)\.([A-Z][A-Za-z0-9]+)""")
        val named = sources.filter { it.name != "FutaberIcons.kt" }.flatMap { file ->
            glyph.findAll(file.readText()).map { Triple(file.name, it.groupValues[1], it.groupValues[2]) }.toList()
        }
        assertTrue(named.size > 40, "glyphs found (${named.size})")
        named.forEach { (file, family, glyphName) ->
            assertTrue(family == "Outlined" || family == "AutoMirrored.Outlined", "$file: Icons.$family.$glyphName is not an outlined glyph")
            val key = "$family.$glyphName"
            assertTrue(futaberLineIconFor(key) != null, "$file: $key has no line glyph")
        }
    }

    @Test
    fun noScreenDrawsAMaterialIconDirectly() {
        val direct = Regex("""(?<![A-Za-z.])Icon\(""")
        sources.filter { it.name != "FutaberIcons.kt" }.forEach { file ->
            val text = file.readText()
            assertTrue(direct.find(text) == null, "${file.name} draws Icon( directly instead of FutaberIcon(")
            assertTrue("material3.Icon(" !in text, "${file.name} draws material3.Icon( directly")
        }
    }

    @Test
    fun theRedesignsFloatingBarAndShapesAreGone() {
        val all = sources.joinToString("\n") { it.readText() }
        listOf("futaberFloatingBar", "FUTABER_FLOATING_BAR_EXTRA_DP", "FutaberShapes.pill", "FutaberShapes.small", "FUTABER_ROUNDED_ICONS")
            .forEach { assertTrue(it !in all, "$it is still used") }
        assertTrue("futaber-thread-back" !in all, "the thread's ← button is gone")
    }

    /**
     * 2026-11-04 (the user's decision): the camera menu's 「カメラ」 was taken out, so the app takes no picture anywhere: no
     * camera usage string for iOS, no CAMERA permission and no camera's file path for Android, and no screen of the
     * mode opens a camera.
     */
    @Test
    fun theAppUsesNoCamera() {
        val pbxproj = File("../iosApp/iosApp.xcodeproj/project.pbxproj")
        assertTrue(pbxproj.isFile, "the iOS project is found")
        assertTrue("NSCameraUsageDescription" !in pbxproj.readText(), "the iOS app declares a camera use")
        val manifest = File("../app-android/src/main/AndroidManifest.xml")
        assertTrue(manifest.isFile, "the Android manifest is found")
        assertTrue("android.permission.CAMERA" !in manifest.readText(), "the Android app asks for the camera")
        assertTrue("futaber_camera" !in File("../app-android/src/main/res/xml/compat_file_paths.xml").readText())
        val platformSources = listOf("androidMain", "iosMain", "jvmMain").flatMap { set ->
            File("src/$set/kotlin/ui/futaber").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        }
        assertTrue(platformSources.isNotEmpty(), "the platform sources are found")
        (sources + platformSources).forEach { file ->
            val text = file.readText()
            listOf("TakePicture", "UIImagePickerControllerSourceTypeCamera", "rememberFutaberCamera", "ACTION_IMAGE_CAPTURE")
                .forEach { assertTrue(it !in text, "${file.name} still opens a camera ($it)") }
        }
        assertEquals(listOf("recent", "file", "library"), futaberCameraMenuOriginals(filesRefused = false).map { it.id })
    }

    /** Every colour role of every theme, read from the data class (its fields are the packed colour values). */
    private fun FutaberColors.allColours(): Map<String, Color> =
        FutaberColors::class.java.declaredFields.filter { it.type == java.lang.Long.TYPE }.associate { field ->
            field.isAccessible = true
            field.name to Color(field.getLong(this).toULong())
        }

    @Test
    fun noThemeKeepsTheRedesignsColours() {
        val redesign = listOf(
            Color(0xFF0E7C73), Color(0xFF5FD0C4), // the teal accent, light and dark
            Color(0xFFFBFAF7), Color(0xFFF0EDE7), // the warm white page and its capsule
            Color(0xFF15171A), Color(0xFF23272C)  // the redesign's dark page and capsule
        )
        listOf(FutaberTokens.light, FutaberTokens.dark, FutaberTokens.oldDark, FutaberTokens.futaba).forEach { theme ->
            val colours = theme.allColours()
            assertTrue(colours.size >= 60, "every role is read (${colours.size})")
            colours.forEach { (role, colour) -> assertTrue(colour !in redesign, "$role is a redesign colour") }
        }
        assertEquals(Color.White, FutaberTokens.light.allColours().getValue("bar"))
    }

    /**
     * L-B3 (V32・V11): the thread's and the catalog's lists draw the thin scroll bar (one function, so the fast scroll bar
     * of E5 can take its place), and a post's picture is never clipped to rounded corners.
     */
    @Test
    fun theListsDrawTheScrollBarAndPicturesKeepSquareCorners() {
        val byName = sources.associateBy { it.name }
        val thread = byName.getValue("FutaberThread.kt").readText()
        val catalog = byName.getValue("FutaberCatalog.kt").readText()
        assertTrue("rememberFutaberScrollIndicator(listState)" in thread && ".then(scrollIndicator)" in thread, "the thread's list has no scroll bar")
        assertTrue("rememberFutaberScrollIndicator(state)" in catalog && "rememberFutaberScrollIndicator(state, columns)" in catalog,
            "the catalog's list and grid have no scroll bar")
        val parts = byName.getValue("FutaberPostParts.kt").readText()
        val attachment = parts.substringAfter("internal fun FutaberPostAttachment(").substringBefore("// ---- F22")
        assertTrue("clip(" !in attachment && "RoundedCornerShape" !in attachment, "a post's picture is clipped")
    }

    /**
     * L-B6 (M54・G34・V34): the operation menu is drawn in the thread's layer over the whole screen (its veil covers the
     * bottom bar too), not inside the thread's body; iOS's edge swipe does not close it; no label is broken by hand.
     */
    @Test
    fun theOperationMenuCoversTheBarLeavesTheEdgeSwipeAndBreaksLabelsByWidth() {
        val byName = sources.associateBy { it.name }
        val thread = byName.getValue("FutaberThread.kt").readText()
        val layer = thread.substringAfter("    FutaberScreenLayer {")
        val body = thread.substringBefore("    FutaberScreenLayer {")
        assertTrue("FutaberOperationMenu(" in layer, "the menu is not in the layer over the whole screen")
        assertTrue("FutaberOperationMenu(" !in body, "the menu is still drawn inside the thread's body")
        assertTrue("PlatformBackHandler(enabled = menuOpen)" !in thread, "the menu's back is the menu's own (no edge swipe)")
        val menu = byName.getValue("FutaberOperationMenu.kt").readText()
        assertTrue("iosEdgeGestureEnabled = false" in menu, "the menu lets iOS's edge swipe close it")
        val all = sources.joinToString("\n") { it.readText() }
        assertTrue("futaberBalancedLabel" !in all, "a label is still broken by hand")
        assertTrue("LineBreak.Simple" in menu, "the label is not broken by its width")
    }
}
