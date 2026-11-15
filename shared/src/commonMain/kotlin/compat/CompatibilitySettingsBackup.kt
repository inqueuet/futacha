package com.valoser.futacha.shared.compat

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 * Portable, deliberately bounded representation of the compatibility profile's
 * user settings.  Thread bodies and media are not included: those are caches,
 * not settings, and must never be overwritten by a settings restore.
 */
@Serializable
data class CompatToolbarBackup(
    val surface: String,
    val items: List<CompatToolbarBackupItem>
)

@Serializable
data class CompatToolbarBackupItem(
    val key: String,
    val position: Int,
    val active: Boolean,
    val hidden: Boolean = false
)

@Serializable
data class CompatSettingsBackup(
    val schemaVersion: Int = CURRENT_COMPAT_SETTINGS_BACKUP_VERSION,
    val exportedAtEpochMillis: Long,
    val boards: List<CompatBoard> = emptyList(),
    val tabs: List<CompatTab> = emptyList(),
    val history: List<CompatHistoryEntry> = emptyList(),
    val catalogPreferences: List<CompatCatalogPreference> = emptyList(),
    val preferences: Map<String, String> = emptyMap(),
    val ngRules: List<CompatNgRule> = emptyList(),
    /**
     * Null for partial (watch/NG-only) restores: those must keep the current
     * tabs, active tab and selector state instead of resetting the workspace.
     */
    val workspace: CompatWorkspaceRecord? = CompatWorkspaceRecord(),
    val toolbars: List<CompatToolbarBackup> = emptyList(),
    /** Each mode's marks and order on the backed-up tabs (plan B10). Absent from older files, which restore without marks. */
    val tabViews: List<CompatSharedTabView> = emptyList(),
    /**
     * The あいもげ boards and what hangs on them (AM-B5), kept apart so an older version skips them and restores the Futaba
     * part as before. Never written when empty, so a Futaba-only file is byte for byte what it was. In memory the lists
     * above hold everything: [encodeCompatSettingsBackup] splits, [decodeCompatSettingsBackup] joins.
     */
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val siteBoards: CompatSiteBoardsBackup? = null
)

/** See [CompatSettingsBackup.siteBoards]. */
@Serializable
data class CompatSiteBoardsBackup(
    val boards: List<CompatBoard> = emptyList(),
    val tabs: List<CompatTab> = emptyList(),
    val history: List<CompatHistoryEntry> = emptyList(),
    val tabViews: List<CompatSharedTabView> = emptyList(),
    val ngRules: List<CompatNgRule> = emptyList()
)

/** [this] with the あいもげ boards, their tabs, history, tab marks and board / thread NG rules moved to [CompatSettingsBackup.siteBoards]. */
internal fun CompatSettingsBackup.withSiteBoardsSplit(): CompatSettingsBackup {
    val joined = withSiteBoardsJoined()
    val siteBoardKeys = joined.boards.filter { aimogeBoardOnlyShapeOf(it.canonicalUrl) != null }.mapTo(HashSet(), CompatBoard::key)
    if (siteBoardKeys.isEmpty()) return joined
    val siteTabs = joined.tabs.filter { it.boardKey in siteBoardKeys }
    val siteHistory = joined.history.filter { it.boardKey in siteBoardKeys }
    val siteTabKeys = siteTabs.mapTo(HashSet(), CompatTab::key)
    val siteScopes = siteBoardKeys + siteTabKeys + (siteTabs.map { it.canonicalUrl } + siteHistory.map { it.canonicalUrl })
        .mapNotNull { runCatching { compatTabKey(it) }.getOrNull() }
    fun isSiteRule(rule: CompatNgRule) = rule.scopeKey in siteScopes
    return joined.copy(
        boards = joined.boards.filter { it.key !in siteBoardKeys },
        tabs = joined.tabs.filter { it.key !in siteTabKeys },
        history = joined.history.filter { it.boardKey !in siteBoardKeys },
        tabViews = joined.tabViews.filter { it.tabKey !in siteTabKeys },
        ngRules = joined.ngRules.filterNot(::isSiteRule),
        siteBoards = CompatSiteBoardsBackup(
            boards = joined.boards.filter { it.key in siteBoardKeys },
            tabs = siteTabs,
            history = siteHistory,
            tabViews = joined.tabViews.filter { it.tabKey in siteTabKeys },
            ngRules = joined.ngRules.filter(::isSiteRule)
        )
    )
}

