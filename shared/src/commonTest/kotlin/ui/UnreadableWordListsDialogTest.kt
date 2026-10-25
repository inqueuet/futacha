package com.valoser.futacha.shared.ui

import com.valoser.futacha.shared.state.AppStateWordList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** L87: an unreadable NG / watch word list is named, and what 「初期化する」 and waiting do is told. */
class UnreadableWordListsDialogTest {
    @Test
    fun theDialogNamesTheListAndTellsWhatEachChoiceDoes() {
        AppStateWordList.entries.forEach { list ->
            assertEquals("「${list.label}」を読み込めません", unreadableWordListTitle(list))
            val message = unreadableWordListMessage(list)
            assertTrue(message.contains("「${list.label}」"), message)
            // Kept until the person chooses: edits are not saved, nothing stored is removed.
            assertTrue(message.contains("保存されません"), message)
            assertTrue(message.contains("そのまま残します"), message)
            // 「初期化する」 empties the list, and what could not be read does not come back.
            assertTrue(message.contains("「初期化する」"), message)
            assertTrue(message.contains("元に戻せません"), message)
        }
    }
}
