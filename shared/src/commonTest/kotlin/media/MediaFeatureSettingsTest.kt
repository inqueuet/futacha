package com.valoser.futacha.shared.media

import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.BaseInMemoryPlatformStateStorage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlin.test.*

class MediaFeatureSettingsTest {
    @Test fun missingMalformedAndFutureSettingsAreDisabled() {
        for (raw in listOf(null, "", "{broken", "null", "[]", "{\"version\":2,\"prompt_display_enabled\":true}",
            "{\"prompt_display_enabled\":true,\"prompt_placement\":\"unknown\"}")) {
            val settings = MediaFeatureSettings.decode(raw)
            MediaFeature.entries.forEach { assertFalse(settings.isEnabled(it), "Must be OFF: $raw") }
            assertFalse(settings.showAiLabels)
            assertFalse(settings.showInlinePrompt)
        }
    }

    @Test fun roundTripKeepsIndependentTogglesAndPlacement() {
        val settings = MediaFeatureSettings(promptDisplayEnabled = true, videoEditorEnabled = true,
            promptPlacement = PromptPlacement.INLINE_ONLY)
        assertEquals(settings, MediaFeatureSettings.decode(MediaFeatureSettings.encode(settings)))
        assertFalse(settings.showAiLabels)
        assertTrue(settings.showInlinePrompt)
        assertFalse(settings.isEnabled(MediaFeature.IMAGE_EDITOR))
        assertTrue(settings.isEnabled(MediaFeature.VIDEO_EDITOR))
        val labels = settings.copy(promptPlacement = PromptPlacement.LABELS_ONLY)
        assertTrue(labels.showAiLabels)
        assertFalse(labels.showInlinePrompt)
        assertFalse(labels.copy(promptDisplayEnabled = false).showAiLabels)
    }

    @Test fun concurrentUpdatesPreserveOtherTogglesAndSurviveStoreRecreation(): Unit = runBlocking {
        val storage = BaseInMemoryPlatformStateStorage()
        val store = AppStateStore(storage)
        assertEquals(MediaFeatureSettings.Disabled, store.mediaFeatureSettings.first())
        coroutineScope {
            launch { store.updateMediaFeatureSettings { it.copy(promptDisplayEnabled = true) } }
            launch { store.updateMediaFeatureSettings { it.copy(imageEditorEnabled = true) } }
            launch { store.updateMediaFeatureSettings { it.copy(videoEditorEnabled = true) } }
        }
        val restored = AppStateStore(storage).mediaFeatureSettings.first()
        MediaFeature.entries.forEach { assertTrue(restored.isEnabled(it)) }
        store.updateMediaFeatureSettings { it.copy(promptDisplayEnabled = false) }
        assertTrue(store.mediaFeatureSettings.first().imageEditorEnabled)
        assertFalse(store.mediaFeatureSettings.first().promptDisplayEnabled)
    }

    @Test fun failedWriteDoesNotPublishAnEnabledSetting(): Unit = runBlocking {
        val storage = object : BaseInMemoryPlatformStateStorage() {
            override suspend fun updateMediaFeatureSettingsJson(value: String) { error("failed") }
        }
        val store = AppStateStore(storage)
        assertFailsWith<IllegalStateException> { store.updateMediaFeatureSettings { it.copy(promptDisplayEnabled = true) } }
        assertEquals(MediaFeatureSettings.Disabled, store.mediaFeatureSettings.first())
    }

    @Test fun offThenOnDoesNotReviveOldCopyOrEditPermits() {
        val gate = MediaFeatureGate()
        assertNull(gate.permit(MediaFeature.PROMPT))
        gate.update(MediaFeatureSettings(promptDisplayEnabled = true, imageEditorEnabled = true))
        val prompt = assertNotNull(gate.permit(MediaFeature.PROMPT))
        val editor = assertNotNull(gate.permit(MediaFeature.IMAGE_EDITOR))
        gate.update(MediaFeatureSettings(imageEditorEnabled = true))
        assertFalse(gate.isCurrent(prompt))
        assertTrue(gate.isCurrent(editor))
        gate.update(MediaFeatureSettings(promptDisplayEnabled = true, imageEditorEnabled = true))
        assertFalse(gate.isCurrent(prompt))
        assertTrue(gate.isCurrent(editor))
        assertFalse(MediaFeatureGate().isCurrent(editor))
    }

    @Test fun offStartsNoWorkAndCancelsOnlyPromptConsumer(): Unit = runBlocking {
        val gate = MediaFeatureGate()
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        var starts = 0
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            gate.permits(MediaFeature.PROMPT).collectLatest { permit ->
                if (permit != null) {
                    starts++
                    started.complete(Unit)
                    try { awaitCancellation() } finally { cancelled.complete(Unit) }
                }
            }
        }
        try {
            yield()
            assertEquals(0, starts)
            gate.update(MediaFeatureSettings(promptDisplayEnabled = true, imageEditorEnabled = true))
            withTimeout(5000) { started.await() }
            gate.update(MediaFeatureSettings(promptDisplayEnabled = true, imageEditorEnabled = false))
            yield()
            assertFalse(cancelled.isCompleted, "An unrelated editor toggle must not restart prompt parsing")
            gate.update(MediaFeatureSettings.Disabled)
            withTimeout(5000) { cancelled.await() }
            assertEquals(1, starts)
        } finally { collector.cancelAndJoin() }
    }
}