/** [this] with [CompatSettingsBackup.siteBoards] put back into the lists (the shape the stores restore from). */
internal fun CompatSettingsBackup.withSiteBoardsJoined(): CompatSettingsBackup {
    val site = siteBoards ?: return this
    return copy(
        boards = boards + site.boards,
        tabs = tabs + site.tabs,
        history = history + site.history,
        tabViews = tabViews + site.tabViews,
        ngRules = ngRules + site.ngRules,
        siteBoards = null
    )
}

@Serializable
data class CompatSettingsBackupImportReport(
    val boardsImported: Int,
    val tabsImported: Int,
    val historyImported: Int,
    val preferencesImported: Int,
    val ngRulesImported: Int,
    val toolbarsImported: Int
)

/**
 * How many restored tabs or history entries ([restoredKeys]) are still stored
 * ([keptKeys]) after the normal limits ran. The tab trim keeps the newest by
 * the time each tab was opened, so restored tabs older than the device's own
 * may be trimmed first (P4-4); the report counts what was actually kept.
 */
fun countCompatRestoredKept(restoredKeys: Collection<String>, keptKeys: Set<String>): Int =
    restoredKeys.toSet().count { it in keptKeys }

/** Human-editable companion file for watch words and NG rules only. */
@Serializable
data class CompatWatchNgBackup(
    val schemaVersion: Int = CURRENT_COMPAT_SETTINGS_BACKUP_VERSION,
    val exportedAtEpochMillis: Long,
    val watchWords: List<String> = emptyList(),
    val ngRules: List<CompatNgRule> = emptyList(),
    val watchRules: List<CompatWatchRule>? = null
)

/**
 * The tab trim never removes favourites or the active tab, so a profile can
 * legitimately hold more than [COMPAT_SHARED_TAB_LIMIT_DEFAULT] tabs (P4-3). A backup
 * accepts as many as Android reads back (MAX_COMPAT_TAB_READ_ROWS); the byte
 * limit below still bounds the file.
 */
const val MAX_COMPAT_BACKUP_TABS = 1_000
const val MAX_COMPAT_BACKUP_TAB_VIEWS = MAX_COMPAT_BACKUP_TABS * 4
const val CURRENT_COMPAT_SETTINGS_BACKUP_VERSION = 1
const val MAX_COMPAT_SETTINGS_BACKUP_BYTES = 2 * 1024 * 1024
const val COMPAT_SETTINGS_BACKUP_FILE_NAME = "futacha-compat-settings.json"
const val COMPAT_WATCH_NG_BACKUP_FILE_NAME = "futacha-compat-watch-ng.json"
const val COMPAT_WATCH_WORDS_PREFERENCE_KEY = "compat.catalog.監視ワード"

/**
 * Settings that only make sense on the device that wrote them: save folders (a document
 * tree URI, a bookmark or an absolute path) and cache locations. Restoring them on another
 * device or OS overwrote the folder chosen there with one that cannot be opened, so saving,
 * MHT and the saved box failed. Exports leave them out and a restore never applies them,
 * even from an older backup that still carries them.
 */
val COMPAT_BACKUP_DEVICE_PREFERENCE_KEYS = setOf(
    "compat.storage.dummyDownloadDir",
    "compat.storage.dummyDrawingDir",
    "compat.storage.dummyImageCacheLocation",
    "compat.storage.dummyCatalogImageCacheLocation"
)

/**
 * Post delete keys are passwords for deleting the user's own posts. A backup
 * file can be copied or shared, so exports leave them out; restoring an older
 * backup that still contains one keeps working.
 *
 * Unsent Futaber drafts are the user's unfinished post text; a settings file
 * that gets shared must not carry them, and a restore must not bring back
 * stale ones. The same holds for ふたったー風's own drafts (which also name the
 * attachments kept on this device) and its own delete key: neither is written,
 * and neither comes back from a file written before they were left out.
 *
 * The start lock's failed-attempt counter and remaining cool-down belong to
 * this device and must not be restored from (or leak into) a backup: restoring
 * one would lock the app, and leaving it out of an export would be no loss.
 */
/** ふたったー風's delete key and drafts (FtutterKeys.DELETE_KEY / DRAFTS; spelled out here so compat/ needs no UI). */
private const val COMPAT_BACKUP_FTUTTER_DELETE_KEY = "compat.ftutter.deleteKey"
private const val COMPAT_BACKUP_FTUTTER_DRAFTS = "compat.ftutter.drafts"
/** ふたばー風's "ペーストボード" (FutaberPreferenceKeys.PASTEBOARD): its pictures are files of this device, as the drafts'. */
private const val COMPAT_BACKUP_FUTABER_PASTEBOARD = "compat.futaber.pasteboard"

