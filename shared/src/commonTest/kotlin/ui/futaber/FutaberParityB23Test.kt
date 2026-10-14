package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.network.defaultBoardPostingCapabilities
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import com.valoser.futacha.shared.ui.compat.appendCompatPostText
import com.valoser.futacha.shared.ui.compat.persistCompatPostAttachment
import com.valoser.futacha.shared.util.ImageData
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The B2 (posting aids) and B3 (key normalisation, draft attachments) parity batches of the ふたばー風 mode (pure parts). */
class FutaberParityB23Test {
    private val may = BoardSummary("b1", "may/b", "", "https://may.2chan.net/b/futaba.php", "")
    private val img = BoardSummary("b", "img/b", "", "https://img.2chan.net/b/futaba.php", "")
    private val tutorial = BoardSummary("t", "チュートリアル", "", "https://www.example.com/t/futaba.php", "")
    private val boards = listOf(img, may, tutorial)
    private val mayKey = futaberCompatBoardKey(may)

    private fun history(boardId: String, threadId: String) = ThreadHistoryEntry(
        threadId = threadId, boardId = boardId, title = "題$threadId", titleImageUrl = "", boardName = "",
        boardUrl = "", lastVisitedEpochMillis = 0L, replyCount = 3
    )

    // ---- settings ----

    @Test fun theNewExtensionsAreOffByDefault() {
        val defaults = FutaberDisplaySettings.from(emptyMap())
        assertFalse(defaults.extPostTools)
        assertFalse(defaults.extDraftAttachment)
        val on = FutaberDisplaySettings.from(
            mapOf(FutaberSettingKeys.EXT_POST_TOOLS to "ON", FutaberSettingKeys.EXT_DRAFT_ATTACHMENT to "ON")
        )
        assertTrue(on.extPostTools && on.extDraftAttachment)
        assertFalse(FutaberDisplaySettings.from(mapOf(FutaberSettingKeys.EXT_POST_TOOLS to "yes")).extPostTools)
    }

    // ---- P10 / P11 ----

    @Test fun anImgReplyTapOffersTheDrawingAndOtherwiseTheTapPicksAFile() {
        val imgCaps = defaultBoardPostingCapabilities(img.url)
        assertFalse(imgCaps.replyAttachmentsAllowed)
        assertEquals(FutaberAttachTap.OpenToolSheet, futaberAttachTap(isReply = true, replyAttachmentsAllowed = imgCaps.replyAttachmentsAllowed))
        // A new thread on img and any post elsewhere pick a file as before.
        assertEquals(FutaberAttachTap.PickFile, futaberAttachTap(isReply = false, replyAttachmentsAllowed = false))
        assertEquals(FutaberAttachTap.PickFile, futaberAttachTap(isReply = true, replyAttachmentsAllowed = true))
    }

    @Test fun theToolSheetOffersTheDrawingAlwaysAndTheOtherToolsOnlyWithTheExtension() {
        val onlyDrawing = futaberPostToolEntries(handwritingOnly = true, allTools = false, canUpload = true)
        assertEquals(listOf("pick", "drawing"), onlyDrawing.map { it.id })
        assertFalse(onlyDrawing.first { it.id == "pick" }.enabled)
        assertTrue(onlyDrawing.first { it.id == "pick" }.label.contains("添付できません"))
        assertTrue(onlyDrawing.first { it.id == "drawing" }.enabled)
        val all = futaberPostToolEntries(handwritingOnly = false, allTools = true, canUpload = false)
        assertEquals(listOf("pick", "drawing", "ups", "voice", "network-info", "device-info"), all.map { it.id })
        assertTrue(all.first { it.id == "pick" }.enabled)
        // Without the network there is nothing to upload to.
        assertFalse(all.first { it.id == "ups" }.enabled)
        assertTrue(futaberPostToolSheetTitle(true).contains("手書き"))
    }

