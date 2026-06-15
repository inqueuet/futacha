package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

internal data class StableTextInputState(
    val value: TextFieldValue,
    val onValueChange: (TextFieldValue) -> Unit
)

@Composable
internal fun rememberStableTextInputState(
    text: String,
    onTextChange: (String) -> Unit
): StableTextInputState {
    var fieldValue by remember {
        mutableStateOf(TextFieldValue(text = text, selection = TextRange(text.length)))
    }
    val latestOnTextChange by rememberUpdatedState(onTextChange)

    LaunchedEffect(text) {
        if (text != fieldValue.text) {
            fieldValue = TextFieldValue(
                text = text,
                selection = TextRange(text.length)
            )
        }
    }

    return StableTextInputState(
        value = fieldValue,
        onValueChange = { nextValue ->
            val previousText = fieldValue.text
            fieldValue = nextValue
            if (nextValue.text != previousText) {
                latestOnTextChange(nextValue.text)
            }
        }
    )
}