val COMPAT_BACKUP_EXCLUDED_PREFERENCE_KEYS = setOf(
    "compat.common.commonPostDeleteKey",
    "compat.lastDeleteKey",
    "compat.futaber.drafts",
    COMPAT_BACKUP_FUTABER_PASTEBOARD,
    COMPAT_BACKUP_FTUTTER_DELETE_KEY,
    COMPAT_BACKUP_FTUTTER_DRAFTS,
    "compat.appLock.attempts"
) + COMPAT_BACKUP_DEVICE_PREFERENCE_KEYS

fun compatBackupExportPreferences(preferences: Map<String, String>): Map<String, String> =
    preferences.filterKeys { it !in COMPAT_BACKUP_EXCLUDED_PREFERENCE_KEYS }

/**
 * ふたばー風's tab strip and ふたったー風's tab bar / protection / opening thread
 * (FutaberPreferenceKeys.TABS, FtutterKeys.TABS / PROTECTED / OPENING). The
 * tab-close reconciliation treats every thread that leaves these lists as
 * closed in all modes, so restoring an older list would close the tabs the user
 * has open now (audit M3, 2026-11-09). The shared tabs themselves are still
 * restored from the backup's own tab list.
 */
val COMPAT_BACKUP_MODE_TAB_LIST_KEYS = setOf(
    "compat.futaber.tabs",
    "compat.ftutter.tabs",
    "compat.ftutter.protected",
    "compat.ftutter.opening"
)

/** Keys a restore must not apply even when an (older) backup file contains them. */
private val COMPAT_BACKUP_UNRESTORABLE_PREFERENCE_KEYS =
    COMPAT_BACKUP_DEVICE_PREFERENCE_KEYS + "compat.futaber.drafts" + "compat.appLock.attempts" +
        // Restoring a lower limit would trim tabs the person has open now without asking (plan Q3).
        COMPAT_SHARED_TAB_LIMIT_KEY +
        COMPAT_BACKUP_FTUTTER_DELETE_KEY + COMPAT_BACKUP_FTUTTER_DRAFTS + COMPAT_BACKUP_FUTABER_PASTEBOARD +
        COMPAT_BACKUP_MODE_TAB_LIST_KEYS

fun compatBackupRestorePreferences(preferences: Map<String, String>): Map<String, String> =
    preferences.filterKeys { it !in COMPAT_BACKUP_UNRESTORABLE_PREFERENCE_KEYS }

private val compatSettingsBackupJson = Json {
    encodeDefaults = true
    // Backups written by a newer app version may carry fields this version
    // does not know yet; the bounded validation below still applies.
    ignoreUnknownKeys = true
    explicitNulls = true
}

fun encodeCompatSettingsBackup(backup: CompatSettingsBackup): String {
    // The limits and the duplicate checks see the Futaba part and the あいもげ part together (AM-B5).
    val joined = backup.withSiteBoardsJoined()
    validateCompatSettingsBackup(joined)
    val encoded = compatSettingsBackupJson.encodeToString(CompatSettingsBackup.serializer(), joined.withSiteBoardsSplit())
    val fits = encoded.encodeToByteArray().size <= MAX_COMPAT_SETTINGS_BACKUP_BYTES
    // The marks are the only part added by plan B10: they must never make a backup that used to fit fail.
    if (!fits && joined.tabViews.isNotEmpty()) return encodeCompatSettingsBackup(joined.copy(tabViews = emptyList()))
    require(fits) {
        "バックアップファイルが大きすぎます"
    }
    return encoded
}

fun decodeCompatSettingsBackup(raw: String): CompatSettingsBackup {
    require(raw.encodeToByteArray().size <= MAX_COMPAT_SETTINGS_BACKUP_BYTES) {
        "バックアップファイルが大きすぎます"
    }
    require("watchWords" !in compatSettingsBackupJson.parseToJsonElement(raw).jsonObject) {
        "監視ワード・NGのファイルです。対応する復元メニューを利用してください"
    }
    // Backups written by older versions carry the image hash cache as
    // preferences, often thousands of them; drop it before the size check so
    // such files can still be restored.
    val decoded = compatSettingsBackupJson.decodeFromString(CompatSettingsBackup.serializer(), raw)
        .withSiteBoardsJoined()
        .let { backup ->
            backup.copy(
                preferences = compatBackupRestorePreferences(backup.preferences)
                    .filterKeys { !isCompatImagePhashCacheKey(it) }
            )
        }
    validateCompatSettingsBackup(decoded)
    return decoded
}