    @Test fun theRefusalOfAnImgReplyNamesTheWayToTheDrawing() {
        val refused = futaberAttachmentVerdict(ImageData(ByteArray(4), "a.png"), defaultBoardPostingCapabilities(img.url), isReply = true)
        val message = assertIs<FutaberAttachmentVerdict.Reject>(refused).message
        assertTrue(message.contains("添付ボタン") && message.contains("手書き"), message)
        // A drawing passes on such a board (it is sent as the board's own hand-drawing field).
        assertEquals(
            FutaberAttachmentVerdict.Accept,
            futaberAttachmentVerdict(ImageData(ByteArray(4), "drawing.png", isHandwriting = true), defaultBoardPostingCapabilities(img.url), isReply = true)
        )
    }

    @Test fun insertedLinesGoOnTheirOwnLineAndTheDeviceLineNamesThisApp() {
        assertEquals("本文\n回線情報: x", appendCompatPostText("本文", "回線情報: x"))
        assertEquals("本文\n回線情報: x", appendCompatPostText("本文\n", "回線情報: x\n"))
        assertEquals("", appendCompatPostText("", "  "))
        assertEquals("ふたちゃ 12.8\nGoogle Pixel", futaberDeviceInfoText("ふたば＠アプリ としあき(仮) 12.8\nGoogle Pixel"))
        assertEquals("Futacha/12.8", futaberNetworkInfoUserAgent("12.8"))
    }

    @Test fun theCookieGuidanceIsOfferedOnlyForACookieFailureEvenWhenItIsWrapped() {
        assertFalse(futaberOffersCookieGuidance(null))
        assertFalse(futaberOffersCookieGuidance(IllegalStateException("本文がありません")))
        // The shared classifier reads a reason naming the posting Cookies as "the Cookies need a look".
        val cookieDetail = "posttimeが確認できません"
        assertTrue(futaberOffersCookieGuidance(IllegalStateException(cookieDetail)))
        // The write screen gets the reason wrapped (返信の送信に失敗しました …) with the server's error under it.
        assertTrue(futaberOffersCookieGuidance(IllegalStateException("返信の送信に失敗しました", IllegalStateException(cookieDetail))))
        assertFalse(futaberOffersCookieGuidance(IllegalStateException("返信の送信に失敗しました", IllegalStateException("本文が長すぎます"))))
    }

    @Test fun theDrawingTopBarIsReadableWithItsWhiteTextInEveryTheme() {
        listOf(FutaberTokens.light, FutaberTokens.dark, FutaberTokens.oldDark, FutaberTokens.futaba).forEach { colors ->
            val chrome = futaberWhiteTextChrome(colors)
            assertTrue(futaberContrastWithWhite(chrome) >= 4.5f, "$chrome")
            assertEquals(chrome, futaberDrawingPalette(colors).chrome)
        }
    }

    // ---- M3: the board part of the keys ----

    @Test fun anOfficialBoardIsWrittenByItsSharedKeyAndATutorialBoardByItsId() {
        assertEquals(mayKey, futaberStoredBoardId(may))
        assertTrue(mayKey.startsWith("compat_board_"))
        assertEquals("t", futaberStoredBoardId(tutorial))
    }

    @Test fun oldTabsAreReadKeptByTheCleanUpAndWrittenInTheNewForm() {
        // An older value: board ids of the board list.
        val old = encodeFutaberTabs(listOf(FutaberTabKey("b1", "100"), FutaberTabKey("t", "5")))
        val read = futaberTabsFromStore(old, boards)
        assertEquals(listOf(FutaberTabKey("b1", "100"), FutaberTabKey("t", "5")), read)
        // The clean-up (a removed board's tabs only) keeps them: the old key names a registered board.
        assertEquals(read, futaberPruneTabsOfRemovedBoards(read, boards))
        // The tab shows its history row.
        assertEquals(listOf("題100"), futaberTabViews(read, listOf(history("b1", "100"))).map { it.title })
        // Written back: the official board by its shared key, the tutorial board as before.
        val written = futaberTabsToStore(read, boards)
        assertEquals(listOf(FutaberTabKey(mayKey, "100"), FutaberTabKey("t", "5")), decodeFutaberTabs(written))
        // And read again (as on another device where may is "b7"): the same tabs.
        val elsewhere = listOf(may.copy(id = "b7"), tutorial)
        val moved = futaberTabsFromStore(written, elsewhere)
        assertEquals(listOf(FutaberTabKey("b7", "100"), FutaberTabKey("t", "5")), moved)
        assertEquals(moved, futaberPruneTabsOfRemovedBoards(moved, elsewhere))
        assertEquals(listOf("題100"), futaberTabViews(moved, listOf(history("b7", "100"))).map { it.title })
    }

