package com.valoser.futacha.shared.ai

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// Round 4 A4-4: a category written by a newer build made every save() fail with
// "判定カテゴリを1つ以上選択してください" after a downgrade; it could not be removed in the UI.
class AiConnectionCategoriesTest {
    private fun stored(categories: String) = """{"moderationProvider":"BOTH","moderationCategories":$categories,""" +
        """"apiKeys":[{"id":"k1","name":"OpenAI 1","key":"test-only-key"}]}"""

    @Test fun unknownCategoriesFromANewerBuildAreDroppedAndSaveSucceeds() = runBlocking {
        val storage = MemoryAiStorage().apply { credentials = stored("""["harassment","future/category"]""") }
        val store = AiConnectionStore(storage)
        store.load()
        assertNull(store.state.value.storageError)
        assertEquals(setOf("harassment"), store.state.value.moderationCategories)
        store.save(AiProvider.DEVICE, AiProvider.BOTH, DEFAULT_OPENAI_SUMMARY_MODEL, moderationThreshold = 0.5f)
        val reopened = AiConnectionStore(storage).also { it.load() }.state.value
        assertEquals(setOf("harassment"), reopened.moderationCategories)
        assertEquals(0.5f, reopened.moderationThreshold)
        assertEquals(listOf("k1"), reopened.apiKeys.map { it.id }, "the saved keys are kept")
    }

    @Test fun onlyUnknownCategoriesFallBackToTheDefaults() = runBlocking {
        val storage = MemoryAiStorage().apply { credentials = stored("""["future/category"]""") }
        val store = AiConnectionStore(storage)
        store.load()
        assertEquals(DEFAULT_OPENAI_MODERATION_CATEGORIES, store.state.value.moderationCategories)
        store.save(AiProvider.DEVICE, AiProvider.BOTH, DEFAULT_OPENAI_SUMMARY_MODEL)
        assertEquals(AiProvider.BOTH, store.state.value.moderationProvider)
    }
}
