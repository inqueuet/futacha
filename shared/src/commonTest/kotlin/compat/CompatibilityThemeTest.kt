package compat

import androidx.compose.ui.graphics.Color
import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.ui.compat.compatPostQuotesOwnPost
import com.valoser.futacha.shared.ui.compat.compatibilityPaletteFor
import com.valoser.futacha.shared.ui.compat.compatibilityLoadingColor
import com.valoser.futacha.shared.ui.compat.compatibilityLoadingUsesIcon
import com.valoser.futacha.shared.ui.compat.compatibilityPopupContent
import com.valoser.futacha.shared.ui.compat.compatibilityPopupSurface
import com.valoser.futacha.shared.ui.compat.compatibilitySaidaneColor
import com.valoser.futacha.shared.ui.compat.compatibilitySettingsCategoryColor
import com.valoser.futacha.shared.ui.compat.compatibilityUsesDarkStatusBarIcons
import com.valoser.futacha.shared.ui.compat.statusBarColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CompatibilityThemeTest {
    @Test
    fun selfPostAndSelfQuoteClassificationMatchesReferenceHeaderPositions() {
        val own = setOf("123")
        val ownPost = CompatPostSnapshot(0, "123", timestamp = "", messageHtml = "本文")
        val directReply = CompatPostSnapshot(1, "124", timestamp = "", messageHtml = ">>123\n返信")
        val unrelated = CompatPostSnapshot(2, "125", timestamp = "", messageHtml = ">>999\n返信")

        assertFalse(compatPostQuotesOwnPost(ownPost, own))
        assertEquals(true, compatPostQuotesOwnPost(directReply, own))
        assertFalse(compatPostQuotesOwnPost(unrelated, own))
    }

    @Test
    fun searchHighlightsMatchEveryTargetThemeBranch() {
        val expectedRows = mapOf(
            "モノクロ" to Color(0xFFDDDDDD),
            "ふたば" to Color(0xFFEEC0A3),
            "ブルー" to Color(0xFFD4E7F0),
            "ピンク" to Color(0xFFF2E0E9),
            "ブラック" to Color(0xFF3C3030),
            null to Color(0xFFE0F2F1)
        )
        expectedRows.forEach { (theme, expected) ->
            assertEquals(expected, compatibilityPaletteFor(theme).searchResultBackground)
        }
        assertEquals(Color(0xFF005AA1), compatibilityPaletteFor("ブラック").searchTextHighlight)
        assertEquals(Color(0xFF005CE6), compatibilityPaletteFor(null).fileName)
        assertEquals(Color(0xFF008CE6), compatibilityPaletteFor("ブラック").fileName)
        assertEquals(Color(0xFFFFA500), compatibilityPaletteFor("ふたば").searchTextHighlight)
        assertEquals(Color(0xFFFFA500), compatibilityPaletteFor(null).searchTextHighlight)
        assertEquals(Color(0xFFBEBEBE), compatibilityPaletteFor("ブラック").text)
        assertEquals(Color.White, compatibilityPaletteFor("ふたば", "白").text)
        assertEquals(Color(0xFFCC1105), compatibilityPaletteFor("ふたば").identityTotal)
        assertEquals(Color(0xFFFC1105), compatibilityPaletteFor("ブラック").identityTotal)
        assertEquals(Color(0xFF007551), compatibilityPaletteFor("ブラック").headerAuthor)
        assertEquals(Color(0xFF91CAC3), compatibilityPaletteFor(null).newReplyBackground)
        assertEquals(Color.White, compatibilityPaletteFor(null).newReplyContent)
    }

    @Test
    fun statusBarIconContrastUsesChromeRatherThanPostTextColor() {
        assertFalse(compatibilityUsesDarkStatusBarIcons(compatibilityPaletteFor("ふたば")))
        assertFalse(compatibilityUsesDarkStatusBarIcons(compatibilityPaletteFor("ふたば", "白")))
        assertFalse(compatibilityUsesDarkStatusBarIcons(compatibilityPaletteFor("ブラック")))
    }

    @Test
    fun popupColorsFollowBlackThemeWithoutChangingLegacyLightPopup() {
        val light = compatibilityPaletteFor(null)
        val black = compatibilityPaletteFor("ブラック")

        assertEquals(Color(0xFFFAFAFA), compatibilityPopupSurface(light))
        assertEquals(Color(0xFF4C4C4C), compatibilityPopupContent(light))
        assertEquals(Color(0xFF1E1E1E), compatibilityPopupSurface(black))
        assertEquals(Color(0xFFDDDDDD), compatibilityPopupContent(black))
        assertEquals(
            Color(0xFFDDDDDD),
            compatibilityPopupContent(compatibilityPaletteFor("ブラック", "黒"))
        )
        assertEquals(
            Color(0xFF4C4C4C),
            compatibilityPopupContent(compatibilityPaletteFor("モノクロ", "白"))
        )
        assertEquals(Color(0xFF424242), black.dialogSurface)
        assertEquals(Color.White, light.dialogSurface)
        assertEquals(Color.White, compatibilitySettingsCategoryColor(black))
    }

    @Test
    fun baseColorsMatchTheReferenceApkResources() {
        val expected = mapOf(
            null to Triple(Color(0xFF009688), Color(0xFFE6E6E6), Color(0xFF333333)),
            "モノクロ" to Triple(Color(0xFF222222), Color(0xFFE6E6E6), Color(0xFF333333)),
            "ふたば" to Triple(Color(0xFF542D24), Color(0xFFFFFFEE), Color(0xFF800000)),
            "ブルー" to Triple(Color(0xFF03A9F4), Color(0xFFFBFBFB), Color(0xFF333333)),
            "ピンク" to Triple(Color(0xFFE91E63), Color(0xFFFAFAFA), Color(0xFF333333)),
            "ブラック" to Triple(Color.Black, Color.Black, Color(0xFFBEBEBE))
        )
        expected.forEach { (name, colors) ->
            val palette = compatibilityPaletteFor(name)
            assertEquals(colors.first, palette.chrome, name)
            assertEquals(colors.second, palette.background, name)
            assertEquals(colors.third, palette.text, name)
        }
        assertEquals(Color(0xFF00867B), compatibilityPaletteFor(null).statusBarColor())
        assertEquals(Color(0xFF26A69A), compatibilityPaletteFor(null).accent)
        assertEquals(Color(0xFF1E1E1E), compatibilityPaletteFor("ブラック").statusBarColor())
        assertEquals(Color.White, compatibilityPaletteFor("ブラック").accent)
        assertEquals(Color.White, compatibilityPaletteFor("ブラック").inputCursor)
        assertEquals(Color(0xFF117743), compatibilityPaletteFor("ふたば").inputCursor)
        listOf(
            "default" to null,
            "mono" to "モノクロ",
            "futaba" to "ふたば",
            "blue" to "ブルー",
            "pink" to "ピンク",
            "black" to "ブラック"
        ).forEach { (raw, display) ->
            assertEquals(
                compatibilityPaletteFor(display),
                compatibilityPaletteFor(raw),
                raw
            )
        }
    }

    @Test
    fun loadingArtworkUsesTheDedicatedColorsFromBothReferenceApks() {
        data class Expected(val theme: String?, val icon: Color, val progress: Color)
        listOf(
            Expected(null, Color(0xFF00A499), Color(0xFF00A499)),
            Expected("モノクロ", Color(0xFF2D2D2D), Color(0xFF5D5D5D)),
            Expected("ふたば", Color(0xFF673C2F), Color(0xFF673C2F)),
            Expected("ブルー", Color(0xFF00B8F6), Color(0xFF00B8F6)),
            Expected("ピンク", Color(0xFFEF3A76), Color(0xFFEF3A76)),
            Expected("ブラック", Color(0xFF2D2D2D), Color.White)
        ).forEach { expected ->
            val palette = compatibilityPaletteFor(expected.theme)
            assertEquals(expected.icon, compatibilityLoadingColor(palette, "アイコン"), expected.theme)
            assertEquals(expected.icon, compatibilityLoadingColor(palette, "icon"), expected.theme)
            assertEquals(expected.progress, compatibilityLoadingColor(palette, "デフォルト"), expected.theme)
            assertEquals(expected.progress, compatibilityLoadingColor(palette, "default"), expected.theme)
            assertEquals(expected.progress, compatibilityLoadingColor(palette, null), expected.theme)
        }
        assertEquals(true, compatibilityLoadingUsesIcon("icon"))
        assertEquals(true, compatibilityLoadingUsesIcon("アイコン"))
        assertEquals(false, compatibilityLoadingUsesIcon("default"))
    }

    @Test
    fun closedThreadUndoActionUsesEveryFinalApkThemeAttribute() {
        val expected = mapOf(
            null to Color(0xFFFFF176),
            "モノクロ" to Color(0xFFDDDDDD),
            "ふたば" to Color(0xFFFFCC00),
            "ブルー" to Color(0xFFFFF176),
            "ピンク" to Color(0xFFFFF176),
            "ブラック" to Color(0xFFFFF176)
        )
        expected.forEach { (theme, color) ->
            assertEquals(color, compatibilityPaletteFor(theme).closedThreadUndoAction, theme)
        }
    }

    @Test
    fun threadHeaderAndBodyColorsMatchBothReferenceApks() {
        listOf(null, "モノクロ", "ふたば", "ブルー", "ピンク").forEach { theme ->
            val palette = compatibilityPaletteFor(theme)
            assertEquals(Color(0xFFCC1105), palette.headerSubject, theme)
            assertEquals(Color(0xFF117743), palette.headerAuthor, theme)
            assertEquals(Color(0xFF005CE6), palette.headerEmail, theme)
            assertEquals(Color(0xFF005CE6), palette.headerSelfPost, theme)
            assertEquals(Color(0xFFF48FB1), palette.headerSelfQuote, theme)
            assertEquals(Color(0xFF117743), palette.headerQuoteAdd, theme)
            assertEquals(Color(0xFF333333), palette.headerSubtext, theme)
            assertEquals(Color(0xFF009688), palette.bodyLink, theme)
            assertEquals(Color(0xFF789922), palette.bodyQuote, theme)
            assertEquals(Color(0xFFCC1105), palette.bodyDropSoon, theme)
            assertEquals(Color(0xFF0000F0), palette.bodyIp, theme)
            assertEquals(Color(0xFFCCCCCC), palette.bodyErased, theme)
            assertEquals(Color(0xFFE25E00), compatibilitySaidaneColor(palette, "そうだねx2", 3), theme)
            assertEquals(Color.Red, compatibilitySaidaneColor(palette, "そうだねx3", 3), theme)
        }

        val black = compatibilityPaletteFor("ブラック")
        assertEquals(Color(0xFFFC1105), black.headerSubject)
        assertEquals(Color(0xFF007551), black.headerAuthor)
        assertEquals(Color(0xFF008CE6), black.headerEmail)
        assertEquals(Color(0xFF008CE6), black.headerSelfPost)
        assertEquals(Color(0xFFF48FB1), black.headerSelfQuote)
        assertEquals(Color(0xFF11A743), black.headerQuoteAdd)
        assertEquals(Color(0xFFBEBEBE), black.headerSubtext)
        assertEquals(Color(0xFF009688), black.bodyLink)
        assertEquals(Color(0xFFF99A00), compatibilitySaidaneColor(black, "そうだねx2", 3))
        assertEquals(Color.Red, compatibilitySaidaneColor(black, "そうだねx3", 3))
    }
}
