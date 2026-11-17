package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.ui.compat.compatPostLineCount
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** L-B8: the writing screen as the original's (docs/futaber-evolution-look-2026-10-31.md §2.8, §4.2 B8). */
class FutaberPostScreenTest {
    // ---- M36 ----

    @Test fun theToolRowIsTheOriginalsSixPlacesThenThisAppsToolsAfterTheLastOne() {
        val row = futaberPostTools(canQuote = true)
        assertEquals(listOf("camera", "kana", "paper", "plane", "clear", "settings", "paste", "quote-mode"), row.map { it.id })
        // The paper plane's place is kept empty until it is captured (M40), so × and the gear stay in the 5th and 6th.
        assertEquals(listOf(false, false, false, true, false, false, false, false), row.map { it.empty })
        assertEquals(6, row.indexOfFirst { it.extra })
        assertTrue(row.drop(6).all { it.extra } && row.take(6).none { it.extra })
        // The tags the earlier screen used stay with the same tools.
        assertEquals("futaber-post-attach", row[0].tag)
        assertEquals("futaber-post-clear", row[4].tag)
        assertEquals("futaber-post-settings", row[5].tag)
        // The extras come from one function (F-B2 can put them behind 「その他の機能」); without the thread on screen no quote mode.
        assertEquals(futaberPostToolExtras(canQuote = true), row.drop(6))
        assertEquals(listOf("paste"), futaberPostToolExtras(canQuote = false).map { it.id })
        assertEquals("ｶﾅ", FUTABER_POST_KANA_LABEL)
    }

    // ---- M37 ----

    // 2026-11-04: the original's 「カメラ」 is taken out (the user's decision); its other three keep their order.
    @Test fun theCameraMenuIsTheOriginalsItemsWithoutTheCameraInTheirOrder() {
        val menu = futaberCameraMenuOriginals(filesRefused = false)
        assertEquals(listOf("最近の画像", "ファイル", "ライブラリ"), menu.map { it.label })
        assertTrue(futaberCameraMenu(filesRefused = false, toolsEnabled = true, canUpload = true).none { it.id == "camera" || it.label == "カメラ" })
        assertTrue(menu.all { it.enabled && !it.extra && !it.refused })
        assertEquals(emptyList(), futaberCameraMenuExtras(filesRefused = false, toolsEnabled = false, canUpload = true))
        assertEquals(listOf("tool-drawing"), futaberCameraMenuExtras(filesRefused = true, toolsEnabled = false, canUpload = true).map { it.id })
    }

    // ---- V17 ----

    @Test fun sendIsAFrameUntilThereIsSomethingToSend() {
        assertEquals(FutaberPostSendStyle.Outline, futaberPostSendStyle("", hasAttachment = false, busy = false))
        assertEquals(FutaberPostSendStyle.Filled, futaberPostSendStyle("a", hasAttachment = false, busy = false))
        assertEquals(FutaberPostSendStyle.Filled, futaberPostSendStyle("", hasAttachment = true, busy = false))
        assertEquals(FutaberPostSendStyle.Busy, futaberPostSendStyle("a", hasAttachment = true, busy = true))
    }

    // ---- F18 ----

    @Test fun anEmptyTextCountsNoLinesAsTheOriginalWhileTheOtherModesKeepOne() {
        assertEquals("0行 / 0B", futaberPostCounterText(compatPostLineCount("", emptyIsOneLine = false), 0))
        assertEquals("2行 / 5B", futaberPostCounterText(compatPostLineCount("ab\ncd", emptyIsOneLine = false), 5))
        // The shared count's default (ふたちゃ・としあき(仮)・ふたったー風) is unchanged.
        assertEquals(1, compatPostLineCount(""))
    }

    // ---- M42 ----

    @Test fun clearRemembersWhatItClearedAndTheRestoreKeepsWhatWasTypedSince() {
        assertNull(futaberRememberCleared("", "", null))
        val first = futaberRememberCleared("題", "本文", null)
        assertEquals(FutaberClearedText("題", "本文"), first)
        // Clearing an empty screen keeps the earlier text.
        assertEquals(first, futaberRememberCleared("", "", first))
        val (back, next) = futaberRestoreCleared("", "", first!!)
        assertEquals(first, back)
        assertNull(next)
        // Typed since: it is not lost, a second restore brings it back.
        val (again, swapped) = futaberRestoreCleared("", "新しい", first)
        assertEquals("本文", again.comment)
        assertEquals(FutaberClearedText("", "新しい"), swapped)
    }

    // ---- G12 ----

