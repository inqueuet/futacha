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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.analytics.AnalyticsTracker
import com.valoser.futacha.shared.state.verifyAppLockPassword
import com.valoser.futacha.shared.ui.board.rememberStableTextInputState
import com.valoser.futacha.shared.util.safeEpochElapsedMillis
import com.valoser.futacha.shared.util.saturatingEpochAdd
import kotlinx.coroutines.delay
import kotlin.time.Clock

private const val APP_LOCK_MAX_FAILURES_BEFORE_WAIT = 5
private const val APP_LOCK_FAILURE_WAIT_MILLIS = 15_000L

@Composable
internal fun FutachaAppLockScreen(
    passwordHash: String,
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier
) {
    var input by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var failedAttempts by rememberSaveable { mutableStateOf(0) }
    var lockoutUntilMillis by rememberSaveable { mutableStateOf(0L) }
    var lockoutRefreshToken by rememberSaveable { mutableStateOf(0) }
    val nowMillis = Clock.System.now().toEpochMilliseconds()
    val isTemporarilyLocked = lockoutUntilMillis > nowMillis
    val lockoutRemainingSeconds = appLockRemainingSeconds(lockoutUntilMillis, nowMillis)
    val inputState = rememberStableTextInputState(
        text = input,
        onTextChange = {
            input = it
            isError = false
        },
        analyticsFieldLabel = "起動ロック解除パスワード"
    )

    LaunchedEffect(lockoutUntilMillis, lockoutRefreshToken) {
        val delayMillis = appLockRemainingMillis(
            lockoutUntilMillis,
            Clock.System.now().toEpochMilliseconds()
        )
        if (delayMillis > 0L) {
            delay(delayMillis)
            lockoutRefreshToken += 1
        }
    }

    fun submit() {
        AnalyticsTracker.uiControl("app_lock", "起動ロックの解除を実行")
        if (lockoutUntilMillis > Clock.System.now().toEpochMilliseconds()) {
            isError = true
            AnalyticsTracker.uiControl("app_lock", "起動ロックの解除を試行: 一時ロック中")
            return
        }
        if (verifyAppLockPassword(input, passwordHash)) {
            input = ""
            isError = false
            failedAttempts = 0
            lockoutUntilMillis = 0L
            AnalyticsTracker.uiControl("app_lock", "起動ロックを解除: 成功")
            onUnlocked()
        } else {
            failedAttempts += 1
            if (failedAttempts >= APP_LOCK_MAX_FAILURES_BEFORE_WAIT) {
                failedAttempts = 0
                lockoutUntilMillis = saturatingEpochAdd(
                    Clock.System.now().toEpochMilliseconds(),
                    APP_LOCK_FAILURE_WAIT_MILLIS
                )
            }
            isError = true
            AnalyticsTracker.uiControl(
                "app_lock",
                if (lockoutUntilMillis > Clock.System.now().toEpochMilliseconds()) {
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
