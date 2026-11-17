package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.*
import com.valoser.futacha.shared.compat.AimogeAvailability
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.mix.*
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.repo.BoardRepository

internal val LocalHasExistingMix = staticCompositionLocalOf { false }

/** Existing definitions stay accessible even after their boards have been removed. */
internal fun mixOfferable(boardUrls: List<String>, definitions: String? = null): Boolean =
    mixDefinitionsOnRead(definitions).isNotEmpty() || boardUrls.count { !it.contains("example.com") } >= 2

internal fun mixDefinitionsOnRead(raw: String?): List<MixDefinition> = when (val loaded = decodeMixDefinitions(raw)) {
    is MixLoadResult.Ok -> loaded.mixes
    is MixLoadResult.ReadOnly -> loaded.mixes
}

internal sealed interface MixOpenResult {
    data class Opened(val id: String) : MixOpenResult
    data object Cancelled : MixOpenResult
    data class Refused(val message: String) : MixOpenResult

    fun idOrReport(report: (String) -> Unit): String? = when (this) {
        is Opened -> id
        Cancelled -> null
        is Refused -> { report(message); null }
    }
}

internal suspend fun firstMixIdOrCreate(store: CompatibilityStore, boardUrls: List<String>, nowMillis: Long, confirmMembers: suspend (List<String>) -> Boolean): MixOpenResult {
    val mixStore = MixStore(store)
    val loaded = mixStore.load()
    val mixes = when (loaded) {
        is MixLoadResult.Ok -> loaded.mixes
        is MixLoadResult.ReadOnly -> loaded.mixes
    }
    mixes.firstOrNull()?.let { return MixOpenResult.Opened(it.id) }
    if (loaded is MixLoadResult.ReadOnly) return MixOpenResult.Refused("まとめカタログを読み込めません：${loaded.reason}")
    val members = boardUrls.filter { !it.contains("example.com") }.take(MAX_MIX_MEMBERS)
    if (members.size < 2) return MixOpenResult.Refused("板を2つ以上登録してください")
    if (!confirmMembers(members)) return MixOpenResult.Cancelled
    return when (val created = mixStore.create("まとめ", members, MixOrderKind.ALTERNATE, nowMillis)) {
        is MixWriteResult.Refused -> MixOpenResult.Refused(created.message)
        MixWriteResult.Ok -> (mixStore.load() as? MixLoadResult.Ok)?.mixes?.firstOrNull()?.let { MixOpenResult.Opened(it.id) }
            ?: MixOpenResult.Refused("まとめカタログを読み込めませんでした")
    }
}

/** The MIX screen for a mode that keeps its own navigation: reads the definition, resolves it, leaves when it is gone. */
@Composable
internal fun MixScreenHost(
    mixId: String,
    store: CompatibilityStore,
    repository: BoardRepository,
    boards: List<BoardSummary>,
    onExit: () -> Unit,
    onOpen: (MixEntry) -> Unit,
    onSwitch: (String) -> Unit,
    stateHolder: androidx.compose.runtime.saveable.SaveableStateHolder,
    fileSystem: com.valoser.futacha.shared.util.FileSystem? = null,
    /** Gives each row its board's watch words; null leaves them empty. */
    stateStore: com.valoser.futacha.shared.state.AppStateStore? = null,
    httpClient: io.ktor.client.HttpClient? = null,
    projectEntries: suspend (List<MixEntry>) -> List<MixEntry> = { it },
    catalogWordNgEnabled: Boolean = true,
    modePolicy: MixModePolicy = MixModePolicy.FUTACHA,
    gridColumns: MixGridColumns = MixGridColumns.DEFAULT,
    onUnavailable: (String) -> Unit = {}
) {
    if (!com.valoser.futacha.shared.compat.MixAvailability.enabled) {
        LaunchedEffect(mixId) { onExit() }
        return
    }
    val definitions by produceState<List<MixDefinition>?>(null, store) {
        kotlinx.coroutines.flow.combine(store.isLoaded, store.preferences) { loaded, prefs ->
            if (!loaded) null else mixDefinitionsOnRead(prefs[MIX_DEFINITIONS_KEY])
        }.collect { value = it }
    }
    val definition = definitions?.firstOrNull { it.id == mixId }
    if (definitions != null && definition == null) {
        LaunchedEffect(mixId) { onExit(); onUnavailable("まとめカタログが見つからないため、閉じました") }
        return
    }
    if (definition == null) return
    val resolved = remember(definition, boards) {
        resolveMixMembers(definition, boards, AimogeAvailability.sitesEnabled, { com.valoser.futacha.shared.service.SiteRefreshPause.sitePausedNow() },
            { url -> com.valoser.futacha.shared.service.SiteRefreshPause.boardPausedNow(com.valoser.futacha.shared.compat.siteCanonicalBoardUrlOf(url) ?: url) })
    }
    com.valoser.futacha.shared.ui.util.PlatformBackHandler(iosEdgeGestureEnabled = false, onBack = onExit)
    stateHolder.SaveableStateProvider(mixId) {
        FutachaMixScreen(
            mix = resolved, repository = repository, store = store, onBack = onExit, onOpen = onOpen,
            registeredBoards = boards, onSwitch = onSwitch, stateStore = stateStore,
            httpClient = httpClient, fileSystem = fileSystem, projectEntries = projectEntries, catalogWordNgEnabled = catalogWordNgEnabled, modePolicy = modePolicy,
            gridColumns = gridColumns
        )
    }
}
