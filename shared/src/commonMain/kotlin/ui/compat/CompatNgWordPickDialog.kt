package com.valoser.futacha.shared.ui.compat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow

/** カタログの長押しの追加の項目（原作の7項目の後ろ）。 */
internal const val COMPAT_CATALOG_PICK_NG_WORD = "スレ文から語句を選んでNGワードに登録"

/** 選んだ語句（何も選んでいなければスレ文の全部）。前後の空白を除き100字まで。空なら null。 */
internal fun compatPickedNgWord(text: String, selection: TextRange): String? {
    val picked = if (selection.collapsed) text else text.substring(selection.min.coerceIn(0, text.length), selection.max.coerceIn(0, text.length))
    return picked.trim().take(100).ifBlank { null }
}

/**
 * 1447422626：キーボードで打つと予測変換に語が残るので、スレ文を読むだけの欄に出して、
 * 指で選んだ語句をNGワードにする（キーボードは出ない）。
 */
@Composable
internal fun CompatNgWordPickDialog(text: String, onDismiss: () -> Unit, onRegister: (String) -> Unit) {
    var value by remember(text) { mutableStateOf(TextFieldValue(text)) }
    val picked = compatPickedNgWord(value.text, value.selection)
    FutachaAppLockAwareWindow {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("NGワードに登録") },
            text = {
                Column {
                    Text("登録する語句を長押しして選んでください。選ばない時はスレ文の全部を登録します。")
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it.copy(text = text) },
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().testTag("compat-ng-word-pick-field")
                    )
                    Text("登録する語句：${picked ?: "（なし）"}")
                }
            },
            confirmButton = {
                TextButton(enabled = picked != null, onClick = { picked?.let(onRegister) },
                    modifier = Modifier.testTag("compat-ng-word-pick-register")) { Text("登録") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } }
        )
    }
}
