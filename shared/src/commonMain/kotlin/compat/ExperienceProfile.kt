package com.valoser.futacha.shared.compat

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/** Which store owns the boards, history and tabs a mode edits. */
enum class ProfileDataOwner {
    /** AppStateStore: boards and history are authoritative here. */
    APP_STATE,

    /** CompatibilityStore: boards and history are authoritative here. */
    COMPAT_STORE
}

enum class ModeAvailability {
    RELEASED,

    /**
     * Selectable on Android and iOS only ([ExperienceProfileAvailability.mobileModesEnabled]); the desktop hosts
     * neither list nor restore it, because their profile switching and board synchronisation know only
     * ふたちゃ and としあき(仮).
     */
    MOBILE,

    /** Selectable only while [ExperienceProfileAvailability.previewModesEnabled] is set (debug builds). */
    PREVIEW
}

/**
 * Release builds keep [previewModesEnabled] false so unfinished modes are neither listed nor restored.
 */
object ExperienceProfileAvailability {
    private var configured: Boolean? = null
    private var configuredMobile: Boolean? = null

    /** True on Android and iOS, false on the desktop. Android sets it in the Application; iOS reports it itself. */
    var mobileModesEnabled: Boolean
        get() = configuredMobile ?: platformDefaultMobileModesEnabled()
        set(value) {
            configuredMobile = value
        }

    /** Android sets this from the debuggable flag; other hosts use [platformDefaultPreviewModesEnabled]. */
    var previewModesEnabled: Boolean
        get() = configured ?: platformDefaultPreviewModesEnabled()
        set(value) {
            configured = value
        }
}

/** iOS: debug binaries only. Android: false until the Application sets it. Desktop: never. */
expect fun platformDefaultPreviewModesEnabled(): Boolean

/** iOS: true. Android: false until the Application sets it. Desktop: never. */
expect fun platformDefaultMobileModesEnabled(): Boolean

enum class ExperienceProfile(
    val persistedValue: String,
    val displayName: String,
    val dataOwner: ProfileDataOwner,
    val availability: ModeAvailability
) {
    FUTACHA("futacha", "ふたちゃモード", ProfileDataOwner.APP_STATE, ModeAvailability.RELEASED),
    TOSHIAKI_COMPAT("toshiaki_compat", "としあき(仮)モード", ProfileDataOwner.COMPAT_STORE, ModeAvailability.RELEASED),
    FUTABER("futaber", "ふたばー風モード", ProfileDataOwner.APP_STATE, ModeAvailability.MOBILE);

    /** True when the mode edits the AppStateStore boards/history (ふたちゃ, ふたばー). */
    val usesAppStateData: Boolean get() = dataOwner == ProfileDataOwner.APP_STATE

    val isSelectable: Boolean
        get() = when (availability) {
            ModeAvailability.RELEASED -> true
            ModeAvailability.MOBILE ->
                ExperienceProfileAvailability.mobileModesEnabled || ExperienceProfileAvailability.previewModesEnabled
            ModeAvailability.PREVIEW -> ExperienceProfileAvailability.previewModesEnabled
        }

    companion object {
        /** Profiles the user may choose or have restored, in declaration order. */
        val selectableEntries: List<ExperienceProfile>
            get() = entries.filter { it.isSelectable }

        /** Unknown values, and preview modes outside preview builds, fall back to ふたちゃ. */
        fun fromPersistedValue(value: String?): ExperienceProfile =
            entries.firstOrNull { it.persistedValue == value && it.isSelectable } ?: FUTACHA
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