    @Test fun aTabOfARemovedBoardStillGoesInEitherForm() {
        val stored = encodeFutaberTabs(listOf(FutaberTabKey(mayKey, "1"), FutaberTabKey("b1", "2"), FutaberTabKey("t", "3")))
        val withoutMay = listOf(img, tutorial)
        val read = futaberTabsFromStore(stored, withoutMay)
        assertEquals(listOf(FutaberTabKey("t", "3")), futaberPruneTabsOfRemovedBoards(read, withoutMay))
        // No board list yet: nothing is dropped.
        assertEquals(read, futaberPruneTabsOfRemovedBoards(read, emptyList()))
    }

    @Test fun anOldAndANewKeyOfTheSameTabAreOneTab() {
        val stored = encodeFutaberTabs(listOf(FutaberTabKey("b1", "1"), FutaberTabKey(mayKey, "1")))
        assertEquals(listOf(FutaberTabKey("b1", "1")), futaberTabsFromStore(stored, boards))
    }

    @Test fun favouritesMoveToTheNewFormAndSurviveTheBoardCleanUp() {
        val fav = FutaberFavorite("b1", "100", "題", "", "may/b", 3, 1L)
        val oldStored = encodeFutaberFavorites(listOf(fav))
        val read = futaberFavoritesFromStore(oldStored, boards)
        assertEquals(listOf(fav), read)
        assertEquals(read, futaberPruneFavorites(read, boards))
        val written = futaberFavoritesToStore(read, boards)
        assertEquals(mayKey, decodeFutaberFavorites(written).single().boardId)
        val elsewhere = listOf(may.copy(id = "b7"))
        val moved = futaberFavoritesFromStore(written, elsewhere)
        assertEquals("b7", moved.single().boardId)
        assertEquals(moved, futaberPruneFavorites(moved, elsewhere))
        assertEquals(5, futaberFavoriteViews(moved, listOf(history("b7", "100").copy(replyCount = 5))).single().replyCount)
    }

    @Test fun seenCountsAreReadInEitherFormAndWrittenInTheNewOne() {
        val old = encodeFutaberSeenCounts(listOf(FutaberSeenCount("b1", "100", 7)))
        assertEquals(7, futaberSeenCountForBoard(old, may, "100"))
        assertNull(futaberSeenCountForBoard(old, img, "100"))
        val next = assertNotNull(futaberSeenCountsAfterForBoard(old, may, "100", 9))
        assertEquals(listOf(FutaberSeenCount(mayKey, "100", 9)), decodeFutaberSeenCounts(next))
        assertEquals(9, futaberSeenCountForBoard(next, may.copy(id = "b7"), "100"))
        // Nothing to write when it already says so in the new form.
        assertNull(futaberSeenCountsAfterForBoard(next, may, "100", 9))
        // The tutorial board keeps its id.
        val tut = assertNotNull(futaberSeenCountsAfterForBoard(null, tutorial, "5", 2))
        assertEquals("t", decodeFutaberSeenCounts(tut).single().boardId)
        assertNull(futaberSeenCountsAfterForBoard(old, may, "100", 0))
    }

