package com.valoser.futacha.shared.compat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

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
    val resultGate = remember { ExperienceProfileResultGate() }
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
