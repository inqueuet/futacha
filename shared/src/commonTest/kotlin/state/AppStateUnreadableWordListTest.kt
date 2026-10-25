package com.valoser.futacha.shared.state

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * L87: an NG / watch word list that is stored but cannot be read (damaged, or over the size limit) was shown as empty,
 * and the next edit saved that empty view with only the new entry, wiping every stored one.
 */
class AppStateUnreadableWordListTest {
    private val damaged = """["荒らし","晒""""
    private val tooLarge = "[" + (0 until 200_000).joinToString(",") { "\"word$it\"" } + "]"

    @Test
    fun anEditOfAnUnreadableListKeepsTheStoredOne() = runBlocking<Unit> {
        val storage = FakePlatformStateStorage()
        val store = AppStateStore(storage)
        storage.updateNgWordsJson(damaged)
        storage.updateNgHeadersJson(damaged)
        storage.updateCatalogNgWordsJson(damaged)
        storage.updateWatchWordsJson(tooLarge)
        assertTrue(tooLarge.length > APP_STATE_SETTINGS_JSON_MAX_CHARS)

        store.updateNgWords { it + "新しい語" }
        store.setNgWords(listOf("新しい語"))
        store.updateNgHeaders { it + "ID:abc" }
        store.setNgHeaders(listOf("ID:abc"))
        store.updateCatalogNgWords { it + "晒し" }
        store.setCatalogNgWords(listOf("晒し"))
        store.updateWatchWords { it + "猫" }
        store.setWatchWords(listOf("猫"))

        assertEquals(damaged, storage.ngWordsJson.first())
        assertEquals(damaged, storage.ngHeadersJson.first())
        assertEquals(damaged, storage.catalogNgWordsJson.first())
        assertEquals(tooLarge, storage.watchWordsJson.first())
        assertNotNull(store.lastStorageError.value)
    }

    @Test
    fun anEditOfAnUnreadableBoardWatchWordMapKeepsTheStoredOne() = runBlocking<Unit> {
        val storage = FakePlatformStateStorage()
        val store = AppStateStore(storage)
        val damagedMap = """{"b":["猫"],"img":["""
        storage.updateBoardWatchWordsJson(damagedMap)

        store.setBoardWatchWords("b", listOf("犬"))
        store.updateBoardWatchWords("img") { it + "犬" }
        store.clearBoardWatchWordsOverride("b")

        assertEquals(damagedMap, storage.boardWatchWordsJson.first())
        // A board without its own words starts from the global list: an unreadable global list is not edited from empty.
        storage.updateBoardWatchWordsJson("{}")
        storage.updateWatchWordsJson(damaged)
        store.updateBoardWatchWords("b") { it + "犬" }
        assertEquals("{}", storage.boardWatchWordsJson.first())
    }

    @Test
    fun anUnreadableListIsReportedAndCanBeResetToEditAgain() = runBlocking<Unit> {
        val storage = FakePlatformStateStorage()
        val store = AppStateStore(storage)
        storage.updateNgWordsJson(damaged)
        storage.updateBoardWatchWordsJson("""{"b":""")

        assertEquals(setOf(AppStateWordList.NG_WORDS, AppStateWordList.BOARD_WATCH_WORDS), store.unreadableWordLists.first())
        // A list that reads is never reset.
        assertFalse(store.resetUnreadableWordList(AppStateWordList.WATCH_WORDS))
        assertEquals(null, storage.watchWordsJson.first())

        assertTrue(store.resetUnreadableWordList(AppStateWordList.NG_WORDS))
        assertTrue(store.resetUnreadableWordList(AppStateWordList.BOARD_WATCH_WORDS))
        assertEquals(emptySet(), store.unreadableWordLists.first())
        assertEquals(emptyList(), store.ngWords.first())

        store.updateNgWords { it + "新しい語" }
        store.setBoardWatchWords("b", listOf("犬"))
        assertEquals(listOf("新しい語"), store.ngWords.first())
        assertEquals(mapOf("b" to listOf("犬")), store.boardWatchWords.first())
        assertFalse(store.resetUnreadableWordList(AppStateWordList.NG_WORDS))
        assertEquals(listOf("新しい語"), store.ngWords.first())
    }

    @Test
    fun readableAndAbsentListsAreEditedAsBefore() = runBlocking<Unit> {
        val storage = FakePlatformStateStorage()
        val store = AppStateStore(storage)
        store.updateNgWords { it + "一" }
        store.updateNgWords { it + "二" }
        store.setWatchWords(listOf("猫"))
        store.updateBoardWatchWords("b") { it + "犬" }
        assertEquals(listOf("一", "二"), store.ngWords.first())
        assertEquals(mapOf("b" to listOf("猫", "犬")), store.boardWatchWords.first())
        assertEquals(emptySet(), store.unreadableWordLists.first())
    }
}
