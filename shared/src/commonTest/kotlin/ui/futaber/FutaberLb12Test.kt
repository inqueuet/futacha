package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.util.resolveUrlLaunchRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** L-B12: URL schemes (C05), the quote voice (C24), wallpapers (C14〜C17), the favourites' pictures (M14), the player (M19). */
class FutaberLb12Test {
    private fun post(id: String, html: String, image: String? = null, thumb: String? = null) = Post(
        id = id, author = null, subject = null, timestamp = "", messageHtml = html, imageUrl = image, thumbnailUrl = thumb
    )

    // ---- C05 / M13 ----
    @Test fun theBrowserSchemeReplacesUrlWithTheEncodedAddress() {
        assertEquals(
            "googlechromes://https%3A%2F%2Fexample.com%2Fa%3Fb%3D1",
            futaberUrlSchemeExpand("googlechromes://[url]", FUTABER_URL_SCHEME_URL_PLACEHOLDER, "https://example.com/a?b=1")
        )
    }

    @Test fun theEncoderKeepsSafeLettersAndEncodesJapaneseAsUtf8() {
        assertEquals("a-b_c.d~E9", futaberUrlSchemeEncode("a-b_c.d~E9"))
        assertEquals("%E3%81%82", futaberUrlSchemeEncode("あ"))
        assertEquals("%20%2F", futaberUrlSchemeEncode(" /"))
    }

    @Test fun anEmptyOrBrokenTemplateIsNotRegistered() {
        assertNull(futaberUrlSchemeExpand("", "[text]", "x"))
        assertNull(futaberUrlSchemeExpand("   ", "[text]", "x"))
        assertNull(futaberUrlSchemeExpand("not a scheme [text]", "[text]", "x"))
        assertNull(futaberUrlSchemeExpand("1abc://[text]", "[text]", "x"))
    }

    @Test fun theActionDoesNothingWithNoTemplateAndTheBrowserKeepsTheAddress() {
        val none = FutaberUrlSchemes()
        assertNull(none.actionTarget("https://x"))
        assertEquals("https://x", none.browserTarget("https://x"))
        val set = FutaberUrlSchemes(browser = "ghttp://[url]", action = "goodreader://download?url=[text]")
        assertEquals("goodreader://download?url=https%3A%2F%2Fx", set.actionTarget("https://x"))
        assertEquals("ghttp://https%3A%2F%2Fx", set.browserTarget("https://x"))
        assertTrue(FutaberUrlSchemes(browser = "oops").browserInvalid)
        assertFalse(set.browserInvalid)
    }

    @Test fun theLauncherTakesThePersonsSchemeButNotScriptsOrFiles() {
        assertNull(resolveUrlLaunchRequest("ghttp://x"))
        assertNotNull(resolveUrlLaunchRequest("ghttp://x", allowAnyScheme = true))
        assertNotNull(resolveUrlLaunchRequest("googlechromes://https%3A%2F%2Fx", allowAnyScheme = true))
        for (bad in listOf("javascript:alert(1)", "file:///etc/passwd", "data:text/html,x", "content://x", "intent://x", "1abc://x")) {
            assertNull(resolveUrlLaunchRequest(bad, allowAnyScheme = true), bad)
        }
    }

    @Test fun theSettingsListTheUrlSchemeFifthInFutabersGeneral() {
        val general = futaberSettingsTopGroups(FutaberSettingsAvailability()).first { it.id == "general" }.rows
        assertEquals(FutaberSettingsRow.UrlScheme, general[4])
        assertEquals(FutaberSettingsRow.Viewer, general[3])
    }

    @Test fun theSchemesAreReadFromThePreferences() {
        val s = FutaberDisplaySettings.from(
            mapOf(FutaberSettingKeys.URL_SCHEME_BROWSER to "ghttp://[url]", FutaberSettingKeys.URL_SCHEME_ACTION to "x://[text]")
        ).urlSchemes
        assertEquals("ghttp://[url]", s.browser)
        assertEquals("x://[text]", s.action)
        assertEquals(FutaberUrlSchemes(), FutaberDisplaySettings.from(emptyMap()).urlSchemes)
    }

    // ---- C24 ----
    @Test fun theQuotePitchBeginsAtHalfAndKeepsItsRange() {
        assertEquals(5, futaberQuotePitchTenths(null))
        assertEquals(5, futaberQuotePitchTenths("garbage"))
        assertEquals(5, futaberQuotePitchTenths("4"))
        assertEquals(5, futaberQuotePitchTenths("21"))
        assertEquals(12, futaberQuotePitchTenths("12"))
        assertEquals(5, FutaberDisplaySettings.from(emptyMap()).quotePitchTenths)
    }

    @Test fun theQuotedLinesAreReadAndMarkedApartFromTheRest() {
        val posts = listOf(post("1", "&gt;&gt;12 そうだね<br>ほんとだ<br>&gt;ひとこと"))
        val segments = futaberReadAloudSegments(posts)
        assertEquals(1, segments.size)
        val runs = futaberSpeechRuns(segments[0].body)
        assertEquals(listOf(FutaberSpeechRun("12 そうだね", true), FutaberSpeechRun("ほんとだ", false), FutaberSpeechRun("ひとこと", true)), runs)
        assertFalse(runs.any { FUTABER_QUOTE_MARK in it.text })
    }

