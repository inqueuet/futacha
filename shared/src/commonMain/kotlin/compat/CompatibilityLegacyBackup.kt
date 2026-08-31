package com.valoser.futacha.shared.compat

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import io.ktor.http.decodeURLQueryComponent

/** A word from the original Android viewer's keyword.cfg export. */
data class CompatLegacyScopedWord(
    val boardUrl: String?,
    val word: String
)

data class CompatLegacyBoardRecord(
    val name: String,
    val canonicalUrl: String,
    val originalUrl: String,
    val sortOrder: Int
)

/**
 * The original APK used Base64(JSON) and kept board-scoped catalog rules as
 * `[boardUrl, word]`. Thread rules were split into global strings and
 * board-scoped arrays. Keep this model independent of the current database so
 * it can be validated before anything is persisted.
 */
data class CompatLegacyBackupData(
    val fileType: String,
    val boards: List<CompatLegacyBoardRecord> = emptyList(),
    val catalogWatchWords: List<CompatLegacyScopedWord> = emptyList(),
    val catalogNgWords: List<CompatLegacyScopedWord> = emptyList(),
    val threadNgHeaders: List<CompatLegacyScopedWord> = emptyList(),
    val threadNgWords: List<CompatLegacyScopedWord> = emptyList(),
    val preferences: Map<String, String> = emptyMap(),
    /** old.apk's global catalog sort, migrated into every restored board. */
    val catalogSort: CompatCatalogSort? = null,
    /** Toolbar order/visibility stored in setting.cfg's four database snapshots. */
    val toolbars: Map<CompatToolbarSurface, List<CompatToolbarItem>> = emptyMap()
)

private const val LEGACY_KEYWORD_TYPE = "keyword"
private const val LEGACY_SETTING_TYPE = "setting"
internal const val MAX_COMPAT_LEGACY_BACKUP_BYTES = 2 * 1024 * 1024

/** Decode either the old Base64 wrapper or a plain JSON file used by tests/tools. */
@OptIn(ExperimentalEncodingApi::class)
fun decodeCompatLegacyBackup(raw: String): CompatLegacyBackupData {
    val normalized = raw.trim()
    require(normalized.encodeToByteArray().size <= MAX_COMPAT_LEGACY_BACKUP_BYTES) {
        "旧版バックアップが大きすぎます"
    }
    val jsonText = if (normalized.startsWith("{")) {
        normalized
    } else {
        decodeCompatLegacyBase64(normalized)
    }
    require(jsonText.encodeToByteArray().size <= MAX_COMPAT_LEGACY_BACKUP_BYTES) {
        "旧版バックアップが大きすぎます"
    }
    val root = kotlinx.serialization.json.Json.parseToJsonElement(jsonText).jsonObject
    val fileType = root.string("strFileType") ?: root.string("type")
        ?: error("旧版バックアップの種類を判定できません")
    return when (fileType.lowercase()) {
        LEGACY_KEYWORD_TYPE -> parseLegacyKeywordBackup(root)
        LEGACY_SETTING_TYPE -> parseLegacySettingBackup(root)
        else -> error("対応していない旧版バックアップです: $fileType")
    }
}

@OptIn(ExperimentalEncodingApi::class)
private fun decodeCompatLegacyBase64(raw: String): String {
    val compact = raw.filterNot(Char::isWhitespace)
    fun decode(candidate: String): String? = runCatching {
        Base64.decode(candidate).decodeToString()
    }.getOrNull()
    decode(compact)?.let { return it }
    val standard = compact.replace('-', '+').replace('_', '/')
    decode(standard)?.let { return it }
    return decode(standard + "=".repeat((4 - standard.length % 4) % 4))
        ?: error("旧版バックアップのBase64を読めません")
}

