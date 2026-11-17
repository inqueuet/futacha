package com.valoser.futacha.shared.ui.board

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/** Only decorates the IME-owned range; text, offsets and the composition itself stay intact. */
internal class ImeCompositionHighlight(
    private val range: TextRange?,
    private val background: Color,
    private val foreground: Color = if (background.luminance() < .179f) Color.White else Color.Black
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val decorated = AnnotatedString.Builder(text)
        range?.let {
            val start = it.min.coerceIn(0, text.length)
            val end = it.max.coerceIn(start, text.length)
            if (start < end) decorated.addStyle(SpanStyle(color = foreground, background = background), start, end)
        }
        return TransformedText(decorated.toAnnotatedString(), OffsetMapping.Identity)
    }
}

/** Input decoration has its own readable pair; a toolbar colour is not an input background. */
internal data class ImeCompositionColors(
    val background: Color,
    val foreground: Color,
    /** The clause being converted stands out from the rest of the composition. */
    val clauseBackground: Color = background,
    val clauseForeground: Color = foreground
)

internal fun imeCompositionColors(surface: Color): ImeCompositionColors =
    if (surface.luminance() < .179f) ImeCompositionColors(Color(0xFF394B66), Color.White, Color(0xFF2E7D76), Color.White)
    else ImeCompositionColors(Color(0xFFD7E4F7), Color(0xFF1A1C1E), Color(0xFF7FB8B2), Color(0xFF1A1C1E))

@Composable
internal fun imeCompositionHighlight(range: TextRange?): VisualTransformation {
    val pair = imeCompositionColors(MaterialTheme.colorScheme.background)
    return ImeCompositionHighlight(range, pair.background, pair.foreground)
}

@Composable
internal fun ImeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null,
    singleLine: Boolean = false,
    colors: TextFieldColors = TextFieldDefaults.colors()
) {
    val input = rememberStableTextInputState(value, onValueChange)
    TextField(input.value, input.onValueChange, modifier = modifier, label = label, singleLine = singleLine,
        colors = colors, visualTransformation = imeCompositionHighlight(input.value.composition))
}

@Composable
internal fun ImeOutlinedTextField(
    value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null, singleLine: Boolean = false
) {
    val input = rememberStableTextInputState(value, onValueChange)
    OutlinedTextField(input.value, input.onValueChange, modifier = modifier, label = label,
        singleLine = singleLine, visualTransformation = imeCompositionHighlight(input.value.composition))
}

/**
 * The clause the IME is converting, if it says which one: on iOS the marked text's selectedRange
 * becomes the selection inside the composition (BasicTextField with TextFieldState). Android IMEs
 * send their clause styling as spans, which TextFieldState draws by itself.
 */
internal fun imeActiveClause(composition: TextRange?, selection: TextRange): TextRange? {
    if (composition == null || selection.collapsed) return null
    val start = maxOf(composition.min, selection.min)
    val end = minOf(composition.max, selection.max)
    return if (start < end) TextRange(start, end) else null
}

/** Draws the composition for a [androidx.compose.foundation.text.input.TextFieldState] field. */
internal class ImeClauseHighlight(
    private val state: androidx.compose.foundation.text.input.TextFieldState,
    private val colors: ImeCompositionColors
) : androidx.compose.foundation.text.input.OutputTransformation {
    override fun androidx.compose.foundation.text.input.TextFieldBuffer.transformOutput() {
        val composition = state.composition ?: return
        val start = composition.min.coerceIn(0, length)
        val end = composition.max.coerceIn(start, length)
        if (start >= end) return
        val clause = imeActiveClause(TextRange(start, end), state.selection)
        if (clause == null) {
            addStyle(SpanStyle(color = colors.foreground, background = colors.background), start, end)
        } else {
            addStyle(SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline), start, end)
            addStyle(SpanStyle(color = colors.clauseForeground, background = colors.clauseBackground), clause.min, clause.max)
        }
    }
}

@Composable
internal fun rememberImeClauseHighlight(state: androidx.compose.foundation.text.input.TextFieldState): ImeClauseHighlight {
    val colors = imeCompositionColors(MaterialTheme.colorScheme.background)
    return androidx.compose.runtime.remember(state, colors) { ImeClauseHighlight(state, colors) }
}
