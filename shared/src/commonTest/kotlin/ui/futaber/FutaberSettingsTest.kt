package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.CompatWatchRule
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.model.BoardSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** L-B9: futaber's settings (look plan §2.9, C47・C25・C21・C23・C46・C45・C37・C33・C11・C12) and this app's rows after them. */
class FutaberSettingsTest {
    private val everything = FutaberSettingsAvailability(fileManager = true)

    @Test
    fun theTopHasFutabersGroupsInItsOrderAndEveryOtherRowAfterTheEula() {
        val top = futaberSettingsTopGroups(everything)
        assertEquals(listOf("全般", "通知", null), top.map { it.title })
        // futaber's 全般 (its 「URLスキーム」 is L-B12's) and 通知; the EULA is the last of futaber's rows.
        assertEquals(
            listOf("theme", "catalog", "thread", "viewer", "url-scheme", "font-size", "posting", "gesture", "ng"),
            top[0].rows.map { it.id }
        )
        assertEquals(listOf("patrol-enabled", "new-thread-notify"), top[1].rows.map { it.id })
        assertEquals(listOf("eula"), top[2].rows.map { it.id })
        assertTrue(top.none { it.extra })
        // This app's own groups come from the one function, after futaber's, in the order they had (look plan §2.9).
        val extras = futaberSettingsTopExtras(everything)
        assertTrue(extras.all { it.extra })
        assertEquals(listOf("データ・通信", "機能", "動作", "このアプリ", "閲覧スレ通知", "情報・サポート"), extras.map { it.title })
        // Every row the settings had before L-B9 is still there (none became unreachable).
        val before = listOf(
            "theme", "catalog", "thread", "viewer", "url-scheme", "font-size", "posting", "gesture", "ng", "patrol-enabled", "notifications",
            "storage", "network", "background", "backup", "cookies", "image-search", "media", "privacy", "ai",
            "update-check", "background-refresh", "watch-alert", "watch-words", "lightweight", "app-lock",
            "extras", "app-icon", "mode", "preferred-file-manager", "archive-report", "help", "changelog", "license", "version"
        )
        val all = (top + extras).flatMap { group -> group.rows.map { it.id } }
        assertEquals(all.toSet().size, all.size, "a row shows once")
        assertTrue(all.containsAll(before), (before - all.toSet()).toString())
        // Nothing of the extras sits before futaber's EULA.
        assertEquals(top.flatMap { it.rows }.size - 1, all.indexOf("eula"))
    }

    @Test
    fun withoutTheSharedServicesOnlyTheRowsThatNeedNothingAreShown() {
        val bare = FutaberSettingsAvailability(services = false, stateStore = false, cookies = false, watchWordsEditor = false)
        val top = futaberSettingsTopGroups(bare)
        assertEquals(listOf("全般", null), top.map { it.title })
        assertFalse(top[0].rows.any { it == FutaberSettingsRow.Viewer })
        assertEquals(listOf("このアプリ"), futaberSettingsTopExtras(bare).map { it.title })
        assertEquals(listOf("extras", "mode"), futaberSettingsTopExtras(bare).single().rows.map { it.id })
        assertFalse(futaberSettingsTopExtras(everything.copy(cookies = false)).flatMap { it.rows }.contains(FutaberSettingsRow.Cookies))
        assertFalse(futaberSettingsTopExtras(everything.copy(archiveReport = false)).any { it.id == "archive-report" })
    }