private fun parseLegacyKeywordBackup(root: JsonObject): CompatLegacyBackupData {
    fun catalog(key: String): List<CompatLegacyScopedWord> = root.array(key).mapNotNull { item ->
        val pair = item as? JsonArray ?: return@mapNotNull null
        val board = pair.stringAt(0)?.decodeLegacyComponent()?.let(::normalizeLegacyBoardScope)
        val word = pair.stringAt(1)?.decodeLegacyComponent().orEmpty().trim()
        word.takeIf { it.isNotBlank() }?.let { CompatLegacyScopedWord(board, it) }
    }

    fun thread(key: String, scopedKey: String): List<CompatLegacyScopedWord> = buildList {
        root.array(key).forEach { item ->
            item.stringValue()?.decodeLegacyComponent()?.trim()
                ?.takeIf(String::isNotBlank)
                ?.let { add(CompatLegacyScopedWord(null, it)) }
        }
        root.array(scopedKey).forEach { item ->
            val pair = item as? JsonArray ?: return@forEach
            val word = pair.stringAt(0)?.decodeLegacyComponent().orEmpty().trim()
            val board = pair.stringAt(1)?.decodeLegacyComponent()?.let(::normalizeLegacyBoardScope)
            word.takeIf(String::isNotBlank)?.let { add(CompatLegacyScopedWord(board, it)) }
        }
    }

    return CompatLegacyBackupData(
        fileType = LEGACY_KEYWORD_TYPE,
        catalogWatchWords = catalog("arrCatalogExtractList"),
        catalogNgWords = catalog("arrCatalogIgnoreList"),
        threadNgHeaders = thread("arrThreadRefuseList", "arrThreadRefuseOnlyList"),
        threadNgWords = thread("arrThreadIgnoreList", "arrThreadIgnoreOnlyList")
    )
}

/**
 * Map the stable, non-sensitive settings that have a direct compatibility
 * equivalent. Unknown fields are intentionally ignored because old setting.cfg
 * contains database/cache paths that must not be copied blindly. In particular,
 * Android document-provider paths, the posting-cookie timestamp/expiry and the
 * source APK version are device/session metadata rather than portable settings.
 * Board limit/favorite slots are also omitted: the final APK never consumes the
 * board favorite in UI, while this app probes thread existence directly instead
 * of estimating it from the historical board limit.
 */
