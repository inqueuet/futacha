package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.mix.*
import com.valoser.futacha.shared.model.CatalogDisplayStyle
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.state.boardSettingKeys
import com.valoser.futacha.shared.state.resolveEffectiveWatchWordsForBoard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.time.Clock

/** A MIX's session cache lives as long as the process: coming back from a thread does not fetch again. */
internal val futachaMixSessionCache = MixCatalogSessionCache()

internal fun mixOrderLabel(kind: MixOrderKind): String = when (kind) {
    MixOrderKind.ALTERNATE -> "交互"
    MixOrderKind.NEW -> "新しい順"
    MixOrderKind.OLD -> "古い順"
    MixOrderKind.MANY -> "レスが多い順"
    MixOrderKind.FEW -> "レスが少ない順"
    MixOrderKind.MOMENTUM -> "勢い順"
    MixOrderKind.SO -> "そうだね順"
}

internal fun mixFailureText(
    failures: Map<String, MixFetchFailure>, skipped: Map<String, MixSkipReason>,
    boards: List<com.valoser.futacha.shared.model.BoardSummary> = emptyList()
): String? {
    fun name(url: String) = boards.firstOrNull {
        (com.valoser.futacha.shared.compat.storeCanonicalBoardUrlOf(it.url) ?: it.url) == url
    }?.name ?: url
    val parts = failures.map { (url, reason) ->
        val text = when (reason) {
            MixFetchFailure.TIMEOUT -> "時間切れ"
            MixFetchFailure.RATE_LIMITED -> "アクセス制限中"
            MixFetchFailure.SERVER_ERROR -> "サーバーのエラー"
            MixFetchFailure.NOT_FOUND -> "板が見つかりません"
            MixFetchFailure.NETWORK -> "通信エラー"
            MixFetchFailure.PARSE -> "応答を読み取れません"
            MixFetchFailure.UNKNOWN -> "読み込み失敗"
        }
        "${name(url)}：$text"
    } + skipped.map { (url, reason) ->
        val text = when (reason) {
            MixSkipReason.NOT_REGISTERED -> "板が未登録です"
            MixSkipReason.UNSUPPORTED_SITE -> "対応していない板です"
            MixSkipReason.AIMOGE_DISABLED -> "あいもげは無効です"
            MixSkipReason.TUTORIAL_BOARD -> "サンプル板は使えません"
            MixSkipReason.DUPLICATE -> "重複しています"
            MixSkipReason.SITE_PAUSED -> "休止中"
        }
        "${name(url)}：$text"
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString("\n")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FutachaMixScreen(
    mix: ResolvedMix,
    repository: BoardRepository,
    store: CompatibilityStore,
    onBack: () -> Unit,
    onOpen: (MixEntry) -> Unit,
    registeredBoards: List<com.valoser.futacha.shared.model.BoardSummary> = mix.boards,
    /** Switch to another MIX (or a newly created one); null hides the switcher. */
    onSwitch: ((String) -> Unit)? = null,
    /** Reads each row's own board watch words (the board's, else the global ones). */
    stateStore: com.valoser.futacha.shared.state.AppStateStore? = null,
    /** Lets the あいもげ pause the background keeps be read before a fetch. */
    fileSystem: com.valoser.futacha.shared.util.FileSystem? = null,
    /** Needed for the picture-hash NG; without it that NG is not applied. */
    httpClient: io.ktor.client.HttpClient? = null,
    projectEntries: suspend (List<MixEntry>) -> List<MixEntry> = { it },
    catalogWordNgEnabled: Boolean = true,
    modePolicy: MixModePolicy = MixModePolicy.FUTACHA
) {
    var editing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val definition = mix.definition
    val confirmMixMembers = rememberMixR18Confirmation(store)
    var creating by remember(definition.id) { mutableStateOf(false) }
    val order = definition.order
    var message by remember(definition.id) { mutableStateOf<String?>(null) }
    var orderMenu by remember(definition.id) { mutableStateOf(false) }
    val globalWatchWords by (stateStore?.observedWatchWords?.collectPreferenceAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList<String>()) })
    val boardWatchWordMap by (stateStore?.observedBoardWatchWords?.collectPreferenceAsState(initial = null) ?: remember { mutableStateOf<Map<String, List<String>>?>(null) })
    val catalogNgWords by (stateStore?.observedCatalogNgWords?.collectPreferenceAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList<String>()) })
    val compatPrefs by store.preferences.collectAsState(initial = emptyMap<String, String>())
    var rowMenu by remember { mutableStateOf<MixEntry?>(null) }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val openUrl = com.valoser.futacha.shared.util.rememberUrlLauncher()
    var switchMenu by remember { mutableStateOf(false) }
    val allMixes by produceState<List<MixDefinition>>(emptyList(), store) {
        store.preferences.collect { prefs ->
            value = when (val r = decodeMixDefinitions(prefs[MIX_DEFINITIONS_KEY])) {
                is MixLoadResult.Ok -> r.mixes
                is MixLoadResult.ReadOnly -> r.mixes
            }
        }
    }
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val session = remember(repository, store, fileSystem, scope, modePolicy) {
        val fetcher = MixCatalogFetcher(
            fetchPage = { board, mode -> fetchMixCatalogPageForMode(repository, store, board.url, mode, modePolicy) },
            loadPause = {},
            nowMillis = { Clock.System.now().toEpochMilliseconds() },
            createdAtOf = { it.threadCreatedAtEpochMillis }
        )
        MixCatalogSession(scope, futachaMixSessionCache, { Clock.System.now().toEpochMilliseconds() },
            resolve = { def, boards ->
                resolveMixForFetch(def, boards, com.valoser.futacha.shared.compat.AimogeAvailability.sitesEnabled,
                    { com.valoser.futacha.shared.service.SiteRefreshPause.load(fileSystem) },
                    { com.valoser.futacha.shared.service.SiteRefreshPause.sitePausedNow() },
                    { com.valoser.futacha.shared.service.SiteRefreshPause.boardPausedNow(it) })
            }, fetch = fetcher::fetch, cacheNamespace = modePolicy.name,
            isEnabled = { com.valoser.futacha.shared.compat.MixAvailability.enabled })
    }
    val sessionState by session.state.collectAsState()
    val result = sessionState.result
    val running = sessionState.running
    LaunchedEffect(session, definition, registeredBoards) { session.show(definition, registeredBoards) }
    DisposableEffect(session) { onDispose { session.close() } }

    rowMenu?.let { entry ->
        val boardUrl = entry.board.url
        val canonical = com.valoser.futacha.shared.compat.storeCanonicalBoardUrlOf(boardUrl) ?: boardUrl
        val boardKey = com.valoser.futacha.shared.compat.compatBoardKey(canonical)
        val threadUrl = futachaCatalogThreadUrl(boardUrl, entry.item)
        com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow {
            AlertDialog(
                onDismissRequest = { rowMenu = null },
                title = { Text(entry.item.title?.take(40).orEmpty().ifBlank { "スレッド" }, maxLines = 1) },
                text = {
                    Column {
                        TextButton(onClick = { rowMenu = null; clipboard.setText(androidx.compose.ui.text.AnnotatedString(threadUrl)) }) { Text("URLをコピー") }
                        TextButton(onClick = { rowMenu = null; openUrl(threadUrl) }) { Text("ブラウザで開く") }
                        TextButton(onClick = {
                            rowMenu = null
                            scope.launch { runCatching { addFutachaCatalogTab(store, entry.board, boardKey, entry.item, entry.replyCountOrNull) }.onFailure { message = it.message } }
                        }) { Text("タブに追加") }
                        TextButton(onClick = {
                            rowMenu = null
                            scope.launch { runCatching { registerFutachaCatalogNgThread(store, boardKey, entry.item) }.onFailure { message = it.message } }
                        }) { Text("スレッドをNGに登録") }
                    }
                },
                confirmButton = { TextButton(onClick = { rowMenu = null }) { Text("閉じる") } }
            )
        }
    }

    if (editing) MixEditDialog(definition, registeredBoards, store, onDismiss = { editing = false }, onDeleted = { editing = false; onBack() })
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(definition.name, maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "戻る") } },
                actions = {
                    if (onSwitch != null) {
                        TextButton(onClick = { switchMenu = true }) { Text("切替") }
                        DropdownMenu(expanded = switchMenu, onDismissRequest = { switchMenu = false }) {
                            allMixes.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(m.name + if (m.id == definition.id) "（表示中）" else "") },
                                    onClick = { switchMenu = false; if (m.id != definition.id) onSwitch(m.id) }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("新しいまとめを作る") },
                                onClick = {
                                    switchMenu = false
                                    if (!creating) scope.launch {
                                        creating = true
                                        try {
                                            val members = registeredBoards.map { it.url }.filter { !it.contains("example.com") }.take(MAX_MIX_MEMBERS)
                                            if (confirmMixMembers(members)) {
                                                when (val r = MixStore(store).create("まとめ${allMixes.size + 1}", members, MixOrderKind.ALTERNATE, Clock.System.now().toEpochMilliseconds())) {
                                                    MixWriteResult.Ok -> (MixStore(store).load() as? MixLoadResult.Ok)?.mixes?.lastOrNull()?.let { onSwitch(it.id) }
                                                    is MixWriteResult.Refused -> message = r.message
                                                }
                                            }
                                        } finally { creating = false }
                                    }
                                }
                            )
                        }
                    }
                    IconButton(onClick = { orderMenu = true }) { Icon(Icons.Rounded.SwapVert, "並び順：${mixOrderLabel(order)}") }
                    DropdownMenu(expanded = orderMenu, onDismissRequest = { orderMenu = false }) {
                        MixOrderKind.entries.forEach { kind ->
                            DropdownMenuItem(
                                text = { Text(mixOrderLabel(kind) + if (kind == order) "（選択中）" else "") },
                                onClick = {
                                    orderMenu = false
                                    if (kind != order) {
                                        scope.launch {
                                            when (val saved = MixStore(store).update(definition.id) { it.copy(order = kind) }) {
                                                MixWriteResult.Ok -> Unit
                                                is MixWriteResult.Refused -> message = saved.message
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                    IconButton(onClick = { session.refresh() }) { Icon(Icons.Rounded.Refresh, "更新") }
                    TextButton(onClick = { editing = true }) { Text("編集") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            val resultMessage = result?.let { mixFailureText(it.failures, it.skipped, registeredBoards) }
            val limitMessage = result?.cutByLimit?.takeIf { it.isNotEmpty() }?.let { cut ->
                val names = cut.map { url -> registeredBoards.firstOrNull {
                    (com.valoser.futacha.shared.compat.storeCanonicalBoardUrlOf(it.url) ?: it.url) == url
                }?.name ?: url }
                "${names.joinToString("、")}：先頭80件まで表示しています"
            }
            listOfNotNull(message, sessionState.message, resultMessage, limitMessage).distinct().joinToString("\n").takeIf { it.isNotEmpty() }?.let { Text(it, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) }
            val allRules by store.ngRules.collectAsState(initial = emptyList<com.valoser.futacha.shared.compat.CompatNgRule>())
            var displayedEntries by remember(definition.id) { mutableStateOf(emptyList<MixEntry>()) }
            val titles = rememberMixHeadMetadataTitles(result?.entries.orEmpty(), repository,
                CatalogDisplayStyle.Grid, gridState, listState, modePolicy.completeTitles,
                visibleEntries = displayedEntries)
            val resolvedTitles = titles.toMap()
            val ngEnabled = mixCatalogNgEnabled(compatPrefs, modePolicy)
            val projectionPreferences = mapOf(
                "compat.catalog.NG機能" to compatPrefs["compat.catalog.NG機能"],
                com.valoser.futacha.shared.ui.ftutter.FtutterKeys.NG_LIST to compatPrefs[com.valoser.futacha.shared.ui.ftutter.FtutterKeys.NG_LIST],
                com.valoser.futacha.shared.ui.compat.COMPAT_COMMON_NG_APPLY_STORAGE_KEY to compatPrefs[com.valoser.futacha.shared.ui.compat.COMPAT_COMMON_NG_APPLY_STORAGE_KEY]
            ).mapNotNull { (key, value) -> value?.let { key to it } }.toMap()
            val ruleFiltered by produceState<List<MixEntry>>(emptyList(), result?.entries, projectEntries,
                allRules, catalogWordNgEnabled, catalogNgWords, projectionPreferences, modePolicy, resolvedTitles) {
                value = emptyList()
                val projected = calculateMixCatalogProjection(result?.entries.orEmpty(), allRules,
                    catalogNgWords, projectionPreferences, modePolicy, catalogWordNgEnabled, projectEntries, resolvedTitles = resolvedTitles)
                currentCoroutineContext().ensureActive()
                value = projected
            }
            val phashThreshold = mixImageNgThreshold(compatPrefs, modePolicy)
            val phashRules = remember(allRules, ngEnabled) { if (!ngEnabled) emptyList() else allRules.filter { it.kind == com.valoser.futacha.shared.compat.CompatNgKind.CATALOG_IMAGE_PHASH } }
            val cachedImageLoader = com.valoser.futacha.shared.ui.image.LocalFutachaCatalogImageLoader.current
            // At most 256 pictures for the whole MIX, three at a time, 15 s (the same bounds as one board's catalog).
            val phashes by produceState(emptyMap<String, String>(), ruleFiltered, phashRules, httpClient, definition.members) {
                val client = httpClient
                if (phashRules.isEmpty() || client == null) { value = emptyMap(); return@produceState }
                val candidates = planMixImageNgCandidates(ruleFiltered, phashRules, definition.members)
                val stored = com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation {
                    store.loadImagePhashes(candidates.map { com.valoser.futacha.shared.compat.compatImagePhashCachePreferenceKey(it.second) })
                }.getOrDefault(emptyMap())
                val cached = candidates.mapNotNull { (id, url) ->
                    stored[com.valoser.futacha.shared.compat.compatImagePhashCachePreferenceKey(url)]
                        ?.takeIf { h -> com.valoser.futacha.shared.compat.isValidCompatImagePhash(h) }?.let { id to it }
                }.toMap()
                value = cached
                val missing = candidates.filterNot { it.first in cached }
                if (missing.isEmpty()) return@produceState
                val byKey = ruleFiltered.associateBy { it.key.asString() }
                val computed = com.valoser.futacha.shared.ui.compat.collectCompatImagePhashes(
                    client, missing, cachedImageLoader = cachedImageLoader,
                    fallbackImageUrl = { id -> byKey[id]?.item?.thumbnailUrl },
                    resolveMissingUrl = { id -> resolveMixMissingImageUrl(byKey[id], repository::fetchOpImageUrl) },
                    onPartial = { partial -> value = cached + partial }
                )
                value = cached + computed
                com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation {
                    val toSave = missing.mapNotNull { (id, url) ->
                        computed[id]?.let { com.valoser.futacha.shared.compat.compatImagePhashCachePreferenceKey(url) to it }
                    }.toMap()
                    if (toSave.isNotEmpty()) store.saveImagePhashes(toSave)
                }
            }
            val entries by produceState<List<MixEntry>>(emptyList(), ruleFiltered, phashRules, phashes, phashThreshold) {
                value = emptyList()
                val filtered = kotlinx.coroutines.withContext(com.valoser.futacha.shared.util.AppDispatchers.parsing) {
                    val context = currentCoroutineContext()
                    mixEntriesWithoutPhashNg(ruleFiltered, phashRules, phashes, phashThreshold) { context.ensureActive() }
                }
                currentCoroutineContext().ensureActive()
                value = filtered
            }
            SideEffect { displayedEntries = entries }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when {
                    result == null && running -> CircularProgressIndicator()
                    entries.isEmpty() -> Text("表示できるスレッドがありません")
                    else -> {
                        val items = entries.map { it.item }
                        val now = rememberMixDisplayTime()
                        androidx.compose.runtime.CompositionLocalProvider(
                            LocalFutachaCatalogLongPress provides { item: com.valoser.futacha.shared.model.CatalogItem ->
                                entries.firstOrNull { it.item === item }?.takeIf { com.valoser.futacha.shared.compat.aimogeBoardShapeOf(it.board.url) == null }?.let { rowMenu = it }
                            }
                        ) { CatalogGrid(
                            items = items, embeddedHtml = emptyList(), board = null, watchWords = emptyList(),
                            onThreadSelected = { item ->
                                // The title the head metadata gave replaces a cut catalog title in the tab and history (C-M13).
                                entries.firstOrNull { it.item === item }?.let { e ->
                                    onOpen(mixEntryWithHeadTitle(e, titles))
                                }
                            },
                            onRefresh = { session.refresh() }, isRefreshing = running, gridColumns = 3,
                            gridState = gridState, resolvedHeadTitles = titles, resolveHeadMetadata = modePolicy.completeTitles,
                            mixEntries = entries, mixNowMillis = now,
                            mixWatchWordsOf = { e ->
                                val map = boardWatchWordMap
                                if (stateStore == null || modePolicy == MixModePolicy.COMPAT) {
                                    val canonical = com.valoser.futacha.shared.compat.storeCanonicalBoardUrlOf(e.board.url) ?: e.board.url
                                    com.valoser.futacha.shared.compat.compatWatchWordsForBoard(compatPrefs, com.valoser.futacha.shared.compat.compatBoardKey(canonical))
                                } else if (map == null) emptyList() else resolveEffectiveWatchWordsForBoard(
                                    globalWatchWords = globalWatchWords, boardWatchWords = map,
                                    keys = boardSettingKeys(e.board.id, e.board.url)
                                )
                            }
                        ) }
                    }
                }
            }
        }
    }
}

/** Drops every row its own board's catalog NG rules hide (threads and words); the picture-hash NG is not applied. */
internal fun mixEntriesWithoutNg(
    entries: List<MixEntry>,
    rules: List<com.valoser.futacha.shared.compat.CompatNgRule>,
    catalogWordNgEnabled: Boolean = true,
    ensureActive: () -> Unit = {}
): List<MixEntry> {
    if (rules.isEmpty()) return entries
    val indexes = HashMap<String, com.valoser.futacha.shared.compat.CompatCatalogRuleIndex>()
    return entries.filterNot { e ->
        ensureActive()
        val canonical = com.valoser.futacha.shared.compat.storeCanonicalBoardUrlOf(e.board.url) ?: e.board.url
        val key = com.valoser.futacha.shared.compat.compatBoardKey(canonical)
        indexes.getOrPut(key) {
            com.valoser.futacha.shared.compat.buildCompatCatalogRuleIndex(
                com.valoser.futacha.shared.compat.compatCatalogRulesForBoard(rules, key).filter { rule ->
                    catalogWordNgEnabled || (rule.kind != com.valoser.futacha.shared.compat.CompatNgKind.CATALOG_WORD &&
                        rule.kind != com.valoser.futacha.shared.compat.CompatNgKind.CATALOG_IGNORE)
                }
            )
        }.hides(e.item)
    }
}

/** Drops rows whose picture hash is close to a picture-NG rule of the row's own board (or a global one). */
internal fun mixEntriesWithoutPhashNg(
    entries: List<com.valoser.futacha.shared.mix.MixEntry>,
    phashRules: List<com.valoser.futacha.shared.compat.CompatNgRule>,
    phashes: Map<String, String>,
    threshold: Int = com.valoser.futacha.shared.compat.CompatImagePhash.DEFAULT_THRESHOLD,
    ensureActive: () -> Unit = {}
): List<com.valoser.futacha.shared.mix.MixEntry> {
    if (phashes.isEmpty() || phashRules.isEmpty()) return entries
    return entries.filterNot { e ->
        ensureActive()
        val hash = phashes[e.key.asString()] ?: return@filterNot false
        val canonical = com.valoser.futacha.shared.compat.storeCanonicalBoardUrlOf(e.board.url) ?: e.board.url
        val boardKey = com.valoser.futacha.shared.compat.compatBoardKey(canonical)
        phashRules.any {
            (it.scopeKey == boardKey || it.scopeKey == "*") &&
                com.valoser.futacha.shared.compat.CompatImagePhash.isSimilar(hash, it.normalizedValue, threshold)
        }
    }
}

/** The row to open: carries the title read from the thread's head metadata, when there is one. */
internal fun mixEntryWithHeadTitle(entry: com.valoser.futacha.shared.mix.MixEntry, titles: Map<String, String>): com.valoser.futacha.shared.mix.MixEntry =
    titles[entry.key.asString()]?.takeIf { it.isNotBlank() }?.let { t -> entry.copy(item = entry.item.copy(title = t)) } ?: entry
