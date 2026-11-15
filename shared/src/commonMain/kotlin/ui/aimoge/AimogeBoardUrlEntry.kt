package com.valoser.futacha.shared.ui.aimoge

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.valoser.futacha.shared.compat.AimogeAvailability
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.compat.aimogeBoardShapeOf
import com.valoser.futacha.shared.network.aimoge.createAimogeHttpClient
import com.valoser.futacha.shared.repo.aimoge.AimogeBoardInfo
import com.valoser.futacha.shared.repo.aimoge.AimogeBoardRepository
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** The reason shown when an あいもげ board URL is entered while あいもげ is off. */
const val AIMOGE_BOARD_URL_DISABLED_MESSAGE = "あいもげの板は、この版では使えません"

private val aimogeAggregateRegex = Regex("^https?://(www\\.)?nijiurachan\\.net(/(b|mix))?/?([?#].*)?$", RegexOption.IGNORE_CASE)

internal const val AIMOGE_R18_CONFIRM_TEXT =
    "R18のスレを立てられる板です。成人向けの内容が表示されることがあります。追加しますか？"

/** What an entered board URL is, seen from あいもげ. */
sealed interface AimogeBoardUrlInput {
    /** Not an あいもげ address: the mode's Futaba checks decide. */
    data object NotAimoge : AimogeBoardUrlInput
    /** Top / overall / mix URL: choose individual boards before registering any. */
    data object SelectBoards : AimogeBoardUrlInput
    /** An あいもげ address that cannot be added while あいもげ is off. */
    data class Refused(val message: String) : AimogeBoardUrlInput
    /** An あいもげ board, in its canonical form `https://nijiurachan.net/b/{slug}/`. */
    data class Board(val url: String, val slug: String) : AimogeBoardUrlInput
}

private val catalogSuffix = Regex("/catalog/?$", RegexOption.IGNORE_CASE)

/**
 * The canonical あいもげ board address of what the user typed in 「板を追加」, without reading the flag: a board,
 * thread or catalog address, with or without the trailing `/`, over http or https. Anything else is null.
 */
fun aimogeBoardUrlOfInput(raw: String): String? {
    var text = raw.trim()
    if (text.isEmpty() || text.length > 2_000) return null
    if (text.startsWith("http://", ignoreCase = true)) text = "https://" + text.substring(7)
    text = text.substringBefore('#').substringBefore('?')
    text = text.replace(catalogSuffix, "/")
    return aimogeBoardShapeOf(text)
}

/** The entered [raw] URL as an あいもげ board, the refusal while あいもげ is off, or [AimogeBoardUrlInput.NotAimoge]. */
fun classifyAimogeBoardUrlInput(raw: String): AimogeBoardUrlInput {
    val url = aimogeBoardUrlOfInput(raw) ?: return if (aimogeAggregateRegex.matches(raw.trim())) {
        if (AimogeAvailability.sitesEnabled) AimogeBoardUrlInput.SelectBoards
        else AimogeBoardUrlInput.Refused(AIMOGE_BOARD_URL_DISABLED_MESSAGE)
    } else AimogeBoardUrlInput.NotAimoge
    if (!AimogeAvailability.sitesEnabled) return AimogeBoardUrlInput.Refused(AIMOGE_BOARD_URL_DISABLED_MESSAGE)
    return AimogeBoardUrlInput.Board(url, url.removeSuffix("/").substringAfterLast('/'))
}

/** A board read from `/boards`: its name and whether R18 threads may be started on it. */
data class AimogeBoardLookup(val url: String, val name: String, val r18: Boolean)

/**
 * The name and R18 flag of the board [url] (canonical) from `/boards`; the slug and no R18 when the list cannot be
 * read or does not hold the board. The icons in the list are kept, as the list dialog did.
 */
