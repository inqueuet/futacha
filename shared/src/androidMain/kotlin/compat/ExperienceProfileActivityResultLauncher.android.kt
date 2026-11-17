package com.valoser.futacha.shared.compat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable

/**
 * The only Activity Result launcher used by the shared Android UI.
 *
 * Launch captures the active experience-profile generation. A callback from a picker,
 * permission dialog, or external recognizer is delivered only once and only while that
 * exact profile session is still current. Launch failures clear their token so a later
 * callback cannot consume stale authority.
 */
class ExperienceProfileActivityResultLauncher<I> internal constructor(
    private val launchCurrent: (I) -> Unit
) {
    fun launch(input: I) = launchCurrent(input)
}

@Composable
fun <I, O> rememberExperienceProfileActivityResultLauncher(
    contract: ActivityResultContract<I, O>,
    onCurrentResult: (O, ExperienceProfileSessionToken) -> Unit
): ExperienceProfileActivityResultLauncher<I> {
    val controllerState = rememberUpdatedState(LocalExperienceProfileUiController.current)
    val callbackState = rememberUpdatedState(onCurrentResult)
    // Saved with the instance state, like the launcher's own registry key: after the
    // process is killed while the picker is open, the restored launcher receives the
    // result and the token must still be there to authorize it (M-4).
    val pendingToken = rememberSaveable { mutableStateOf<String?>(null) }
    val resultGate = remember(pendingToken) { SavedExperienceProfileResultGate(pendingToken) }
    DisposableEffect(resultGate) { onDispose { resultGate.clear() } }
    val launcher = rememberLauncherForActivityResult(contract) { result ->
        val session = resultGate.consumeIfCurrent(controllerState.value)
            ?: return@rememberLauncherForActivityResult
        callbackState.value(result, session)
    }
    return remember(launcher, resultGate) {
        ExperienceProfileActivityResultLauncher { input ->
            resultGate.markLaunched(controllerState.value)
            try {
                launcher.launch(input)
            } catch (error: Throwable) {
                resultGate.clear()
                throw error
            }
        }
    }
}

/** [ExperienceProfileResultGate] whose pending token lives in saveable state. */
internal class SavedExperienceProfileResultGate(private val pending: MutableState<String?>) {
    fun markLaunched(controller: ExperienceProfileUiController) {
        pending.value = encodeExperienceProfileSessionToken(captureExperienceProfileSession(controller))
    }

    fun consumeIfCurrent(controller: ExperienceProfileUiController): ExperienceProfileSessionToken? {
        val token = decodeExperienceProfileSessionToken(pending.value)
        pending.value = null
        return token?.takeIf { isExperienceProfileSessionCurrent(it, controller) }
    }

    fun clear() {
        pending.value = null
    }
}

internal fun encodeExperienceProfileSessionToken(token: ExperienceProfileSessionToken): String =
    "${token.profile.persistedValue}:${token.generation}"

internal fun decodeExperienceProfileSessionToken(value: String?): ExperienceProfileSessionToken? {
    val separator = value?.lastIndexOf(':')?.takeIf { it > 0 } ?: return null
    val profile = ExperienceProfile.entries.firstOrNull { it.persistedValue == value.substring(0, separator) }
        ?: return null
    val generation = value.substring(separator + 1).toLongOrNull() ?: return null
    return ExperienceProfileSessionToken(profile, generation)
}