fun encodeCompatWatchNgBackup(backup: CompatSettingsBackup): String {
    validateCompatSettingsBackup(backup)
    val words = backup.preferences[COMPAT_WATCH_WORDS_PREFERENCE_KEY]
        .orEmpty()
        .lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .toList()
    require(words.size <= 10_000) { "監視ワードが多すぎます" }
    val encoded = compatSettingsBackupJson.encodeToString(
        CompatWatchNgBackup.serializer(),
        CompatWatchNgBackup(
            exportedAtEpochMillis = backup.exportedAtEpochMillis,
            watchWords = words,
            ngRules = backup.ngRules,
            watchRules = if (COMPAT_WATCH_RULES_KEY in backup.preferences) compatWatchRules(backup.preferences) else null
        )
    )
    require(encoded.encodeToByteArray().size <= MAX_COMPAT_SETTINGS_BACKUP_BYTES) {
        "バックアップファイルが大きすぎます"
    }
    return encoded
}

fun decodeCompatWatchNgBackup(raw: String): CompatSettingsBackup {
    require(raw.encodeToByteArray().size <= MAX_COMPAT_SETTINGS_BACKUP_BYTES) {
        "バックアップファイルが大きすぎます"
    }
    require("watchWords" in compatSettingsBackupJson.parseToJsonElement(raw).jsonObject) {
        "監視ワード・NGのバックアップではありません"
    }
    val decoded = compatSettingsBackupJson.decodeFromString(CompatWatchNgBackup.serializer(), raw)
    require(decoded.schemaVersion == CURRENT_COMPAT_SETTINGS_BACKUP_VERSION) {
        "対応していないバックアップ形式です"
    }
    require(decoded.watchWords.size <= 10_000) { "監視ワードが多すぎます" }
    decoded.watchWords.forEach { word ->
        require(word.length <= MAX_COMPAT_PREFERENCE_VALUE_CHARS) { "監視ワードが長すぎます" }
    }
    require(decoded.ngRules.size <= MAX_COMPAT_NG_RULES) { "NG項目が多すぎます" }
    decoded.ngRules.forEach(::requireValidCompatNgRule)
    val normalizedWatchWords = decoded.watchWords
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .joinToString("\n")
    require(normalizedWatchWords.length <= MAX_COMPAT_PREFERENCE_VALUE_CHARS) {
        "監視ワードが多すぎます"
    }
    return CompatSettingsBackup(
        exportedAtEpochMillis = decoded.exportedAtEpochMillis,
        preferences = mapOf(
            COMPAT_WATCH_WORDS_PREFERENCE_KEY to normalizedWatchWords
        ) + (decoded.watchRules?.let { rules ->
            // Same compact encoding and limits as saving: the backup file's
            // verbose form (defaults and nulls written out) must not count.
            mapOf(COMPAT_WATCH_RULES_KEY to encodeValidCompatWatchRules(rules))
        } ?: emptyMap()),
        ngRules = decoded.ngRules,
        workspace = null
    )
}

/**
 * Keep the two user-facing backup rows physically independent.  The settings
 * file deliberately excludes watch/NG data so it can be restored without
 * replacing a hand-maintained keyword list.
 */
fun CompatSettingsBackup.settingsOnly(): CompatSettingsBackup = copy(
    preferences = preferences.filterKeys {
        it != COMPAT_WATCH_WORDS_PREFERENCE_KEY && it != COMPAT_WATCH_RULES_KEY &&
            !it.startsWith("compat.watcher.result.") && !isCompatImagePhashCacheKey(it)
    },
    ngRules = emptyList()
)

/**
 * A compact, editable keyword file. Board/tab records are intentionally not
 * included: importing words must never add or overwrite navigation state.
 * Board-scoped rules are accepted when the same board already exists on the
 * destination device, just like the legacy keyword.cfg import.
 */
fun CompatSettingsBackup.watchAndNgOnly(): CompatSettingsBackup = CompatSettingsBackup(
    schemaVersion = schemaVersion,
    exportedAtEpochMillis = exportedAtEpochMillis,
    preferences = preferences.filterKeys { it == COMPAT_WATCH_WORDS_PREFERENCE_KEY || it == COMPAT_WATCH_RULES_KEY },
    ngRules = ngRules,
    workspace = null
)