    @Test fun draftsAreFoundByEitherKeyAndMoveToTheNewOne() {
        val reply = FutaberPostTarget.Reply("b1", "100", "題")
        assertEquals("r:$mayKey:100", futaberDraftStoreKey(reply, boards))
        assertEquals("c:t", futaberDraftStoreKey(FutaberPostTarget.CreateThread("t"), boards))
        val old = listOf(FutaberDraft(reply.draftKey, "", "古い本文", 1L))
        assertEquals("古い本文", futaberDraftForTarget(old, reply, boards)?.comment)
        val next = futaberDraftsWithText(old, reply, boards, "", "新しい本文", 2L)
        assertEquals(listOf("r:$mayKey:100"), next.map { it.key })
        assertEquals("新しい本文", futaberDraftForTarget(next, reply.copy(boardId = "b1"), boards)?.comment)
        // On another device the same draft is found.
        assertEquals("新しい本文", futaberDraftForTarget(next, FutaberPostTarget.Reply("b7", "100", "題"), listOf(may.copy(id = "b7")))?.comment)
        assertTrue(futaberDiscardDraft(next + old, reply, boards).isEmpty())
    }

    // ---- P07: the draft's attachment ----

    @Test fun aDraftWithoutAFileIsStoredAsBeforeAndAnOlderAppReadsANewerOne() {
        val plain = FutaberDraft("r:b:1", "題", "本文", 5L)
        val text = encodeFutaberDrafts(listOf(plain))
        assertFalse(text.contains("attachment"))
        assertEquals(listOf(plain), decodeFutaberDrafts("""[{"key":"r:b:1","subject":"題","comment":"本文","updatedAt":5}]"""))
        // A newer value read by the old shape: the unknown fields are ignored.
        val withFile = encodeFutaberDrafts(listOf(plain.copy(attachmentLocator = "private/compat_post_attachments/x-y/a.png", attachmentIsHandwriting = true)))
        val oldShape = Json { ignoreUnknownKeys = true }
        val fields = oldShape.parseToJsonElement(withFile).jsonArray.single().jsonObject
        assertEquals(setOf("key", "subject", "comment", "updatedAt", "attachmentLocator", "attachmentIsHandwriting"), fields.keys)
        assertEquals("本文", oldShape.decodeFromString(OldDraft.serializer(), fields.toString()).comment)
        assertEquals(true, decodeFutaberDrafts(withFile).single().attachmentIsHandwriting)
        // A draft holding only a file is not empty.
        assertFalse(FutaberDraft("k", attachmentLocator = "x").isEmpty)
        assertTrue(FutaberDraft("k").isEmpty)
    }

    @kotlinx.serialization.Serializable
    private data class OldDraft(val key: String, val subject: String = "", val comment: String = "", val updatedAt: Long = 0L)

    @Test fun onlyTheFilesOfThisModesDraftsAreEverDeleted() = runBlocking {
        val fs = InMemoryFileSystem()
        val mine = persistCompatPostAttachment(fs, FUTABER_DRAFT_ATTACHMENT_OWNER, ImageData(byteArrayOf(1, 2), "a.png")).getOrThrow()
        val toshiaki = persistCompatPostAttachment(fs, "compat_tab_0123", ImageData(byteArrayOf(3), "b.png")).getOrThrow()
        assertTrue(futaberOwnsDraftAttachment(mine))
        assertFalse(futaberOwnsDraftAttachment(toshiaki))
        assertFalse(futaberOwnsDraftAttachment("../../etc/passwd"))
        val before = listOf(FutaberDraft("a", attachmentLocator = mine), FutaberDraft("b", attachmentLocator = toshiaki))
        // Even a dropped note of another mode's file is not a file this mode deletes.
        assertEquals(setOf(mine), futaberDroppedDraftLocators(before, emptyList()))
        coroutineScope { futaberDeleteDraftFiles(this, fs, setOf(mine, toshiaki)) }
        assertFalse(fs.exists(mine))
        assertTrue(fs.exists(toshiaki))
    }

