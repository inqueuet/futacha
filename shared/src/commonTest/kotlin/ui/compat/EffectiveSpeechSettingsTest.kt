package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.audio.SpeechEngineKind
import com.valoser.futacha.shared.audio.SpeechSettings
import kotlin.test.Test
import kotlin.test.assertEquals

/** L4: the settings screen shows the voice actually used without changing the saved choice. */
class EffectiveSpeechSettingsTest {
    private val system = SpeechSettings(engine = SpeechEngineKind.SYSTEM, rate = 1.5f)
    private val zundamon = SpeechSettings(engine = SpeechEngineKind.VOICEVOX_ZUNDAMON, rate = 1.5f)

    @Test fun aCapableDeviceShowsTheSavedChoice() {
        assertEquals(zundamon, effectiveSpeechSettings(zundamon, zundamonAvailable = true))
        assertEquals(system, effectiveSpeechSettings(system, zundamonAvailable = true))
    }

    @Test fun anUnsupportedDeviceShowsTheSystemVoiceItActuallyUses() {
        assertEquals(zundamon.copy(engine = SpeechEngineKind.SYSTEM), effectiveSpeechSettings(zundamon, zundamonAvailable = false))
        assertEquals(system, effectiveSpeechSettings(system, zundamonAvailable = false))
    }
}