    @Test
    fun theThreadPageHasFutabersSectionsThenTheReadAloudVoiceAndTheFourLegacySwitches() {
        assertEquals(
            listOf("題名と名前の表示", "画像表示サイズ", "返信", "スレッド読み上げ", "画面をタップしてスクロール", "その他"),
            futaberThreadSettingsSections().map { it.title }
        )
        assertEquals(
            listOf(FutaberThreadSettingsExtra.SpeechVoice, FutaberThreadSettingsExtra.DeletedPosts, FutaberThreadSettingsExtra.PostLayout, FutaberThreadSettingsExtra.Legacy),
            futaberThreadSettingsExtras(services = true)
        )
        assertEquals(listOf(FutaberThreadSettingsExtra.PostLayout, FutaberThreadSettingsExtra.Legacy), futaberThreadSettingsExtras(services = false))
        // The four switches keep their keys, names and the off default.
        assertEquals(
            listOf(
                "compat.futaber.instantCopy", "compat.futaber.legacySearchFilter",
                "compat.futaber.legacyManyRepliesFilter", "compat.futaber.legacySubjectField"
            ),
            futaberLegacySwitches.map { it.key }
        )
        assertEquals(
            listOf(FUTABER_INSTANT_COPY_SWITCH, FUTABER_LEGACY_SEARCH_SWITCH, FUTABER_LEGACY_MANY_REPLIES_SWITCH, FUTABER_LEGACY_SUBJECT_SWITCH),
            futaberLegacySwitches.map { it.label }
        )
        val none = FutaberDisplaySettings.from(emptyMap())
        assertTrue(futaberLegacySwitches.none { none.legacySwitchOn(it) })
        val all = FutaberDisplaySettings.from(futaberLegacySwitches.associate { it.key to "ON" })
        assertTrue(futaberLegacySwitches.all { all.legacySwitchOn(it) })
        // C25: tap-scroll keeps its key and its on default (G20); an explicit OFF stays off.
        assertTrue(none.tapScroll)
        assertFalse(FutaberDisplaySettings.from(mapOf(FutaberSettingKeys.TAP_SCROLL to "OFF")).tapScroll)
        assertEquals("compat.futaber.tapScroll", FutaberSettingKeys.TAP_SCROLL)
        // C26: futaber's switch and note.
        assertEquals("画像長押しメニューにGoogleLens検索を表示", FUTABER_GOOGLE_LENS_SWITCH)
        assertFalse(none.googleLens)
    }

    @Test
    fun theImageRatioIsSeventyAtFirstFiftyForTheOldSmallAndFeedsTheAttachmentSize() {
        assertEquals(70, futaberImageRatioPercent(emptyMap()))
        // The old 「小さく表示」 reads as 50% until a ratio is stored.
        assertEquals(50, futaberImageRatioPercent(mapOf(FutaberSettingKeys.SMALL_IMAGES to "ON")))
        assertEquals(80, futaberImageRatioPercent(mapOf(FutaberSettingKeys.SMALL_IMAGES to "ON", FutaberSettingKeys.IMAGE_RATIO to "80")))
        assertEquals(70, futaberImageRatioPercent(mapOf(FutaberSettingKeys.IMAGE_RATIO to "5")))
        assertEquals(70, futaberImageRatioPercent(mapOf(FutaberSettingKeys.IMAGE_RATIO to "x")))
        assertEquals(10, futaberImageRatioPercent(mapOf(FutaberSettingKeys.IMAGE_RATIO to "10")))
        assertEquals(100, futaberImageRatioPercent(mapOf(FutaberSettingKeys.IMAGE_RATIO to "100")))
        assertEquals(60, futaberImageRatioStep(70, -1))
        assertEquals(10, futaberImageRatioStep(10, -1))
        assertEquals(100, futaberImageRatioStep(100, 1))
        val legacy = FutaberDisplaySettings.from(mapOf(FutaberSettingKeys.SMALL_IMAGES to "ON"))
        assertTrue(legacy.smallImages)
        assertEquals(50, legacy.imageRatioPercent)
        // 「オリジナルサイズで表示」 is futaber's 0.7 of the thumbnail; the ratio is of that.
        assertEquals(FUTABER_ATTACHMENT_RATIO, futaberSettingsImageRatio(false, 50))
        assertEquals(0.35f, futaberSettingsImageRatio(true, 50), 0.0001f)
        assertEquals(0.49f, futaberSettingsImageRatio(true, 70), 0.0001f)
        val size = futaberAttachmentSize(250, 188, futaberAttachmentRatio(false, futaberSettingsImageRatio(true, 50)))
        assertEquals(87.5f, size.width, 0.01f)
        // The operation menu's 「画像を小さくする」 is 0.3 of whatever the settings give.
        assertEquals(0.105f, futaberAttachmentRatio(true, futaberSettingsImageRatio(true, 50)), 0.0001f)
    }

