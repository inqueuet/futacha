package com.valoser.futacha.shared.compat

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

enum class ExperienceProfile(
    val persistedValue: String,
    val displayName: String
) {
    FUTACHA("futacha", "ふたちゃモード"),
    TOSHIAKI_COMPAT("toshiaki_compat", "としあき(仮)モード");

    companion object {
        fun fromPersistedValue(value: String?): ExperienceProfile =
            entries.firstOrNull { it.persistedValue == value } ?: FUTACHA
    }
}

enum class ModeSwitchPhase {
    SESSION_FLUSHED,
    OLD_PROFILE_QUIESCED,
    PROFILE_PERSISTED,
    LAUNCHER_ALIAS_UPDATED,
    ROOT_REBUILT
}

data class ModeSwitchJournal(
    val from: ExperienceProfile,
    val to: ExperienceProfile,
    val phase: ModeSwitchPhase,
    val generation: Long
)

@Immutable
data class ExperienceProfileUiController(
    val isAvailable: Boolean = false,
    val activeProfile: ExperienceProfile = ExperienceProfile.FUTACHA,
    val sessionGeneration: Long = 0L,
    val isSessionActive: Boolean = true,
    val switchInProgress: Boolean = false,
    val lastError: String? = null,
    val isSessionAuthoritativelyCurrent: ((ExperienceProfileSessionToken) -> Boolean)? = null,
    val requestSwitch: (ExperienceProfile) -> Unit = {}
)

val LocalExperienceProfileUiController = staticCompositionLocalOf {
    ExperienceProfileUiController()
}

data class ExperienceProfileSessionToken(
    val profile: ExperienceProfile,
    val generation: Long
)

/** Keeps persisted session generations positive and prevents signed overflow. */
fun nextExperienceProfileGeneration(current: Long): Long =
    if (current <= 0L || current == Long.MAX_VALUE) 1L else current + 1L

fun captureExperienceProfileSession(
    controller: ExperienceProfileUiController
): ExperienceProfileSessionToken = ExperienceProfileSessionToken(
    profile = controller.activeProfile,
    generation = controller.sessionGeneration
)

fun isExperienceProfileSessionCurrent(
    token: ExperienceProfileSessionToken,
    controller: ExperienceProfileUiController
): Boolean {
    val snapshotIsCurrent = controller.isSessionActive &&
        token.profile == controller.activeProfile &&
        token.generation == controller.sessionGeneration
    if (!snapshotIsCurrent) return false
    return controller.isSessionAuthoritativelyCurrent?.invoke(token) != false
}

class ExperienceProfileResultGate {
    private var pending: ExperienceProfileSessionToken? = null

    fun markLaunched(controller: ExperienceProfileUiController) {
        pending = captureExperienceProfileSession(controller)
    }

    fun consumeIfCurrent(controller: ExperienceProfileUiController): ExperienceProfileSessionToken? {
        val token = pending
        pending = null
        return token?.takeIf { isExperienceProfileSessionCurrent(it, controller) }
    }

    fun clear() {
        pending = null
    }
}
