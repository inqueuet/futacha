package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.valoser.futacha.shared.audio.TextSpeaker

/**
 * Closes [speaker] when it leaves the composition or is replaced.
 *
 * The effect captures the instance it was keyed on. Reading the state in
 * onDispose instead would close the speaker that was just created (the key
 * changes from null to the new instance on first use), so the thread could
 * never read aloud again.
 */
@Composable
internal fun CompatTextSpeakerDisposal(speaker: TextSpeaker?) {
    DisposableEffect(speaker) {
        onDispose { speaker?.close() }
    }
}
