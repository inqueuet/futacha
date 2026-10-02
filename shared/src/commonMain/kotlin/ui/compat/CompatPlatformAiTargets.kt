package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.ai.FutachaAiCommand
import com.valoser.futacha.shared.ai.boardSelectorParameter
import com.valoser.futacha.shared.ai.catalogModeParameter
import com.valoser.futacha.shared.ai.draftCommentParameter
import com.valoser.futacha.shared.ai.draftEmailParameter
import com.valoser.futacha.shared.ai.draftNameParameter
import com.valoser.futacha.shared.ai.draftPasswordParameter
import com.valoser.futacha.shared.ai.draftSubjectParameter
import com.valoser.futacha.shared.ai.threadIdParameter
import com.valoser.futacha.shared.ai.threadUrlParameter
import com.valoser.futacha.shared.compat.CanonicalThreadUrl
import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.CompatCatalogSort
import com.valoser.futacha.shared.compat.CompatHost
import com.valoser.futacha.shared.compat.CompatReplyDraft
import com.valoser.futacha.shared.compat.CompatTab
import com.valoser.futacha.shared.compat.CompatibilityWorkspaceState
import com.valoser.futacha.shared.compat.canonicalizeBoardUrl
import com.valoser.futacha.shared.compat.canonicalizeThreadUrl
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.compat.compatTabKey
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.CancellationException
import kotlin.time.Clock

// Resolves the board, thread and catalog order named by a platform AI command
// against the compatibility workspace. Without a board selector the active
// tab's board, then the catalog being shown, is used.

internal fun resolvePlatformAiBoard(
    command: FutachaAiCommand,
    state: CompatibilityWorkspaceState,
    boards: List<CompatBoard>
): CompatBoard? {
    val requested = command.boardSelectorParameter()
    if (requested.isNullOrBlank()) {
        val active = state.activeTabKey?.let { key -> state.tabs.firstOrNull { it.key == key } }
        return active?.let { tab -> boards.firstOrNull { it.key == tab.boardKey } }
            ?: (state.host as? CompatHost.Catalog)?.let { host -> boards.firstOrNull { it.key == host.boardKey } }
    }
    val normalized = requested.trim().lowercase()
    val canonical = canonicalizeBoardUrl(requested)
    return boards.firstOrNull { board ->
        board.key.equals(requested, ignoreCase = true) ||
            board.name.equals(requested, ignoreCase = true) ||
            board.originalUrl.equals(requested, ignoreCase = true) ||
            (canonical != null && board.canonicalUrl.equals(canonical, ignoreCase = true))
    } ?: boards.firstOrNull { board ->
        board.name.lowercase().contains(normalized) || board.key.lowercase().contains(normalized)
    }
}

internal fun resolvePlatformAiThread(
    command: FutachaAiCommand,
    state: CompatibilityWorkspaceState,
    boards: List<CompatBoard>
): Pair<CanonicalThreadUrl, CompatBoard>? {
    val targetUrl = command.threadUrlParameter()
        ?: run {
            val board = resolvePlatformAiBoard(command, state, boards) ?: return@run null
            val number = command.threadIdParameter()?.takeIf { it.all(Char::isDigit) } ?: return@run null
            "${board.canonicalUrl.trimEnd('/')}/res/$number.htm"
        }
        ?: return null
    val parsed = canonicalizeThreadUrl(targetUrl) ?: return null
    val board = boards.firstOrNull { it.canonicalUrl.equals(parsed.canonicalBoardUrl, ignoreCase = true) }
        ?: CompatBoard(
            key = compatBoardKey(parsed.canonicalBoardUrl),
            name = parsed.boardPath.substringAfterLast('/'),
            canonicalUrl = parsed.canonicalBoardUrl,
            originalUrl = parsed.canonicalBoardUrl,
            sortOrder = boards.size
        )
    return parsed to board
}

/** Where a thread-scoped platform AI command must be delivered. */
internal sealed interface CompatPlatformAiThreadRoute {
    /** The active tab: deliver in place (showing the workspace first if another host is in front). */
    data class Active(val tab: CompatTab) : CompatPlatformAiThreadRoute
    /** A different thread that has to be opened (and recorded) first. */
    data class Open(val thread: CanonicalThreadUrl, val board: CompatBoard) : CompatPlatformAiThreadRoute
}

/**
 * Siri / Shortcuts / watch commands such as "一番上へ" usually carry no thread,
 * meaning the thread being shown. A named thread equal to the active tab must
 * not reopen it either: that would prepend the tab and rewrite its history time
 * on every watch tap. A named but unresolvable thread stays a failure instead
 * of silently acting on the active tab.
 */
