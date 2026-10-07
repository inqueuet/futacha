package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valoser.futacha.shared.ui.util.PlatformBackHandler

/** One action of the long-press sheet. A disabled one is shown with the reason it cannot run. */
internal data class FutaberPostAction(
    val id: String,
    val label: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

/** The actions of a long-pressed post that can run without sending anything. */
internal fun futaberPostActions(
    post: com.valoser.futacha.shared.model.Post,
    onQuote: (String) -> Unit,
    onCopy: (String) -> Unit
): List<FutaberPostAction> {
    val body = futaberQuoteBody(post)
    val lastLine = futaberQuoteLastLine(post)
    return listOf(
        FutaberPostAction("quote", "引用", enabled = body.isNotEmpty()) { onQuote(body) },
        FutaberPostAction("quote-number", "No.引用") { onQuote(futaberQuoteByNumber(post)) },
        FutaberPostAction("quote-last", "最終行引用", enabled = lastLine.isNotEmpty()) { onQuote(lastLine) },
        FutaberPostAction("copy", "コピー") { onCopy(futaberPostPlainText(post)) }
    )
}

/** The post's text as plain lines, for the clipboard. */
internal fun futaberPostPlainText(post: com.valoser.futacha.shared.model.Post): String =
    com.valoser.futacha.shared.ui.board.messageHtmlToPlainText(post.messageHtml)

/**
 * A card at the bottom with the post's number and its actions; tapping outside closes it, and so do the system's
 * Back and (on iOS) the left-edge swipe. A sheet with more actions than the screen is tall scrolls inside its card,
 * so the last action ("キャンセル") can always be reached.
 */
@Composable
internal fun FutaberPostActionSheet(
    title: String,
    actions: List<FutaberPostAction>,
    onDismiss: () -> Unit,
    /** True when the sheet covers the whole screen, so it must stay above the system's bottom bar. */
    avoidNavigationBar: Boolean = false
) {
    val colors = LocalFutaberColors.current
    // Registered when the sheet appears, so it is the newest handler: Back closes the sheet before the screen under it.
    PlatformBackHandler(onBack = onDismiss)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f))
                .clickable(onClickLabel = "閉じる", onClick = onDismiss)
                .testTag("futaber-action-scrim")
        )
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .then(if (avoidNavigationBar) Modifier.navigationBarsPadding() else Modifier)
                .padding(8.dp)
                .heightIn(max = maxHeight * FUTABER_SHEET_MAX_HEIGHT_FRACTION)
                .testTag("futaber-post-actions"),
            shape = FutaberShapes.sheet,
            color = colors.background,
            shadowElevation = 8.dp
        ) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    title, color = colors.meta, fontSize = 12.sp,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
                )
                actions.forEachIndexed { index, action ->
                    if (index > 0) HorizontalDivider(color = colors.separator, thickness = 0.5.dp)
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp)
                            .clickable(enabled = action.enabled, onClickLabel = action.label, onClick = action.onClick)
                            .padding(horizontal = 16.dp)
                            .testTag("futaber-action-${action.id}"),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            action.label,
                            color = if (action.enabled) colors.body else colors.meta,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/** The tallest the card may be: the share of the screen it covers before it scrolls. */
internal const val FUTABER_SHEET_MAX_HEIGHT_FRACTION = 0.85f