fun validateCompatSettingsBackup(backup: CompatSettingsBackup) {
    require(backup.schemaVersion == CURRENT_COMPAT_SETTINGS_BACKUP_VERSION) {
        "対応していないバックアップ形式です"
    }
    // The same limit as the board lists themselves (D-B1): with 100 here, a person with 101 boards could not save a
    // backup at all.
    require(backup.boards.size <= com.valoser.futacha.shared.model.MAX_REGISTERED_BOARDS) { "板の登録数が上限を超えています" }
    require(backup.tabs.size <= MAX_COMPAT_BACKUP_TABS) { "タブの登録数が上限を超えています" }
    require(backup.history.size <= 200) { "履歴の登録数が上限を超えています" }
    require(backup.preferences.size <= 4096) { "設定項目が多すぎます" }
    require(backup.ngRules.size <= MAX_COMPAT_NG_RULES) { "NG項目が多すぎます" }
    require(backup.toolbars.size <= CompatToolbarSurface.entries.size) { "ツールバー項目が多すぎます" }
    require(backup.tabViews.size <= MAX_COMPAT_BACKUP_TAB_VIEWS) { "タブの印が多すぎます" }
    require(backup.tabViews.all { it.tabKey.length in 1..500 && it.profile.length in 1..32 }) { "タブの印が不正です" }
    require(backup.boards.map { it.key }.distinct().size == backup.boards.size) { "板キーが重複しています" }
    require(backup.boards.map { it.canonicalUrl }.distinct().size == backup.boards.size) { "板URLが重複しています" }
    require(backup.tabs.map { it.key }.distinct().size == backup.tabs.size) { "タブキーが重複しています" }
    require(backup.history.map { it.canonicalUrl }.distinct().size == backup.history.size) { "履歴URLが重複しています" }
    backup.boards.forEach { board ->
        require(board.key.length in 1..200)
        require(board.name.length <= 200)
        require(board.canonicalUrl.length <= 500)
        require(board.originalUrl.length <= 500)
    }
    backup.preferences.forEach { (key, value) ->
        requireValidCompatPreference(key, value)
    }
    backup.ngRules.forEach(::requireValidCompatNgRule)
    backup.toolbars.forEach { toolbar ->
        require(toolbar.surface in CompatToolbarSurface.entries.map { it.name })
        require(toolbar.items.size <= 64)
        require(toolbar.items.map { it.key }.distinct().size == toolbar.items.size)
    }
}

/** The marks a backup carries: only on the tabs it carries, each mode in its own order. */
fun compatBackupTabViews(views: List<CompatSharedTabView>, tabs: List<CompatTab>): List<CompatSharedTabView> {
    val keys = tabs.mapTo(HashSet(), CompatTab::key)
    return views.filter { it.tabKey in keys }
        .distinctBy { it.profile to it.tabKey }
        .sortedWith(compareBy(CompatSharedTabView::profile, CompatSharedTabView::ordinal))
}

/**
 * The marks after a restore (plan B10). The tabs of a restore are added to the ones open now, so the marks are too: what
 * each mode has now stays where it is and the backup's marks follow in their own order. A mark is only a decoration of
 * a tab, so one that cannot apply (no such tab, a mode this version does not know) is dropped and fails nothing.
 */
fun compatMergedBackupTabViews(
    current: List<CompatSharedTabView>,
    restored: List<CompatSharedTabView>,
    tabKeys: Set<String>
): List<CompatSharedTabView> {
    val profiles = ExperienceProfile.entries.mapTo(HashSet(), ExperienceProfile::persistedValue)
    val incoming = restored.filter { it.tabKey in tabKeys && it.profile in profiles }
    if (incoming.isEmpty()) return current
    val touched = incoming.mapTo(LinkedHashSet(), CompatSharedTabView::profile)
    val rebuilt = touched.flatMap { profile ->
        val mine = current.filter { it.profile == profile }.sortedBy(CompatSharedTabView::ordinal)
        val present = mine.mapTo(HashSet(), CompatSharedTabView::tabKey)
        val added = incoming.filter { it.profile == profile && it.tabKey !in present }
            .sortedBy(CompatSharedTabView::ordinal)
            .distinctBy(CompatSharedTabView::tabKey)
        (mine + added).mapIndexed { index, view -> view.copy(ordinal = index) }
    }
    return current.filter { it.profile !in touched } + rebuilt
}