suspend fun lookupAimogeBoard(
    url: String,
    loadBoards: (suspend () -> List<AimogeBoardInfo>)? = null,
    store: CompatibilityStore? = null
): AimogeBoardLookup {
    val slug = url.removeSuffix("/").substringAfterLast('/')
    val list = try {
        loadBoards?.invoke() ?: run {
            val repository = AimogeBoardRepository.owningClient({ createAimogeHttpClient(appVersion = "") })
            try { loadAimogeBoardsOffMain(repository) } finally { repository.close() }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        return AimogeBoardLookup(url, slug, r18 = false)
    }
    runCatching { AimogeBoardIcons.learnAndSave(store, list) }
    val info = list.firstOrNull { it.url == url } ?: return AimogeBoardLookup(url, slug, r18 = false)
    return AimogeBoardLookup(url, info.name.ifBlank { slug }, info.r18)
}

/**
 * Adds an あいもげ board entered as a URL: reads its name from `/boards` (the slug when that fails), asks once for an
 * R18 board, then calls `onAdd(name, url)`. [typedName] (ふたちゃ・としあき(仮)'s name field) wins when not blank.
 */
@Stable
class AimogeBoardUrlAdder internal constructor(
    private val scope: CoroutineScope,
    private val loadBoards: (suspend () -> List<AimogeBoardInfo>)?,
    private val store: CompatibilityStore?
) {
    var busy by mutableStateOf(false)
        private set
    internal var askR18 by mutableStateOf<AimogeBoardLookup?>(null)
    internal var onAdd: (String, String) -> Unit = { _, _ -> }
    internal var selectingBoards by mutableStateOf(false)

    fun selectBoards() {
        if (AimogeAvailability.sitesEnabled && !busy) selectingBoards = true
    }

    fun start(url: String, typedName: String? = null) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val found = lookupAimogeBoard(url, loadBoards, store)
                val named = typedName?.trim()?.takeIf(String::isNotEmpty)?.let { found.copy(name = it) } ?: found
                if (named.r18) askR18 = named else onAdd(named.name, named.url)
            } finally {
                busy = false
            }
        }
    }
}

@Composable
fun rememberAimogeBoardUrlAdder(
    onAdd: (name: String, url: String) -> Unit,
    loadBoards: (suspend () -> List<AimogeBoardInfo>)? = null,
    store: CompatibilityStore? = null,
    registeredUrls: Set<String> = emptySet(),
    onAddBoards: ((List<AimogeBoardInfo>) -> Unit)? = null
): AimogeBoardUrlAdder {
    val scope = rememberCoroutineScope()
    val latest by rememberUpdatedState(onAdd)
    val adder = remember(loadBoards, store) { AimogeBoardUrlAdder(scope, loadBoards, store) }
    adder.onAdd = { name, url -> latest(name, url) }
    if (adder.selectingBoards) AimogeAddBoardsDialog(
        registeredUrls = registeredUrls,
        onAdd = { boards ->
            adder.selectingBoards = false
            if (onAddBoards != null) onAddBoards(boards) else boards.forEach { latest(it.name, it.url) }
        },
        onDismiss = { adder.selectingBoards = false },
        loadBoards = loadBoards,
        store = store
    )
    adder.askR18?.let { board ->
        FutachaAppLockAwareWindow { AlertDialog(
            onDismissRequest = { adder.askR18 = null },
            title = { Text("R18の板") },
            text = { Text(AIMOGE_R18_CONFIRM_TEXT + "\n\n" + board.name) },
            confirmButton = {
                TextButton(
                    onClick = { adder.askR18 = null; latest(board.name, board.url) },
                    modifier = Modifier.testTag("aimoge-r18-confirm")
                ) { Text("追加する") }
            },
            dismissButton = {
                TextButton(onClick = { adder.askR18 = null }, modifier = Modifier.testTag("aimoge-r18-cancel")) { Text("キャンセル") }
            }
        ) }
    }
    return adder
}

/**
 * The picker's own repository makes its HTTP client lazily on the first request; that request runs on the IO
 * dispatcher so the client is never built on the main thread (audit 2026-11-11 L4).
 */
internal suspend fun loadAimogeBoardsOffMain(repository: AimogeBoardRepository): List<AimogeBoardInfo> =
    kotlinx.coroutines.withContext(com.valoser.futacha.shared.util.AppDispatchers.io) { repository.boards() }
