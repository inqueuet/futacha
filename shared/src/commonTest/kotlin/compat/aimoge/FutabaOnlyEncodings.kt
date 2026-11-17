package com.valoser.futacha.shared.compat.aimoge

import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.CompatCatalogPreference
import com.valoser.futacha.shared.compat.CompatHistoryEntry
import com.valoser.futacha.shared.compat.CompatNgKind
import com.valoser.futacha.shared.compat.CompatNgRule
import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.compat.CompatSettingsBackup
import com.valoser.futacha.shared.compat.CompatSharedTabView
import com.valoser.futacha.shared.compat.CompatTab
import com.valoser.futacha.shared.compat.CompatThreadSnapshot
import com.valoser.futacha.shared.compat.CompatWorkspaceRecord
import com.valoser.futacha.shared.compat.ScrollAnchor
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.compat.compatTabKey
import com.valoser.futacha.shared.compat.encodeCompatSettingsBackup
import com.valoser.futacha.shared.model.QuoteReference
import com.valoser.futacha.shared.model.SavedPost
import com.valoser.futacha.shared.model.SavedThreadMetadata
import kotlinx.serialization.json.Json

/**
 * Futaba-only data in every form the stores write (AM-B0): the iOS and desktop shared stores encode these models with
 * `encodeDefaults = true` (their `encodedSnapshotBytes` is the byte size of the snapshot's string), the settings backup
 * with its own encoder, and a saved thread's metadata with the saved-thread repository's. A field AM-B2 adds must not
 * change one byte of these.
 */
internal object FutabaOnlyEncodings {
    private val storeJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }
    private val savedJson = Json { ignoreUnknownKeys = true }

    private const val boardUrl = "https://may.2chan.net/b/"
    private const val threadUrl = "https://may.2chan.net/b/res/1439323740.htm"
    private val boardKey = compatBoardKey(boardUrl)
    private val tabKey = compatTabKey(threadUrl)

    val board = CompatBoard(boardKey, "虹裏may", boardUrl, "https://may.2chan.net/b/futaba.php", 0)
    val tab = CompatTab(
        key = tabKey, canonicalUrl = threadUrl, originalUrl = threadUrl, boardKey = boardKey, boardName = "虹裏may",
        threadNo = "1439323740", title = "スレ", thumbnailUrl = "https://may.2chan.net/b/thumb/1s.jpg",
        replyCount = 12, checkedReplyCount = 3, favorite = true, insertedAtEpochMillis = 1_000L,
        contentUpdatedAtEpochMillis = 2_000L, scrollAnchor = ScrollAnchor("1439323745", 12, 4, 7L), snapshotRevision = 7L
    )
    val history = CompatHistoryEntry(
        canonicalUrl = threadUrl, originalUrl = threadUrl, boardKey = boardKey, boardName = "虹裏may",
        threadNo = "1439323740", title = "スレ", replyCount = 12, contentUpdatedAtEpochMillis = 2_000L,
        lastVisitedEpochMillis = 3_000L
    )
    val snapshot = CompatThreadSnapshot(
        tabKey = tabKey, revision = 7L, fetchedAtEpochMillis = 2_000L, boardTitle = "二次元裏", expiresAtLabel = "19:54頃消えます",
        posts = listOf(
            CompatPostSnapshot(position = 0, postNo = "1439323740", timestamp = "26/11/10(月)11:59:00", messageHtml = "本文"),
            CompatPostSnapshot(
                position = 1, postNo = "1439323741", author = "としあき", mail = "sage", timestamp = "26/11/10(月)12:00:00",
                posterId = "ID:abcd", messageHtml = "&gt;&gt;1439323740", imageUrl = "https://may.2chan.net/b/src/1.jpg",
                thumbnailUrl = "https://may.2chan.net/b/thumb/1s.jpg", saidaneLabel = "そうだねx2", referencedCount = 1,
                thumbnailWidth = 250, thumbnailHeight = 188, imageFileSizeBytes = 12_345L,
                quoteReferences = listOf(QuoteReference(">>1439323740", listOf("1439323740"))), quoteReferencesParsed = true
            )
        )
    )
    val backup = CompatSettingsBackup(
        exportedAtEpochMillis = 5_000L,
        boards = listOf(board),
        tabs = listOf(tab),
        history = listOf(history),
        catalogPreferences = listOf(CompatCatalogPreference(boardKey)),
        preferences = mapOf("compat.thread.threadFontSize" to "18"),
        ngRules = listOf(CompatNgRule("ng1", CompatNgKind.CATALOG_WORD, boardKey, "word", 4_000L)),
        workspace = CompatWorkspaceRecord(activeTabKey = tabKey),
        tabViews = listOf(CompatSharedTabView(tabKey, "futaber", 0, 1_000L))
    )
    val metadata = SavedThreadMetadata(
        threadId = "1439323740", boardId = "may-b", boardName = "虹裏may", boardUrl = boardUrl, title = "スレ",
        savedAt = 6_000L, expiresAtLabel = null, totalSize = 99L,
        posts = listOf(
            SavedPost("1439323740", 0, null, null, "26/11/10(月)11:59:00", "本文", "https://may.2chan.net/b/src/1.jpg",
                "images/1.jpg", null, null, "https://may.2chan.net/b/thumb/1s.jpg", "thumbs/1s.jpg")
        )
    )

    fun all(): Map<String, String> = linkedMapOf(
        "board" to storeJson.encodeToString(CompatBoard.serializer(), board),
        "tab" to storeJson.encodeToString(CompatTab.serializer(), tab),
        "history" to storeJson.encodeToString(CompatHistoryEntry.serializer(), history),
        "snapshot" to storeJson.encodeToString(CompatThreadSnapshot.serializer(), snapshot),
        "backup" to encodeCompatSettingsBackup(backup),
        "metadata" to savedJson.encodeToString(SavedThreadMetadata.serializer(), metadata),
    )
}