private fun parseLegacySettingBackup(root: JsonObject): CompatLegacyBackupData {
    val boards = root.array("arrBoardList").mapNotNull { item ->
        val values = item as? JsonArray ?: return@mapNotNull null
        val name = values.stringAt(0)?.decodeLegacyComponent()?.trim().orEmpty()
        val originalUrl = values.stringAt(1)?.decodeLegacyComponent()
            ?.replace("http://", "https://")
            ?.trim()
            .orEmpty()
        val canonicalUrl = canonicalizeBoardUrl(originalUrl) ?: return@mapNotNull null
        CompatLegacyBoardRecord(
            name = name.ifBlank { canonicalUrl },
            canonicalUrl = canonicalUrl,
            originalUrl = canonicalUrl,
            // 1.apk's recoverBoradData resets corrupt orders above 200 to zero.
            // Negative values are equally unusable as a Compose list order.
            sortOrder = values.stringAt(4)?.toIntOrNull()
                ?.takeIf { it in 0..200 } ?: 0
        )
    }.distinctBy(CompatLegacyBoardRecord::canonicalUrl)
    val toolbars = buildMap {
        fun decodeToolbar(arrayKey: String, surface: CompatToolbarSurface) {
            val masterKeys = compatToolbarMaster(surface).mapTo(mutableSetOf()) { it.key }
            val decoded = root.array(arrayKey).mapNotNull { item ->
                val values = item as? JsonArray ?: return@mapNotNull null
                val key = values.stringAt(0)?.trim()?.let { rawKey ->
                    if (surface == CompatToolbarSurface.VIEWER) {
                        when (rawKey) {
                            "previous" -> "left"
                            "next" -> "right"
                            else -> rawKey
                        }
                    } else rawKey
                }?.takeIf { it in masterKeys }
                    ?: return@mapNotNull null
                val active = values.stringAt(1)?.toIntOrNull()?.let { it == 1 }
                    ?: return@mapNotNull null
                // 1.apk maps corrupt values above 20 back to zero before updating
                // its toolbar database. Negative values are equally unusable here.
                val position = values.stringAt(2)?.toIntOrNull()
                    ?.takeIf { it in 0..20 } ?: 0
                CompatToolbarItem(key = key, position = position, active = active)
            }
            if (decoded.isNotEmpty()) {
                put(surface, reconcileCompatToolbar(surface, decoded))
            }
        }
        decodeToolbar("arrCatalogToolbarList", CompatToolbarSurface.CATALOG)
        decodeToolbar("arrThreadToolbarList", CompatToolbarSurface.THREAD)
        decodeToolbar("arrViewerToolbarList", CompatToolbarSurface.VIEWER)
        decodeToolbar("arrPostToolbarList", CompatToolbarSurface.POST)
    }
    val preferences = buildMap {
        fun bool(old: String, path: String, key: String) {
            root.bool(old)?.let { put(compatLegacyStorageKey(path, key), if (it) "ON" else "OFF") }
        }
        fun text(old: String, path: String, key: String, transform: (String) -> String = { it }) {
            root.string(old)?.let { value ->
                transform(value).takeIf(String::isNotBlank)?.let { put(compatLegacyStorageKey(path, key), it) }
            }
        }
        fun integer(old: String, path: String, key: String, suffix: String = "") {
            root.string(old)?.filter(Char::isDigit)?.takeIf(String::isNotBlank)?.let {
                put(compatLegacyStorageKey(path, key), it + suffix)
            }
        }

        text("designTheme", "design", "designTheme", ::legacyTheme)
        bool("designNavigationBar", "design", "designNavigationBar")
        text("designLoading", "design", "designLoading", ::legacyLoading)
        text("designTabSelectorLocation", "design", "designTabSelectorLocation", ::legacySelectorLocation)
        bool("designTabSelectorOpened", "design", "designTabSelectorOpened")
        bool("controlPostConfirm", "control", "controlPostConfirm")
        bool("controlTouchScroll", "control", "controlTouchScroll")
        bool("controlTouchOpenDrawer", "control", "controlTouchOpenDrawer")
        bool("controlThreadCloseBack", "control", "controlThreadCloseBack")
        bool("controlViewerSwipeClose", "control", "controlViewerSwipeClose")
        text("controlCatalogVolumeKey", "control", "controlCatalogVolumeKey", ::legacyCatalogVolume)
        text("controlCatalogLongTap", "control", "controlCatalogLongTap", ::legacyLongTap)
        text("controlThreadVolumeKey", "control", "controlThreadVolumeKey", ::legacyThreadVolume)
        text("controlTabSelectorLongTap", "control", "controlTabSelectorLongTap", ::legacyTabLongTap)
        text("backgroundThreadExistCheck", "background", "backgroundThreadExistCheck", ::legacyNetworkMode)
        text("backgroundThreadUpdateCheck", "background", "backgroundThreadUpdateCheck", ::legacyNetworkMode)
        text("commonImageCache", "storage", "commonImageCache")
        text("commonThreadCache", "storage", "commonThreadCache")
        text("commonPostDeleteKey", "common", "commonPostDeleteKey")
        bool("catalogFastScroll", "catalog", "catalogFastScroll")
        bool("catalogPullToRefresh", "catalog", "catalogPullToRefresh")
        bool("catalogThumbCrop", "catalog", "catalogThumbCrop")
        bool("catalogEco", "catalog", "catalogEco")
        bool("catalogMobileEco", "catalog", "catalogMobileEco")
        bool("catalogGridViewResCountOnThumb", "catalog", "catalogGridViewResCountOnThumb")
        bool("catalogFindThreadDeleted", "catalog", "catalogFindThreadDeleted")
        bool("catalogOpenWithReload", "catalog", "catalogOpenWithReload")
        bool("threadPullToRefresh", "thread", "threadPullToRefresh")
        bool("threadFastScroll", "thread", "threadFastScroll")
        bool("threadNg", "thread", "threadNg")
        bool("threadHideDefaultNameAndSubject", "thread", "threadHideDefaultNameAndSubject")
        bool("threadHeaderQuoteSimple", "thread", "threadHeaderQuoteSimple")
        bool("threadAdminDeleteShow", "thread", "threadAdminDeleteShow")
        bool("viewerWebMSwitchMp4", "viewer", "viewerWebMSwitchMp4")
        text("viewerPreloadMode", "viewer", "viewerPreloadMode", ::legacyViewerPreload)
        text("threadHeaderSoudaneDisplay", "thread", "threadHeaderSoudaneDisplay", ::legacySaidaneDisplay)
        text("threadUpsThumbMethod", "thread", "threadUpsThumbMethod", ::legacyUpsMethod)
        integer("imageNgPhashThreshold", "thread", "threadImageNgPhashThreshold")
        integer("threadFontSize", "thread", "threadFontSize")
        integer("threadThumbSize", "thread", "threadThumbSize")
        integer("threadUpsThumbSize", "thread", "threadUpsThumbSize")
        integer("threadExtractSoudaneNum", "thread", "threadExtractSoudaneNum")
        integer("threadExtractQuoteNum", "thread", "threadExtractQuoteNum")
        integer("catalogGridViewPortraitClmNum", "catalog", "catalogGridViewPortraitClmNum")
        integer("catalogGridViewLandscapeClmNum", "catalog", "catalogGridViewLandscapeClmNum")
        integer("catalogGridViewTitleLength", "catalog", "catalogGridViewTitleLength", "文字")
        integer("catalogGridViewTitleFontSize", "catalog", "catalogGridViewTitleFontSize")
        integer("catalogListViewTitleLength", "catalog", "catalogListViewTitleLength", "文字")
        integer("catalogListViewTitleFontSize", "catalog", "catalogListViewTitleFontSize")
        integer("catalogListViewLineNum", "catalog", "catalogListViewLineNum")
        integer("galleryGridViewPortraitClmNum", "viewer", "galleryGridViewPortraitClmNum")
        integer("galleryGridViewLandscapeClmNum", "viewer", "galleryGridViewLandscapeClmNum")
        integer("catalogThreadSize", "catalog", "catalogThreadSize")
        integer("catalogTitleLength", "catalog", "catalogTitleLength")
        integer("delayFewReplies", "catalog", "delayFewReplies")
        bool("catalogAppendDropped", "catalog", "catalogAppendDropped")
        bool("catalogReloadScrollTop", "catalog", "catalogReloadScrollTop")
        root.string("catalogViewMode")?.toIntOrNull()?.let { mode ->
            put("compat.catalog.catalogViewMode", if (mode == 0) "0" else "1")
        }
        integer("autoScrollSpeed", "thread", "autoScrollSpeed", " ms")
        integer("autoScrollPixel", "thread", "autoScrollPixel", " px")
        text("networkImageParallel", "network", "networkImageParallel") { value ->
            value.trim().takeIf { it in setOf("1", "2", "3", "4", "5", "6", "8") }.orEmpty()
        }
        legacyImageSearchTargets(root)?.let { targets ->
            put("compat.image_search.engines", targets)
        }
        root.string("commonPrivacyAlpha")?.filter(Char::isDigit)?.toIntOrNull()?.let {
            put("compat.common.commonPrivacyAlpha", "${it.coerceIn(0, 100)}%")
        }
        root.bool("commonPrivacy")?.let { put("compat.common.commonPrivacy", if (it) "ON" else "OFF") }
        root.bool("catalogNg")?.let { put("compat.catalog.NG機能", if (it) "ON" else "OFF") }

        // Despite the historical `Bypass` key name, both APKs set this true
        // when the visible "通信の軽量化" switch is ON and route a thread
        // through the cache server.
        root.bool("networkCacheServerBypass")?.let { bypass ->
            put("compat.network.cache.enabled", if (bypass) "ON" else "OFF")
        }
        val networkStatus = listOfNotNull(
            root.string("networkCacheServerCheckDate")?.trim()?.takeIf(String::isNotBlank),
            root.string("networkCacheServerMessage")?.trim()?.takeIf(String::isNotBlank)
        ).joinToString(" - ")
        if (networkStatus.isNotBlank()) put("compat.network.cache.status", networkStatus)
    }
    return CompatLegacyBackupData(
        fileType = LEGACY_SETTING_TYPE,
        boards = boards,
        preferences = preferences,
        catalogSort = root.string("catalogSort")?.toIntOrNull()?.coerceIn(0, 4)?.let { mode ->
            when (mode) {
                1 -> CompatCatalogSort.NEW
                2 -> CompatCatalogSort.OLD
                3 -> CompatCatalogSort.MANY
                4 -> CompatCatalogSort.FEW
                else -> CompatCatalogSort.CATALOG
            }
        },
        toolbars = toolbars
    )
}