    @Test fun sendingClearingPushingOutAndReplacingDeleteTheFile() = runBlocking {
        val fs = InMemoryFileSystem()
        val reply = FutaberPostTarget.Reply("t", "1", "題")
        var stored: String? = null
        val dropped = mutableSetOf<String>()
        fun edit(transform: (List<FutaberDraft>) -> List<FutaberDraft>) = futaberEditDrafts(
            editPreference = { _, change -> change(stored)?.let { stored = it } },
            onDropped = { dropped += it },
            transform = transform
        )
        // 保存先の名前は「時刻（ミリ秒）＋中身」から作るので、同じ中身を同じミリ秒に2回保存すると同じ名前になる
        // （iOSのネイティブ試験は速く、実際にそうなった）。別のファイルとして扱う所は中身を変える。
        var fileCount = 0
        suspend fun file() = persistCompatPostAttachment(
            fs, FUTABER_DRAFT_ATTACHMENT_OWNER, ImageData(byteArrayOf((++fileCount).toByte()), "a.png")
        ).getOrThrow()
        fun current() = futaberDraftForTarget(decodeFutaberDrafts(stored), reply, boards)

        // A file with the text; the text changes and the file stays noted.
        val first = file()
        edit { futaberDraftsWithText(it, reply, boards, "", "本文", 1L) }
        edit { futaberDraftsWithAttachment(it, reply, boards, first, true, 2L) }
        edit { futaberDraftsWithText(it, reply, boards, "", "本文2", 3L) }
        assertEquals(first, current()?.attachmentLocator)
        assertEquals(true, current()?.attachmentIsHandwriting)
        assertTrue(dropped.isEmpty())
        // Another file replaces it: the old one goes.
        val second = file()
        edit { futaberDraftsWithAttachment(it, reply, boards, second, false, 4L) }
        assertEquals(setOf(first), dropped)
        // Removing the file and the text (クリア) removes the draft and its file.
        edit { futaberDraftsWithAttachment(it, reply, boards, null, false, 5L) }
        edit { futaberDraftsWithText(it, reply, boards, "", "", 6L) }
        assertNull(current())
        assertEquals(setOf(first, second), dropped)
        // A sent post: the draft and its file go.
        val third = file()
        edit { futaberDraftsWithAttachment(it, reply, boards, third, false, 7L) }
        assertEquals("", current()?.comment)
        edit { futaberDiscardDraft(it, reply, boards) }
        assertTrue(third in dropped)
        assertNull(current())
        // Pushed out by newer drafts: its file goes.
        val fourth = file()
        edit { futaberDraftsWithAttachment(it, reply, boards, fourth, false, 8L) }
        (2..(FUTABER_DRAFT_MAX_COUNT + 1)).forEach { n ->
            edit { futaberDraftsWithText(it, FutaberPostTarget.Reply("t", "$n", "題"), boards, "", "本文$n", 10L + n) }
        }
        assertNull(current())
        assertTrue(fourth in dropped)
        // The extension turned off: every file goes, a draft that kept text stays.
        val fifth = file()
        val other = FutaberPostTarget.Reply("t", "3", "題")
        edit { futaberDraftsWithAttachment(it, other, boards, fifth, false, 30L) }
        edit(::futaberDraftsWithoutAttachments)
        assertTrue(fifth in dropped)
        assertEquals("本文3", futaberDraftForTarget(decodeFutaberDrafts(stored), other, boards)?.comment)
        assertNull(futaberDraftForTarget(decodeFutaberDrafts(stored), other, boards)?.attachmentLocator)
        // Nothing changes: nothing is written.
        val before = stored
        edit(::futaberDraftsWithoutAttachments)
        assertEquals(before, stored)
    }

    @Test fun withTheExtensionOffNoFileIsEverNotedByTheText() {
        val reply = FutaberPostTarget.Reply("t", "1", "題")
        val drafts = futaberDraftsWithText(emptyList(), reply, boards, "", "本文", 1L)
        assertNull(drafts.single().attachmentLocator)
        // Setting "no file" on a draft that has none writes nothing.
        assertEquals(drafts, futaberDraftsWithAttachment(drafts, reply, boards, null, false, 2L))
        assertEquals(emptyList(), futaberDraftsWithAttachment(emptyList(), reply, boards, null, false, 2L))
    }
}
