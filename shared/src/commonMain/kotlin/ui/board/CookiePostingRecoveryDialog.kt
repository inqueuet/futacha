package com.valoser.futacha.shared.ui.board

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.valoser.futacha.shared.repository.CookieRepository

private val postingCookieNames = setOf("posttime", "ptmt")

internal data class CookiePostingRecoveryGuidance(
    val title: String,
    val message: String
)

internal fun buildCookiePostingRecoveryGuidance(
    hasAnyCookies: Boolean,
    hasPostingCookiesForBoard: Boolean
): CookiePostingRecoveryGuidance {
    return when {
        !hasAnyCookies -> CookiePostingRecoveryGuidance(
            title = "書き込み用Cookieがありません",
            message = "まだ Cookie が保存されていません。書き込みを試すと、失敗しても Cookie だけ保存される場合があります。Cookie 画面で保存状態を確認し、保存されていれば Cookie を削除せずにもう一度投稿してください。"
        )

        !hasPostingCookiesForBoard -> CookiePostingRecoveryGuidance(
            title = "この板の書き込み用Cookieがありません",
            message = "Cookie はありますが、この板で使う書き込み用 Cookie が見つかりません。この板で一度書き込みを試すと、失敗しても Cookie だけ保存される場合があります。保存されていれば Cookie を削除せずにもう一度投稿してください。"
        )

        else -> CookiePostingRecoveryGuidance(
            title = "もう一度投稿してください",
            message = "投稿用 Cookie は保存されています。Cookie を削除せずにもう一度投稿してください。サーバーが残り秒数を返した場合は、その時間まで待ってから再試行してください。"
        )
    }
}

internal fun buildCookiePostingRecoveryFallbackGuidance(): CookiePostingRecoveryGuidance {
    return CookiePostingRecoveryGuidance(
        title = "Cookieを確認してください",
        message = "Cookie の状態を確認できませんでした。Cookie 画面で posttime または ptmt が保存されていれば、削除せずにもう一度投稿してください。残り秒数が表示された場合は、その時間まで待ってください。"
    )
}

private sealed interface CookiePostingRecoveryDialogState {
    data object Loading : CookiePostingRecoveryDialogState
    data class Ready(val guidance: CookiePostingRecoveryGuidance) : CookiePostingRecoveryDialogState
}

@Composable
internal fun CookiePostingRecoveryDialog(
    boardUrl: String?,
    repository: CookieRepository,
    onDismiss: () -> Unit,
    onOpenCookieManager: () -> Unit
) {
    var state by remember(boardUrl, repository) {
        mutableStateOf<CookiePostingRecoveryDialogState>(CookiePostingRecoveryDialogState.Loading)
    }

    LaunchedEffect(boardUrl, repository) {
        state = CookiePostingRecoveryDialogState.Loading
        val guidance = runCatching {
            val hasAnyCookies = repository.listCookies().isNotEmpty()
            val hasPostingCookiesForBoard = boardUrl?.takeIf { it.isNotBlank() }?.let { url ->
                repository.hasValidCookieFor(url, preferredNames = postingCookieNames)
            } ?: false
            buildCookiePostingRecoveryGuidance(
                hasAnyCookies = hasAnyCookies,
                hasPostingCookiesForBoard = hasPostingCookiesForBoard
            )
        }.getOrElse {
            buildCookiePostingRecoveryFallbackGuidance()
        }
        state = CookiePostingRecoveryDialogState.Ready(guidance)
    }

    val guidance = when (val currentState = state) {
        CookiePostingRecoveryDialogState.Loading -> CookiePostingRecoveryGuidance(
            title = "Cookieを確認しています",
            message = "保存済み Cookie の状態を確認しています。"
        )

        is CookiePostingRecoveryDialogState.Ready -> currentState.guidance
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(guidance.title) },
        text = { Text(guidance.message) },
        confirmButton = {
            TextButton(
                onClick = onOpenCookieManager,
                enabled = state !is CookiePostingRecoveryDialogState.Loading
            ) {
                Text("Cookieを開く")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("閉じる")
            }
        }
    )
}
