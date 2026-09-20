package com.valoser.futacha.shared.media

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Recheck immediately before publishing a result, copying text or exporting an edit. */
class MediaFeaturePermit internal constructor(
    internal val owner: MediaFeatureGate,
    val feature: MediaFeature,
    internal val generation: Long
)

/** Independent feature generations prevent one setting from cancelling another feature. */
class MediaFeatureGate {
    private data class State(
        val settings: MediaFeatureSettings = MediaFeatureSettings.Disabled,
        val generations: Map<MediaFeature, Long> = MediaFeature.entries.associateWith { 0L }
    )
    private val state = MutableStateFlow(State())

    fun update(settings: MediaFeatureSettings) {
        state.update { old ->
            val generations = old.generations.mapValues { (feature, generation) ->
                val changed = old.settings.isEnabled(feature) != settings.isEnabled(feature) ||
                    (feature == MediaFeature.PROMPT && old.settings.promptPlacement != settings.promptPlacement)
                if (changed) generation + 1 else generation
            }
            State(settings, generations)
        }
    }

    fun permit(feature: MediaFeature): MediaFeaturePermit? = state.value.let {
        if (it.settings.isEnabled(feature)) MediaFeaturePermit(this, feature, it.generations.getValue(feature)) else null
    }

    fun isCurrent(permit: MediaFeaturePermit): Boolean = state.value.let {
        permit.owner === this && it.settings.isEnabled(permit.feature) &&
            it.generations.getValue(permit.feature) == permit.generation
    }

    /** collectLatest cancels only this feature's work on OFF or placement changes. */
    fun permits(feature: MediaFeature): Flow<MediaFeaturePermit?> = state
        .map { it.settings.isEnabled(feature) to it.generations.getValue(feature) }
        .distinctUntilChanged()
        .map { (enabled, generation) -> if (enabled) MediaFeaturePermit(this, feature, generation) else null }
}