internal fun resolvePlatformAiThreadRoute(
    command: FutachaAiCommand,
    state: CompatibilityWorkspaceState,
    boards: List<CompatBoard>
): CompatPlatformAiThreadRoute? {
    val active = state.activeTabKey?.let { key -> state.tabs.firstOrNull { it.key == key } }
    if (!platformAiCommandNamesThread(command)) {
        return active?.let { CompatPlatformAiThreadRoute.Active(it) }
    }
    val (thread, board) = resolvePlatformAiThread(command, state, boards) ?: return null
    return if (active != null && compatTabKey(thread.canonicalUrl) == active.key) {
        CompatPlatformAiThreadRoute.Active(active)
    } else {
        CompatPlatformAiThreadRoute.Open(thread, board)
    }
}

/**
 * A thread on a board not in the list, waiting for the "未登録の板" answer.
 * [aiCommand] is a thread command (already in its forwarded form) to hand to
 * the thread screen once the thread is open, so a confirmed command finishes
 * instead of only opening the thread (C4-5).
 */
internal data class CompatUnregisteredThreadRequest(
    val raw: String,
    val thread: CanonicalThreadUrl,
    val aiCommand: FutachaAiCommand? = null
)

/**
 * C4-6: the reply draft a "draft_reply" command leaves for the reply form,
 * like the modern mode: given values replace the stored ones, the rest (and
 * any attachment) stay. Null when the command carries no draft value.
 */
internal fun compatPlatformAiReplyDraft(
    command: FutachaAiCommand,
    tabKey: String,
    stored: CompatReplyDraft?,
    nowEpochMillis: Long
): CompatReplyDraft? {
    val name = command.draftNameParameter()
    val email = command.draftEmailParameter()
    val subject = command.draftSubjectParameter()
    val comment = command.draftCommentParameter()
    val deleteKey = command.draftPasswordParameter()
    if (listOf(name, email, subject, comment, deleteKey).all { it == null }) return null
    val base = stored ?: CompatReplyDraft(tabKey = tabKey, updatedAtEpochMillis = nowEpochMillis)
    return base.copy(
        name = name ?: base.name,
        email = email ?: base.email,
        subject = subject ?: base.subject,
        comment = comment ?: base.comment,
        deleteKey = deleteKey ?: base.deleteKey,
        updatedAtEpochMillis = nowEpochMillis
    )
}

/**
 * Stores [compatPlatformAiReplyDraft] for [tabKey] before the reply form opens
 * (the form reads the draft when it starts). A storage failure is logged; the
 * form still opens with the draft it has.
 */
internal suspend fun persistCompatPlatformAiReplyDraft(
    store: CompatibilityStore,
    tabKey: String,
    command: FutachaAiCommand
) {
    try {
        val draft = compatPlatformAiReplyDraft(
            command,
            tabKey,
            store.loadDraft(tabKey),
            Clock.System.now().toEpochMilliseconds()
        ) ?: return
        store.saveDraft(draft)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        Logger.w("CompatPlatformAi", "返信の下書きを保存できませんでした: ${failure.message.orEmpty()}")
    }
}

internal fun platformAiCommandNamesThread(command: FutachaAiCommand): Boolean =
    command.threadUrlParameter() != null || command.threadIdParameter() != null

internal fun resolvePlatformAiCatalogSort(command: FutachaAiCommand): CompatCatalogSort? {
    val raw = command.catalogModeParameter()?.trim()?.lowercase() ?: return null
    return CompatCatalogSort.entries.firstOrNull { sort ->
        sort.name.lowercase() == raw || sort.displayLabel.lowercase() == raw
    } ?: when (raw) {
        "catalog", "cat" -> CompatCatalogSort.CATALOG
        "new", "newest", "newest_first" -> CompatCatalogSort.NEW
        "old", "oldest", "oldest_first" -> CompatCatalogSort.OLD
        "many", "most", "most_replies" -> CompatCatalogSort.MANY
        "few", "least", "fewest_replies" -> CompatCatalogSort.FEW
        "lively", "momentum", "speed" -> CompatCatalogSort.LIVELY
        else -> null
    }
}

/**
 * Remembers the last platform AI command a thread screen started. The delivery
 * effect can restart before the command is consumed; a restarted run must
 * consume it instead of executing it again. Plain (non-snapshot) state: only
 * the effect reads it.
 */
internal class CompatPlatformAiDeliveryGuard {
    private var handled: FutachaAiCommand? = null

    fun claim(command: FutachaAiCommand): Boolean {
        if (handled === command) return false
        handled = command
        return true
    }
}