    @Test
    fun theReadAloudStepsMapOntoTheSharedPitchAndRateBothWays() {
        // Nothing stored: the shared normal 1.0, futaber's 1.0 pitch and 0.5 speed (iOS's normal rate).
        val none = FutaberDisplaySettings.from(emptyMap())
        assertEquals(10, none.speechPitchTenths)
        assertEquals(10, none.speechRateTenths)
        assertEquals("1.0", futaberSpeechPitchShown(10))
        assertEquals("0.5", futaberSpeechSpeedShown(10))
        assertEquals("0.6", futaberSpeechSpeedShown(12))
        assertEquals("0.55", futaberSpeechSpeedShown(11))
        assertEquals("1.0", futaberSpeechSpeedShown(20))
        assertEquals("0.25", futaberSpeechSpeedShown(5))
        // Every shared value the shared page's slider can store reads here and is written back unchanged.
        for (tenths in 5..20) {
            val stored = "${tenths / 10}.${tenths % 10}"
            assertEquals(tenths, futaberSharedSpeechTenths(stored))
            if (tenths < 20) assertEquals(stored, futaberSharedSpeechStep(futaberSharedSpeechTenths(futaberSharedSpeechStep(tenths, 1)), -1))
            assertEquals(tenths / 10f, futaberSharedRateForSpeed(futaberSpeechSpeedShown(tenths).toFloat()), 0.0001f)
            assertEquals(tenths / 10f, futaberSpeechPitchShown(tenths).toFloat(), 0.0001f)
        }
        // The slider's own floats ("1.1000001") read as the nearest tenth; a step stores a plain tenth the others read.
        assertEquals(11, futaberSharedSpeechTenths("1.1000001"))
        assertEquals("1.2", futaberSharedSpeechStep(11, 1))
        assertEquals(1.2f, com.valoser.futacha.shared.audio.speechSettingsFromPreferences(mapOf("compat.speech.rate" to "1.2")).rate)
        // The ends hold; what cannot be read is the normal value.
        assertEquals("0.5", futaberSharedSpeechStep(5, -1))
        assertEquals("2.0", futaberSharedSpeechStep(20, 1))
        assertFalse(futaberSharedSpeechCanStep(5, -1))
        assertFalse(futaberSharedSpeechCanStep(20, 1))
        assertEquals(10, futaberSharedSpeechTenths("9"))
        assertEquals(10, futaberSharedSpeechTenths(null))
        assertEquals(listOf("compat.speech.pitch", "compat.speech.rate"), futaberSpeechKeys)
        val read = FutaberDisplaySettings.from(mapOf("compat.speech.pitch" to "1.3", "compat.speech.rate" to "0.8"))
        assertEquals(13, read.speechPitchTenths)
        assertEquals(8, read.speechRateTenths)
    }

    @Test
    fun theGesturePageHasFutabersFourLinesFirstWrittenForThisApp() {
        assertEquals(
            listOf(
                "・スレッド内で左端からスワイプで板一覧を表示",
                "・スレッド内でレス長押しで引用その他アクション",
                "・スレッド内ツールバーの最下部スクロールボタンを長押しで新着へスクロール",
                "・書き込み画面で下スワイプで引用モード"
            ),
            futaberGestureNoteLines
        )
        val note = futaberGestureNote().lines()
        assertEquals("他", note.first())
        assertEquals(futaberGestureNoteLines, note.subList(1, 5))
        assertTrue(note.drop(5).any { "画面をタップしてスクロール" in it })
        // Not futaber's 「直接メニュー表示」: the left edge opens the board list here.
        assertFalse(note.any { "直接メニュー" in it })
    }

    @Test
    fun theNgPageHasFutabersRowsInItsOrderThenThisAppsLists() {
        assertEquals(listOf("NGワード", "NGスレッドURL"), futaberNgCatalogRows().map { it.row })
        assertEquals(listOf("NGワード（詳細）", "NG画像（カタログ）"), futaberNgCatalogExtras().map { it.row })
        assertEquals(listOf("NGワード", "NG画像", "NGネーム", "NGID/IP"), futaberNgResRows().map { it.row })
        assertEquals(emptyList(), futaberNgResExtras(false))
        assertEquals(listOf(FutaberNgListKind.ThreadDetails), futaberNgResExtras(true))
        assertEquals(listOf("カタログNGワード", "カタログNGスレッドURL"), futaberNgCatalogRows().map { it.title })
        assertEquals(listOf("レスNGワード", "レスNG画像", "レスNGネーム", "レスNGID/IP"), futaberNgResRows().map { it.title })
    }