/**
 * Mirrors ImageSearchTarget.idsFromBackup() from 1.apk. New backups contain
 * stable IDs; old backups contain four labels plus the separate ascii2d flag.
 * A present empty ID array is meaningful and must restore an empty selection.
 */
private fun legacyImageSearchTargets(root: JsonObject): String? {
    val hasSearchFields = root.containsKey("imageSearchTargets") ||
        root.containsKey("customSearchUriMulti") || root.containsKey("commonAscii2dSearch")
    if (!hasSearchFields) return null

    val orderedIds = listOf(
        "google.file", "google.url", "lens.file", "lens.url", "ascii2d.url",
        "tineye.url", "iqdb.file", "iqdb.url", "saucenao.file", "saucenao.url",
        "yandex.file", "yandex.url", "bing.url"
    )
    val directIds = (root["imageSearchTargets"] as? JsonArray)?.mapNotNull { element ->
        element.stringValue()?.trim()?.takeIf { it in orderedIds }
    }
    val selected = if (directIds != null) {
        directIds.toSet()
    } else {
        buildSet {
            // This is the compatibility default inserted by the final APK
            // while migrating the older label-based search preferences.
            add("lens.file")
            if (root.bool("commonAscii2dSearch") == true) add("ascii2d.url")
            root.array("customSearchUriMulti").mapNotNull(JsonElement::stringValue).forEach { label ->
                when (label) {
                    "TinEye Search" -> add("tineye.url")
                    "IQDB Search" -> add("iqdb.url")
                    "SauceNAO Search" -> add("saucenao.url")
                    "Yandex画像検索" -> add("yandex.url")
                }
            }
        }
    }
    return orderedIds.filter { it in selected }.joinToString("|")
}