    @Test fun onlyAPullStartedAtTheTopAndNotScrolledByTheTextGoesIntoQuoteMode() {
        val pull = FutaberQuotePull(thresholdPx = 100f)
        pull.onDown(atTop = true, selecting = false)
        assertFalse(pull.onLeftover(60f))
        assertTrue(pull.onLeftover(60f))
        assertFalse(pull.onLeftover(60f), "once per drag")
        // A long text scrolled down: the drag scrolls it back first, and never pulls.
        pull.onDown(atTop = false, selecting = false)
        assertFalse(pull.onLeftover(500f))
        // While text is selected the drag is the selection's.
        pull.onDown(atTop = true, selecting = true)
        assertFalse(pull.onLeftover(500f))
        // Going back up starts the pull again.
        pull.onDown(atTop = true, selecting = false)
        assertFalse(pull.onLeftover(90f))
        pull.onScrolledBack()
        assertFalse(pull.onLeftover(90f))
        assertTrue(pull.onLeftover(20f))
        // The same read from the finger for a short text: down far enough, mostly vertical, the text still at its top.
        pull.onDown(atTop = true, selecting = false)
        assertFalse(pull.onFingerMoved(dx = 0f, dy = 80f, atTop = true))
        assertTrue(pull.onFingerMoved(dx = 10f, dy = 120f, atTop = true))
        assertFalse(pull.onLeftover(500f), "once per drag, whichever way it was read")
        pull.onDown(atTop = true, selecting = false)
        assertFalse(pull.onFingerMoved(dx = 150f, dy = 120f, atTop = true), "a sideways drag is a swipe, not a pull")
        pull.onDown(atTop = true, selecting = false)
        assertFalse(pull.onFingerMoved(dx = 0f, dy = 50f, atTop = false), "the text scrolled away from its top")
        assertFalse(pull.onFingerMoved(dx = 0f, dy = 500f, atTop = true))
    }

    // ---- G29 ----

    @Test fun aRightSwipeFromTheMiddleOrTheLeftEdgeClosesTheWritingButNotWhileSendingOrSelecting() {
        assertTrue(futaberPostSwipeCloses(FutaberSwipe.Right, sending = false, selecting = false))
        assertTrue(futaberPostSwipeCloses(FutaberSwipe.EdgeRight, sending = false, selecting = false))
        assertFalse(futaberPostSwipeCloses(FutaberSwipe.Left, sending = false, selecting = false))
        assertFalse(futaberPostSwipeCloses(FutaberSwipe.Right, sending = true, selecting = false))
        assertFalse(futaberPostSwipeCloses(FutaberSwipe.Right, sending = false, selecting = true))
        // The decision of the swipe itself is the thread's (FutaberSwipeTest): a mostly vertical drag is none.
        assertNull(futaberSwipeDecision(startX = 200f, dx = 80f, dy = 80f, edgePx = 20f, triggerPx = 56f))
        assertEquals(FutaberSwipe.Right, futaberSwipeDecision(startX = 200f, dx = 80f, dy = 10f, edgePx = 20f, triggerPx = 56f))
    }

    // ---- S16・C43・C44 ----

    @Test fun thePostSettingsHaveTheOriginalsSectionsAndTheDeviceInfoOnlyFromTheWritingScreen() {
        assertEquals(
            listOf(FutaberPostSettingsSection.Writing, FutaberPostSettingsSection.ImageQuality, FutaberPostSettingsSection.Other, FutaberPostSettingsSection.NameAndMail),
            futaberPostSettingsSections(fromWritingScreen = true)
        )
        assertEquals(
            listOf(FutaberPostSettingsSection.Writing, FutaberPostSettingsSection.ImageQuality, FutaberPostSettingsSection.NameAndMail),
            futaberPostSettingsSections(fromWritingScreen = false)
        )
    }

    @Test fun theImageQualityIsHighByDefaultAndJpegRedrawsOnlyPictures() {
        assertEquals(FutaberImageQuality.High, FutaberPostSettings.from(emptyMap()).imageQuality)
        assertEquals(FutaberImageQuality.High, FutaberPostSettings.from(mapOf(FutaberPreferenceKeys.POST_IMAGE_QUALITY to "broken")).imageQuality)
        assertEquals(FutaberImageQuality.Jpeg, FutaberPostSettings.from(mapOf(FutaberPreferenceKeys.POST_IMAGE_QUALITY to "JPEG")).imageQuality)
        assertEquals(listOf("高品質", "JPG圧縮"), FutaberImageQuality.entries.map { it.label })
        assertTrue(futaberRedrawsAsJpeg(FutaberImageQuality.Jpeg, isHandwriting = false, isVideo = false))
        assertFalse(futaberRedrawsAsJpeg(FutaberImageQuality.High, isHandwriting = false, isVideo = false))
        assertFalse(futaberRedrawsAsJpeg(FutaberImageQuality.Jpeg, isHandwriting = true, isVideo = false), "a drawing goes as the board's own field")
        assertFalse(futaberRedrawsAsJpeg(FutaberImageQuality.Jpeg, isHandwriting = false, isVideo = true))
    }

