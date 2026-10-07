package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valoser.futacha.shared.state.APP_LOCK_PASSWORD_MAX_LENGTH
import com.valoser.futacha.shared.state.APP_LOCK_PASSWORD_MIN_LENGTH
import com.valoser.futacha.shared.state.isValidAppLockPassword
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow

/** Why a lock password typed here is refused, or null when it is fine to set. */
internal fun futaberAppLockPasswordError(password: String, confirmation: String): String? = when {
    !isValidAppLockPassword(password) -> "${APP_LOCK_PASSWORD_MIN_LENGTH}文字以上で入力してください。"
    password != confirmation -> "確認用パスワードが一致しません。"
    else -> null
}

/**
 * Sets, changes or removes the start-up lock password. It is the lock of the whole app, the one ふたちゃ sets in its
 * own settings; this mode shows no statistics about it. The password stays in memory only (not saved with the screen state).
 */
@Composable
internal fun FutaberAppLockDialog(
    enabled: Boolean,
    onSetPassword: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalFutaberColors.current
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    FutachaAppLockAwareWindow {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = colors.bar,
            titleContentColor = colors.body,
            textContentColor = colors.body,
            title = { Text("起動ロック") },
            text = {
                Column {
                    Text(
                        if (enabled) "有効です。アプリ起動時にパスワードを要求します。" else "無効です。パスワードを設定すると起動時にロックします。",
                        color = colors.meta, fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it.futaberTakeChars(APP_LOCK_PASSWORD_MAX_LENGTH); error = null },
                        label = { Text(if (enabled) "新しいパスワード" else "パスワード") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("futaber-lock-password")
                    )
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it.futaberTakeChars(APP_LOCK_PASSWORD_MAX_LENGTH); error = null },
                        label = { Text("確認") },
                        singleLine = true,
                        isError = error != null,
                        supportingText = error?.let { message -> { Text(message) } },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("futaber-lock-confirmation")
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val problem = futaberAppLockPasswordError(password, confirmation)
                        if (problem != null) error = problem else { onSetPassword(password); onDismiss() }
                    },
                    modifier = Modifier.testTag("futaber-lock-save")
                ) { Text(if (enabled) "変更" else "有効にする", color = colors.link) }
            },
            dismissButton = {
                Column {
                    if (enabled) TextButton(
                        onClick = { onClear(); onDismiss() },
                        modifier = Modifier.testTag("futaber-lock-clear")
                    ) { Text("解除", color = colors.accent) }
                    TextButton(onClick = onDismiss) { Text("キャンセル", color = colors.link) }
                }
            }
        )
    }
}
