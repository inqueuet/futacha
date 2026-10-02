package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

internal data class StableTextInputState(
    val value: TextFieldValue,
    val onValueChange: (TextFieldValue) -> Unit
)

@Composable
internal fun rememberStableTextInputState(
    text: String,
    onTextChange: (String) -> Unit,
    analyticsFieldLabel: String = "入力欄"
): StableTextInputState {
    var fieldValue by remember {
        mutableStateOf(TextFieldValue(text = text, selection = TextRange(text.length)))
    }
    val latestOnTextChange by rememberUpdatedState(onTextChange)
    val latestText by rememberUpdatedState(text)
    // Bumped on each edit; `textAtEdit` is the caller's text when it was sent.
    var editToken by remember { mutableIntStateOf(0) }
    var textAtEdit by remember { mutableStateOf(text) }

    LaunchedEffect(text) {
        if (text != fieldValue.text) {
            fieldValue = TextFieldValue(
                text = text,
                selection = TextRange(text.length)
            )
        }
    }
    // A caller that clamps (e.g. `it.take(max)`) to the text it already had does
    // not change `text`, so the effect above never runs and the field would keep
    // showing characters that are not saved. Once the edit has settled, show the
    // caller's text instead.
    LaunchedEffect(editToken) {
        if (editToken == 0) return@LaunchedEffect
        withFrameNanos { }
        stableTextRejectedEditValue(fieldValue, latestText, textAtEdit)?.let { fieldValue = it }
    }

    return StableTextInputState(
        value = fieldValue,
        onValueChange = { nextValue ->
            val previousText = fieldValue.text
            fieldValue = nextValue
            if (nextValue.text != previousText) {
                textAtEdit = latestText
                editToken += 1
                latestOnTextChange(nextValue.text)
            }
        }
    )
}

/**
 * The value the field should show when the caller kept [textAtEdit] instead of
 * the edited text (its limit rejected or clamped the edit), or null when the
 * field already matches or the caller accepted a new text.
 */
internal fun stableTextRejectedEditValue(
    field: TextFieldValue,
    callerText: String,
    textAtEdit: String
): TextFieldValue? {
    if (field.text == callerText || callerText != textAtEdit) return null
    val length = callerText.length
    return TextFieldValue(
        text = callerText,
        selection = TextRange(field.selection.start.coerceIn(0, length), field.selection.end.coerceIn(0, length))
    )
}