    @Test fun theDeviceLineIsTheOneTheToolsInserted() {
        assertEquals("ふたちゃ 13.5 Google/Pixel/15", futaberDeviceInfoText("ふたば＠アプリ としあき(仮) 13.5 Google/Pixel/15"))
    }

    // ---- F17 ----

    @Test fun theSubjectFieldOfANewThreadIsBehindAnOffSwitch() {
        assertFalse(FutaberDisplaySettings.from(emptyMap()).legacySubjectField)
        assertTrue(FutaberDisplaySettings.from(mapOf(FutaberSettingKeys.LEGACY_SUBJECT_FIELD to "ON")).legacySubjectField)
        assertEquals("compat.futaber.legacySubjectField", FutaberSettingKeys.LEGACY_SUBJECT_FIELD)
    }

    // ---- M39 ----

    @Test fun foldersKeepTheirRowsAndTheTopListsFoldersFirst() {
        var items = futaberPasteboardWithText(emptyList(), "A", "a")
        items = futaberPasteboardWithText(items, "B", "b")
        items = futaberPasteboardWithFolder(items, "定型", "f")
        assertEquals(items, futaberPasteboardWithFolder(items, "   ", "g"), "a blank name makes nothing")
        items = futaberPasteboardMoved(items, setOf("a", "f"), "f")
        // A folder is not put in a folder; the row went in.
        assertEquals(listOf("f", "b"), futaberPasteboardLevel(items, null).map { it.id })
        assertEquals(listOf("a"), futaberPasteboardLevel(items, "f").map { it.id })
        // A text kept while the folder is open goes in it.
        items = futaberPasteboardWithText(items, "C", "c", folder = "f")
        assertEquals(listOf("c", "a"), futaberPasteboardLevel(items, "f").map { it.id })
        // Back to no folder; a folder that does not exist is no folder.
        assertNull(futaberPasteboardMoved(items, setOf("c"), "nope").first { it.id == "c" }.folder)
        // Removing a folder removes its rows.
        assertEquals(listOf("b"), futaberPasteboardWithout(items, "f").map { it.id })
        assertEquals(listOf("c", "f", "a"), futaberPasteboardWithoutAll(items, setOf("b")).map { it.id })
    }

    @Test fun theGripReordersOneLevelAndLeavesTheOthersInPlace() {
        val items = listOf(
            FutaberPasteItem("f", folderName = "定型"),
            FutaberPasteItem("x", text = "X", folder = "f"),
            FutaberPasteItem("a", text = "A"),
            FutaberPasteItem("b", text = "B"),
            FutaberPasteItem("c", text = "C")
        )
        val top = futaberPasteboardLevel(items, null).map { it.id }
        assertEquals(listOf("f", "a", "b", "c"), top)
        val moved = futaberPasteboardMovedWithin(top, from = 3, to = 1)
        assertEquals(listOf("f", "c", "a", "b"), moved)
        val reordered = futaberPasteboardReordered(items, moved)
        assertEquals(listOf("f", "x", "c", "a", "b"), reordered.map { it.id })
        assertEquals(moved, futaberPasteboardLevel(reordered, null).map { it.id })
        assertEquals(top, futaberPasteboardMovedWithin(top, from = 0, to = 9))
    }

    @Serializable
    private data class PasteItemBeforeLB8(val id: String, val text: String = "", val imageLocator: String? = null, val imageName: String = "")

    @Test fun theFolderFieldIsOptionalBothWays() {
        val oldJson = Json { ignoreUnknownKeys = true }
        // A list written before L-B8 reads with no folders.
        val before = oldJson.encodeToString(ListSerializer(PasteItemBeforeLB8.serializer()), listOf(PasteItemBeforeLB8("a", text = "定型")))
        val read = decodeFutaberPasteboard(before)
        assertEquals(listOf(FutaberPasteItem("a", text = "定型")), read)
        assertNull(read.single().folder)
        // A list written now reads in a version before L-B8: the rows show (without a folder), the folder row is dropped by
        // its own filter (no text, no picture).
        val now = listOf(FutaberPasteItem("f", folderName = "定型"), FutaberPasteItem("a", text = "A", folder = "f"), FutaberPasteItem("b", text = "B"))
        val (encoded, _) = encodeFutaberPasteboard(now)
        assertEquals(now, decodeFutaberPasteboard(encoded))
        val inOldVersion = oldJson.decodeFromString(ListSerializer(PasteItemBeforeLB8.serializer()), encoded)
            .filter { it.text.isNotEmpty() || it.imageLocator != null }
        assertEquals(listOf("a", "b"), inOldVersion.map { it.id })
        // A row naming a folder that is gone shows without one.
        val orphan = encodeFutaberPasteboard(listOf(FutaberPasteItem("a", text = "A", folder = "gone"))).first
        assertNull(decodeFutaberPasteboard(orphan).single().folder)
    }
}
