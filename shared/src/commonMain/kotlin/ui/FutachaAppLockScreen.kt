package com.valoser.futacha.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.analytics.AnalyticsTracker
import com.valoser.futacha.shared.state.verifyAppLockPassword
import com.valoser.futacha.shared.ui.board.rememberStableTextInputState
import com.valoser.futacha.shared.util.safeEpochElapsedMillis
import kotlinx.coroutines.delay

/**
 * Whether the app content is shown, as of the last composition. Use it to hide
 * UI (dialogs, sheets); code that acts on commands or runs in coroutines must
 * read [LocalFutachaAppLockHolder] instead, because Android pauses
 * recomposition after ON_STOP and this value then stays `true` (C-1).
 */
internal val LocalFutachaAppUnlocked = androidx.compose.runtime.staticCompositionLocalOf { true }

@Composable
internal fun FutachaAppLockScreen(
    passwordHash: String,
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * The persisted counter / cool-down (read once per process) and the writer for changes. The
     * counter itself lives in [AppLockAttemptTracker.Process]; without a store it still survives
     * rotation and re-locking but not a force-stop.
     */
    persistedAttempts: AppLockAttemptState = AppLockAttemptState(),
    onAttemptsChanged: (AppLockAttemptState) -> Unit = {},
    /** False while the persisted state is still being read: unlocking is ignored until then. */
    attemptsLoaded: Boolean = true
) {
    var input by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    val tracker = AppLockAttemptTracker.Process
    // Bumped whenever the tracker changes or a cool-down ends, so the remaining wait is re-read.
    var attemptRefreshToken by remember { mutableStateOf(0) }
    @Suppress("UNUSED_VARIABLE")
    val observedRefreshToken = attemptRefreshToken
    val lockoutRemainingMillis = tracker.remainingMillis()
    val isTemporarilyLocked = lockoutRemainingMillis > 0L || !attemptsLoaded
    val currentOnAttemptsChanged by rememberUpdatedState(onAttemptsChanged)
    val lockoutRemainingSeconds = lockoutRemainingMillis / 1_000L + if (lockoutRemainingMillis % 1_000L == 0L) 0L else 1L
    val inputState = rememberStableTextInputState(
        text = input,
        onTextChange = {
            input = it
            isError = false
        },
        analyticsFieldLabel = "起動ロック解除パスワード"
    )

    LaunchedEffect(attemptsLoaded) {
        if (attemptsLoaded) {
            tracker.restoreOnce(persistedAttempts)
            attemptRefreshToken += 1
        }
    }
    LaunchedEffect(attemptRefreshToken) {
        val delayMillis = tracker.remainingMillis()
        if (delayMillis > 0L) {
            delay(delayMillis)
            // The wait is over: persist that, so a force-stop now does not make the next launch wait again.
            currentOnAttemptsChanged(tracker.snapshot())
            attemptRefreshToken += 1
        }
    }

    fun submit() {
        if (!attemptsLoaded) return
        AnalyticsTracker.uiControl("app_lock", "起動ロックの解除を実行")
        if (tracker.isLockedOut()) {
            isError = true
            AnalyticsTracker.uiControl("app_lock", "起動ロックの解除を試行: 一時ロック中")
            return
        }
        if (verifyAppLockPassword(input, passwordHash)) {
            input = ""
            isError = false
            tracker.recordSuccess()
            onAttemptsChanged(tracker.snapshot())
            AnalyticsTracker.uiControl("app_lock", "起動ロックを解除: 成功")
            onUnlocked()
        } else {
            val startedLockout = tracker.recordFailure()
            onAttemptsChanged(tracker.snapshot())
            attemptRefreshToken += 1
            isError = true
            AnalyticsTracker.uiControl(
                "app_lock",
                if (startedLockout) {
                    "起動ロックの解除に失敗: 一時ロック"
                } else {
                    "起動ロックの解除に失敗"
                }
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 3.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    text = "ふたちゃ",
                    style = MaterialTheme.typography.headlineSmall
                )
                OutlinedTextField(
                    value = inputState.value,
                    onValueChange = { nextValue ->
                        val wasFilled = inputState.value.text.isNotBlank()
                        val isFilled = nextValue.text.isNotBlank()
                        if (wasFilled != isFilled) {
                            AnalyticsTracker.uiControl(
                                "app_lock_input_state",
                                if (isFilled) "起動ロック用パスワードの入力を開始" else "起動ロック用パスワードを消去",
                                mapOf("input_state" to if (isFilled) "入力あり" else "空")
                            )
                        }
                        inputState.onValueChange(nextValue)
                    },
                    label = { Text("パスワード") },
                    singleLine = true,
                    isError = isError,
                    supportingText = if (isError) {
                        {
                            Text(
                                if (isTemporarilyLocked) {
                                    "${lockoutRemainingSeconds}秒後に再試行できます。"
                                } else {
                                    "パスワードが違います。"
                                }
                            )
                        }
                    } else {
                        null
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions.Default.copy(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (input.isNotBlank() && !isTemporarilyLocked) {
                                submit()
                            }
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = ::submit,
                    enabled = input.isNotBlank() && !isTemporarilyLocked,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("解除")
                }
            }
        }
    }
}

internal fun appLockRemainingMillis(lockoutUntilMillis: Long, nowMillis: Long): Long {
    if (lockoutUntilMillis <= nowMillis) return 0L
    return safeEpochElapsedMillis(lockoutUntilMillis, nowMillis)
}

internal fun appLockRemainingSeconds(lockoutUntilMillis: Long, nowMillis: Long): Long {
    val remainingMillis = appLockRemainingMillis(lockoutUntilMillis, nowMillis)
    return remainingMillis / 1_000L + if (remainingMillis % 1_000L == 0L) 0L else 1L
}

@Composable
internal fun FutachaAppLockLoadingScreen(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

internal enum class FutachaAppLockGate { Loading, Error, Locked, Unlocked }

internal fun resolveFutachaAppLockGate(
    storedHash: String?,
    isUnlockedForSession: Boolean,
    loadingSentinel: String,
    errorSentinel: String
): FutachaAppLockGate = when {
    storedHash == loadingSentinel -> FutachaAppLockGate.Loading
    // Fail closed: an unreadable lock state must not reveal the app.
    storedHash == errorSentinel -> FutachaAppLockGate.Error
    storedHash != null && !isUnlockedForSession -> FutachaAppLockGate.Locked
    else -> FutachaAppLockGate.Unlocked
}

@Composable
internal fun FutachaAppLockGateContent(
    gate: FutachaAppLockGate,
    passwordHash: String,
    onUnlocked: () -> Unit,
    onRetry: () -> Unit,
    persistedAttempts: AppLockAttemptState = AppLockAttemptState(),
    onAttemptsChanged: (AppLockAttemptState) -> Unit = {},
    attemptsLoaded: Boolean = true
) {
    when (gate) {
        FutachaAppLockGate.Loading -> FutachaAppLockLoadingScreen()
        FutachaAppLockGate.Error -> FutachaAppLockLoadErrorScreen(onRetry = onRetry)
        FutachaAppLockGate.Locked -> {
            LaunchedEffect(Unit) {
                AnalyticsTracker.screen("app_lock")
            }
            FutachaAppLockScreen(
                passwordHash = passwordHash,
                onUnlocked = onUnlocked,
                persistedAttempts = persistedAttempts,
                onAttemptsChanged = onAttemptsChanged,
                attemptsLoaded = attemptsLoaded
            )
        }
        FutachaAppLockGate.Unlocked -> Unit
    }
}

/**
 * Covers the still-composed app while it is relocked.  The in-window surface
 * hides the app (and blocks its input) in the same frame the lock engages; the
 * full-screen dialog window is created afterwards, so it is stacked above any
 * dialog, sheet or menu window the app already had open.
 */
@Composable
internal fun FutachaAppLockOverlay(content: @Composable () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {}
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}

@Composable
internal fun FutachaAppLockLoadErrorScreen(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = "起動ロックの設定を読み込めませんでした。",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            OutlinedButton(onClick = onRetry) {
                Text("再試行")
            }
        }
    }
}