private fun compatLegacyStorageKey(path: String, key: String): String =
    if (key == "commonPrivacyAlpha") "compat.common.commonPrivacyAlpha" else "compat.$path.$key"

private fun normalizeLegacyBoardScope(raw: String): String? =
    if (raw.equals("allboard", ignoreCase = true)) null
    else canonicalizeBoardUrl(raw)

private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
private fun JsonObject.bool(key: String): Boolean? = this[key]?.jsonPrimitive?.contentOrNull?.let {
    when (it.lowercase()) {
        "true", "1", "on" -> true
        "false", "0", "off" -> false
        else -> null
    }
}
private fun JsonObject.array(key: String): JsonArray = this[key] as? JsonArray ?: JsonArray(emptyList())
private fun JsonArray.stringAt(index: Int): String? = getOrNull(index)?.stringValue()
private fun JsonElement.stringValue(): String? = (this as? JsonPrimitive)?.contentOrNull
private fun String.decodeLegacyComponent(): String = runCatching { decodeURLQueryComponent() }.getOrDefault(this)

private fun legacyTheme(value: String): String = when (value.lowercase()) {
    "monochrome" -> "mono"
    "default", "mono", "futaba", "blue", "pink", "black" -> value.lowercase()
    else -> value
}
private fun legacyLoading(value: String): String = if (value.equals("icon", true)) "icon" else "default"
private fun legacySelectorLocation(value: String): String = when (value.lowercase()) {
    "over" -> "over"
    "above" -> "above"
    else -> value
}
private fun legacyCatalogVolume(value: String): String = if (value.equals("scroll", true)) "1画面分スクロール" else "何もしない"
private fun legacyThreadVolume(value: String): String = when (value.lowercase()) {
    "reply" -> "1レス分スクロール"
    "scroll" -> "1画面分スクロール"
    "thread" -> "スレッドの切り替え"
    else -> "何もしない"
}
private fun legacyLongTap(value: String): String = when (value.lowercase()) {
    "nothing", "none" -> "何もしない"
    "ng" -> "NGスレッドに登録"
    "del" -> "delを送信する"
    "tab" -> "タブに追加する"
    else -> "選択メニュー"
}
private fun legacyTabLongTap(value: String): String = when (value.lowercase()) {
    "nothing", "none" -> "何もしない"
    "check" -> "更新の確認"
    "reload" -> "再読み込み"
    "post" -> "レスを書き込む"
    "close" -> "スレを閉じる"
    else -> "選択メニュー"
}
private fun legacyNetworkMode(value: String): String = when (value.lowercase()) {
    "usually", "always", "常に確認する" -> "usually"
    "wifi", "wi-fi回線のみ" -> "wifi"
    else -> "none"
}
private fun legacyViewerPreload(value: String): String = when (value.lowercase()) {
    "wifi", "wi-fi回線のみ" -> "wifi"
    "none", "off", "利用しない" -> "none"
    else -> "usually"
}
private fun legacySaidaneDisplay(value: String): String = when (value.lowercase()) {
    "show" -> "通常"
    "show|right" -> "通常(右寄せ)"
    "simple" -> "シンプル"
    "simple|right" -> "シンプル(右寄せ)"
    "hide", "none" -> "非表示"
    else -> value
}
private fun legacyUpsMethod(value: String): String = when (value.lowercase()) {
    "load" -> "表示する"
    "preload" -> "表示する(先読み)"
    "wifi" -> "Wi-Fi回線のみ先読み"
    else -> "表示しない"
}
