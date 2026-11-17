package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.compositionLocalOf
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.network.isInqueuetArchiveUrl
import com.valoser.futacha.shared.ui.compat.toCompatUserMessage
import kotlinx.coroutines.CancellationException
import kotlin.time.Clock

internal val LocalDelPostAndNg = compositionLocalOf<((Post) -> Unit)?> { null }

/** A local post-only rule never hides every post by the same person. */
internal suspend fun registerReportedPostNg(store: CompatibilityStore, tabKey: String, postNo: String) {
    require(tabKey.isNotBlank() && tabKey != "*") { "対象のスレッドを確認できませんでした" }
    val kind = CompatNgKind.THREAD_POST_NO
    check(store.upsertNgRule(CompatNgRule(id = compatNgRuleId(kind, tabKey, postNo), kind = kind,
        scopeKey = tabKey, normalizedValue = postNo, createdAtEpochMillis = Clock.System.now().toEpochMilliseconds()))) {
        "このレスのNGを保存できませんでした"
    }
}

/** No wildcard and no implicit tab creation: the canonical thread URL is an independently validated post-only scope. */
internal suspend fun registerReportedThreadPostNg(
    store: CompatibilityStore, board: com.valoser.futacha.shared.model.BoardSummary, threadId: String, postNo: String
) {
    val boardUrl = canonicalizeBoardUrl(board.url) ?: error("対象の板を確認できませんでした")
    val url = canonicalizeThreadUrl("${boardUrl}res/$threadId.htm")?.canonicalUrl
        ?: error("対象のスレッドを確認できませんでした")
    require(postNo.isNotBlank() && postNo.all(Char::isDigit)) { "対象のレスを確認できませんでした" }
    store.importModernBoards(listOf(board))
    registerReportedPostNg(store, url, postNo)
}

internal suspend fun sendReportAndOptionalNg(
    alsoNg: Boolean,
    send: suspend () -> Unit,
    registerNg: suspend () -> Unit,
    reviewCompliance: Boolean = false
): String {
    suspend fun result(block: suspend () -> Unit): Throwable? = try { block(); null }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { error }
    val sendError = result(send)
    val registered = if (alsoNg) result(registerNg) == null else false
    val sendLabel = if (reviewCompliance) "通報" else "削除依頼"
    return buildString {
        if (sendError == null) {
            append("${sendLabel}を送信しました。")
        } else {
            // Keep the specific reason (404/410, timeout, offline) like the report-only path does.
            val base = "${sendLabel}を送信できませんでした"
            val reason = sendError.toCompatUserMessage(base)
            append(if (reason == base) "$base。" else "$base（$reason）。")
        }
        if (alsoNg) append(if (registered) "\nこのレスをNGに登録しました。" else "\nNGの登録に失敗しました。")
    }
}

/** The DEL confirmation's body; with the iOS review wording it says what the "通報" button sends (L137). */
internal fun futachaReportConfirmationBody(postNo: String, reviewCompliance: Boolean): String =
    if (reviewCompliance) "No.$postNo を不適切な投稿として、ふたば☆ちゃんねるの掲示板管理者へ通報します。"
    else "No.$postNo をふたば☆ちゃんねるの掲示板管理者へ削除依頼します。"

internal enum class DelAndNgGate { PROCEED, BUSY, ARCHIVE_READ_ONLY }

/** "DEL依頼＋NG" must pass the same gates as the plain DEL request (writable board, no other action running). */
internal fun resolveDelAndNgGate(boardUrl: String, actionInProgress: Boolean): DelAndNgGate = when {
    isInqueuetArchiveUrl(boardUrl) -> DelAndNgGate.ARCHIVE_READ_ONLY
    actionInProgress -> DelAndNgGate.BUSY
    else -> DelAndNgGate.PROCEED
}
