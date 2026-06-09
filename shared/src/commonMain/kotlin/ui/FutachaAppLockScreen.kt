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
import com.valoser.futacha.shared.state.verifyAppLockPassword
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
    val lockoutRemainingSeconds = ((lockoutUntilMillis - nowMillis + 999L) / 1_000L).coerceAtLeast(0L)

    LaunchedEffect(lockoutUntilMillis, lockoutRefreshToken) {
        val delayMillis = lockoutUntilMillis - Clock.System.now().toEpochMilliseconds()
        if (delayMillis > 0L) {
            delay(delayMillis)
            lockoutRefreshToken += 1
        }
    }

    fun submit() {
        if (lockoutUntilMillis > Clock.System.now().toEpochMilliseconds()) {
            isError = true
            return
        }
        if (verifyAppLockPassword(input, passwordHash)) {
            input = ""
            isError = false
            failedAttempts = 0
            lockoutUntilMillis = 0L
            onUnlocked()
        } else {
            failedAttempts += 1
            if (failedAttempts >= APP_LOCK_MAX_FAILURES_BEFORE_WAIT) {
                failedAttempts = 0
                lockoutUntilMillis = Clock.System.now().toEpochMilliseconds() + APP_LOCK_FAILURE_WAIT_MILLIS
            }
            isError = true
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
                    value = input,
                    onValueChange = {
                        input = it
                        isError = false
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