    @Test fun aPostOfOnlyAQuoteMarkIsSkippedAndDeletedPostsStaySilent() {
        assertTrue(futaberReadAloudSegments(listOf(post("1", "&gt;&gt;"))).isEmpty())
        assertTrue(futaberReadAloudSegments(listOf(post("1", "text").copy(isDeleted = true))).isEmpty())
    }

    // ---- C14〜C17 ----
    @Test fun theWallpaperDefaultsAreFutabersAndTheNamesStayInsideTheFolder() {
        assertEquals(10, FutaberWallpaperSlot.Catalog.defaultOpacityPercent)
        assertEquals(10, FutaberWallpaperSlot.Thread.defaultOpacityPercent)
        assertEquals(20, FutaberWallpaperSlot.Post.defaultOpacityPercent)
        assertEquals(65, FutaberWallpaperSlot.Menu.defaultOpacityPercent)
        assertTrue(futaberWallpaperFileNameIsValid("catalog-123.jpg"))
        for (bad in listOf("", "../x.jpg", "a/b.png", "a\\b.png", "x.exe", "x")) assertFalse(futaberWallpaperFileNameIsValid(bad), bad)
    }

    @Test fun theWallpapersAreReadFromThePreferencesWithBadValuesIgnored() {
        val w = FutaberWallpapers.from(
            mapOf(
                FutaberWallpaperSlot.Catalog.fileKey to "catalog-1.png",
                FutaberWallpaperSlot.Thread.fileKey to "../evil.png",
                FutaberWallpaperSlot.Post.opacityKey to "35",
                FutaberWallpaperSlot.Menu.opacityKey to "400",
                FUTABER_MENU_BACKGROUND_KEY to "old"
            )
        )
        assertEquals("catalog-1.png", w.file(FutaberWallpaperSlot.Catalog))
        assertNull(w.file(FutaberWallpaperSlot.Thread))
        assertEquals(35, w.opacity(FutaberWallpaperSlot.Post))
        assertEquals(65, w.opacity(FutaberWallpaperSlot.Menu))
        assertEquals(FutaberMenuBackground.Old, w.menuBackground)
        assertEquals(FutaberMenuBackground.Default, FutaberWallpapers.from(emptyMap()).menuBackground)
    }

    @Test fun theOpacitySteppingStaysInRangeAndIsShownLikeFutabers() {
        assertEquals(15, futaberWallpaperOpacityStep(10, 1))
        assertEquals(0, futaberWallpaperOpacityStep(0, -1))
        assertEquals(100, futaberWallpaperOpacityStep(100, 1))
        assertEquals("0.10", futaberWallpaperOpacityShown(10))
        assertEquals("0.65", futaberWallpaperOpacityShown(65))
        assertEquals("1.00", futaberWallpaperOpacityShown(100))
    }

    // ---- M14 ----
    @Test fun theFavouritesPicturesFollowTheChoice() {
        val posts = listOf(post("1", "a", image = "https://x/1.jpg", thumb = "https://x/1s.jpg"))
        assertEquals("https://x/1.jpg", futaberFavoriteSavePosts(posts, FutaberFavoriteImageChoice.Full)[0].imageUrl)
        assertNull(futaberFavoriteSavePosts(posts, FutaberFavoriteImageChoice.Thumbnails)[0].imageUrl)
        assertEquals("https://x/1s.jpg", futaberFavoriteSavePosts(posts, FutaberFavoriteImageChoice.Thumbnails)[0].thumbnailUrl)
        assertNull(futaberFavoriteSavePosts(posts, FutaberFavoriteImageChoice.None)[0].thumbnailUrl)
        assertTrue(futaberFavoriteHasImages(posts))
        assertFalse(futaberFavoriteHasImages(listOf(post("2", "text"))))
    }

    @Test fun theFavouritesFolderIsPrivateAndPerThread() {
        val a = futaberFavoriteImagesFolder("may_b", "1")
        val b = futaberFavoriteImagesFolder("may_b", "2")
        assertTrue(a.startsWith("private/"))
        assertTrue(a != b)
    }

    // ---- M19 ----
    @Test fun thePlayerTakesOnlyHttpsYouTubeAddresses() {
        assertTrue(futaberPlayerUrlIsAllowed("https://www.youtube.com/watch?v=abc"))
        assertTrue(futaberPlayerUrlIsAllowed("https://youtu.be/abc"))
        assertFalse(futaberPlayerUrlIsAllowed("http://youtube.com/watch?v=abc"))
        assertFalse(futaberPlayerUrlIsAllowed("https://youtube.com.evil.example/x"))
        assertFalse(futaberPlayerUrlIsAllowed("https://example.com/youtube.com"))
    }

    @Test fun thePlayerOpensTheThreadsYouTubeAddressOrYouTubesFrontPage() {
        val withLink = listOf(post("1", "見て <a href=\"https://youtu.be/XYZ\">https://youtu.be/XYZ</a>"))
        assertEquals("https://youtu.be/XYZ", futaberPlayerStartUrl(withLink))
        assertEquals(FUTABER_PLAYER_HOME_URL, futaberPlayerStartUrl(listOf(post("1", "nothing"))))
        assertEquals(FUTABER_PLAYER_HOME_URL, futaberPlayerStartUrl(listOf(post("1", "https://example.com/v"))))
    }
}