    @Test
    fun theNamesAndTheIdsAreOneSharedListShownApart() {
        assertTrue(futaberNgHeaderIsIdIp("ID:abc12345"))
        assertTrue(futaberNgHeaderIsIdIp(" ip:106.73.*(enabler.ne.jp)"))
        assertFalse(futaberNgHeaderIsIdIp("としあき"))
        assertFalse(futaberNgHeaderIsIdIp("IDOL"))
        assertEquals("ID:abc12345", futaberNgIdIpEntry("abc12345"))
        assertEquals("IP:106.73.*", futaberNgIdIpEntry("106.73.*"))
        assertEquals("ID:Xy3", futaberNgIdIpEntry("id:Xy3"))
        assertEquals("IP:106.73.*(enabler.ne.jp)", futaberNgIdIpEntry("IP:106.73.*(enabler.ne.jp)"))
        assertEquals("", futaberNgIdIpEntry("  "))
    }

    @Test
    fun anNgThreadUrlMustBeAThreadOfARegisteredOfficialBoard() {
        val may = BoardSummary("may-b", "may", "may", "https://may.2chan.net/b/futaba.php", "may")
        val key = compatBoardKey("https://may.2chan.net/b/")
        val expected = FutaberNgThreadTarget(key, "https://may.2chan.net/b/res/123.htm")
        assertEquals(expected, futaberNgThreadTarget("https://may.2chan.net/b/res/123.htm", listOf(may)))
        assertEquals(expected, futaberNgThreadTarget(" http://may.2chan.net/b/res/123.htm ", listOf(may)))
        assertEquals(expected, futaberNgThreadTarget("may.2chan.net/b/res/123.htm", listOf(may)))
        // Not a thread, another board, not an official board.
        assertNull(futaberNgThreadTarget("https://may.2chan.net/b/futaba.php", listOf(may)))
        assertNull(futaberNgThreadTarget("https://img.2chan.net/b/res/123.htm", listOf(may)))
        assertNull(futaberNgThreadTarget("https://example.com/b/res/123.htm", listOf(may)))
        assertNull(futaberNgThreadTarget("", listOf(may)))
    }

    @Test
    fun theNewThreadNotifyPageHasEveryBoardFirstThenEachBoardWithItsKeywords() {
        val boards = listOf(
            CompatBoard("k2", "img", "https://img.2chan.net/b/", "https://img.2chan.net/b/futaba.php", 1),
            CompatBoard("k1", "may", "https://may.2chan.net/b/", "https://may.2chan.net/b/futaba.php", 0)
        )
        val rules = listOf(
            CompatWatchRule("ねこ"), CompatWatchRule("いぬ", "k1"), CompatWatchRule("とり", "k1", enabled = false),
            CompatWatchRule("うま", "gone")
        )
        val sections = futaberWatchSections(rules, boards)
        assertEquals(listOf(null, "k1", "k2", "gone"), sections.map { it.boardKey })
        assertEquals(FUTABER_WATCH_ALL_BOARDS, sections[0].title)
        assertEquals("may https://may.2chan.net/b/futaba.php", sections[1].title)
        assertEquals(listOf("ねこ"), sections[0].words.map { it.word })
        assertEquals(listOf("いぬ" to true, "とり" to false), sections[1].words.map { it.word to it.enabled })
        assertEquals(listOf("うま"), sections[3].words.map { it.word })
        // Adding goes to the board's section; the same word for the same board is not added twice; there is no limit of
        // three boards (look plan §1.6).
        val added = futaberWatchAdd(rules, "k2", " うし ")
        assertEquals(CompatWatchRule("うし", "k2"), added.last())
        assertEquals(added, futaberWatchAdd(added, "k2", "うし"))
        assertEquals(rules, futaberWatchAdd(rules, null, "  "))
        assertFalse(futaberNewThreadNotifyNoteLines.any { "３つまで" in it || "3つまで" in it })
    }

    @Test
    fun theEulaIsThisAppsOwnTerms() {
        assertEquals("利用規約（EULA）", FUTABER_EULA_TITLE)
        assertEquals(4, FUTABER_EULA_SECTIONS.size)
        // Not futaber's terms.
        assertFalse((FUTABER_EULA_INTRO + FUTABER_EULA_SECTIONS.joinToString()).contains("futaber"))
    }
}
