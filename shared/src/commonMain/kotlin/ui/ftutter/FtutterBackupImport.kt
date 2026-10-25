package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.parser.HtmlEntityDecoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * 原作のバックアップ（`.ftt`）の取り込み（docs/ftutter-mode-research-round6-2026-10-13.md §7）。
 *
 * `.ftt` は原作の設定XML（Android の SharedPreferences）を、AES（CTR）＋PKCS#7 で暗号化したもの。原作は復元の時に設定XMLを丸ごと
 * 上書きしてアプリを終了するが、ここでは中身を読み、このモードの保存形式へ読み替えて、今の設定に足す（読み取り専用の取り込み）。
 */
internal sealed interface FtutterPrefValue {
    data class Text(val value: String) : FtutterPrefValue
    data class Bool(val value: Boolean) : FtutterPrefValue
    data class Number(val value: String) : FtutterPrefValue
    data class Texts(val values: List<String>) : FtutterPrefValue
}

private const val LIST_PREFIX = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIGxpc3Qu"
private const val DOUBLE_PREFIX = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBEb3VibGUu"
private val looseJson = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * `.ftt` を復号して設定XMLの文字列にする。鍵が違う・壊れたファイル・設定XMLでない時は null。
 * 全体を復号する前に、末尾のブロック（PKCS#7の詰め物）と先頭のブロック（XMLの`<`で始まる）だけを復号して確かめ、
 * 明らかに違うファイル（選択はどの種類のファイルもできるので、無関係な大きなファイルも来る）は全体を復号しない。重いのでメインスレッドの外で呼ぶ。
 */
internal fun ftutterDecryptBackup(
    bytes: ByteArray,
    key: ByteArray = FtutterBackupCipher.key,
    iv: ByteArray = FtutterBackupCipher.iv,
    ensureActive: () -> Unit = {}
): String? {
    if (bytes.isEmpty() || bytes.size % 16 != 0) return null
    val aes = FtutterAes(key)
    if (ftutterStripPkcs7(aes.ctrBlock(bytes, iv, bytes.size / 16 - 1)) == null) return null
    if (!ftutterLooksLikeXmlStart(aes.ctrBlock(bytes, iv, 0))) return null
    val plain = ftutterStripPkcs7(aes.ctr(bytes, iv, ensureActive)) ?: return null
    ensureActive()
    val text = runCatching { plain.decodeToString(throwOnInvalidSequence = true) }.getOrNull() ?: return null
    return text.takeIf { it.contains("<map") }
}

/** 復号した先頭が設定XMLらしいか（UTF-8のBOMと空白の後が`<`）。原作の設定XMLは`<?xml`で始まる。 */
internal fun ftutterLooksLikeXmlStart(head: ByteArray): Boolean {
    var at = 0
    if (head.size >= 3 && head[0] == 0xEF.toByte() && head[1] == 0xBB.toByte() && head[2] == 0xBF.toByte()) at = 3
    while (at < head.size) {
        when (head[at].toInt().toChar()) {
            ' ', '\t', '\r', '\n' -> at++
            '<' -> return true
            else -> return false
        }
    }
    // 先頭のブロックが空白だけ（詰め物を含む短いファイル）は、全体を読んで決める。
    return true
}

private val PREF_ELEMENT = Regex(
    "<(string|boolean|long|int|float|double)\\s+name=\"([^\"]*)\"(?:\\s+value=\"([^\"]*)\")?\\s*(?:/>|>(.*?)</\\1>)",
    RegexOption.DOT_MATCHES_ALL
)

/** 設定XMLを読む。キーは`flutter.`を外したもの。原作のリストは接頭辞＋JSON、実数は別の接頭辞の文字列。 */
internal fun parseFtutterPreferencesXml(xml: String, ensureActive: () -> Unit = {}): Map<String, FtutterPrefValue> {
    val result = LinkedHashMap<String, FtutterPrefValue>()
    PREF_ELEMENT.findAll(xml).forEach { match ->
        ensureActive()
        val (tag, rawName, attrValue, body) = match.destructured
        val name = HtmlEntityDecoder.decode(rawName).removePrefix("flutter.")
        val value = when (tag) {
            "boolean" -> FtutterPrefValue.Bool(attrValue == "true")
            "long", "int", "float", "double" -> FtutterPrefValue.Number(attrValue)
            else -> {
                val text = HtmlEntityDecoder.decode(body)
                when {
                    text.startsWith("$LIST_PREFIX!") -> runCatching {
                        looseJson.parseToJsonElement(text.removePrefix("$LIST_PREFIX!")).jsonArray
                            .map { (it as? JsonPrimitive)?.content.orEmpty() }
                    }.getOrNull()?.let { FtutterPrefValue.Texts(it) }
                    text.startsWith(LIST_PREFIX) -> null // 古い形式（Base64のリスト）は読まない。
                    text.startsWith(DOUBLE_PREFIX) -> FtutterPrefValue.Number(text.removePrefix(DOUBLE_PREFIX))
                    else -> FtutterPrefValue.Text(text)
                }
            }
        }
        if (value != null) result[name] = value
    }
    return result
}

/** そのまま同じ名前で入れられる設定（このモードの`compat.ftutter.<原作の名前>`）。 */
internal val FTUTTER_BACKUP_SAME_NAME_SETTINGS: List<String> = listOf(
    FtutterKeys.MAIN_COLOR, FtutterKeys.THEME_MODE, FtutterKeys.CATALOG_FONT_SIZE, FtutterKeys.CATALOG_NUMBER_FONT_SIZE,
    FtutterKeys.RESU_HEADER_FONT_SIZE, FtutterKeys.RESU_CONTENT_FONT_SIZE, FtutterKeys.PRIVACY_CATALOG, FtutterKeys.PRIVACY_CATALOG_ZOOM,
    FtutterKeys.PRIVACY_THREAD, FtutterKeys.PRIVACY_THREAD_ZOOM, FtutterKeys.PRIVACY_TAB_DRAWER, FtutterKeys.DISABLE_STRETCH_ANIMATION,
    FtutterKeys.CATALOG_ROW, FtutterKeys.CATALOG_ROW_LANDSCAPE, FtutterKeys.MAX_CATALOG_THREAD_COUNT, FtutterKeys.SAME_HEIGHT_GRID,
    FtutterKeys.DEFAULT_ORDER_MODE, FtutterKeys.PREVIOUS_ORDER_MODE, FtutterKeys.CATALOG_HIGH_RES, FtutterKeys.MAX_THUMB_HEIGHT,
    FtutterKeys.MIN_THUMB_HEIGHT, FtutterKeys.USE_ZOOM, FtutterKeys.NUMBER_ON_THUMB, FtutterKeys.KITA_SHORTEN, FtutterKeys.MAX_TITLE_LINES,
    FtutterKeys.LIMIT_TITLE_LETTERS, FtutterKeys.MAX_TITLE_LETTERS, FtutterKeys.CATALOG_SCROLLBAR, FtutterKeys.CATALOG_SCROLLBAR_THICKNESS,
    FtutterKeys.CATALOG_SCROLLBAR_ALWAYS, FtutterKeys.CATALOG_SCROLLBAR_LEFT, FtutterKeys.CATALOG_PULL_TOP, FtutterKeys.CATALOG_PULL_BOTTOM,
    FtutterKeys.CATALOG_ICON_WIDTH, FtutterKeys.CATALOG_ICON_HEIGHT, FtutterKeys.CATALOG_TOOLBAR_LEFT, FtutterKeys.CATALOG_TOOLBAR_RIGHT,
    FtutterKeys.SHOW_DELETED, FtutterKeys.DELETED_CHAIN, FtutterKeys.QUOTE_SHORTEN, FtutterKeys.PADDING_TOP, FtutterKeys.PADDING_LEFT,
    FtutterKeys.PADDING_RIGHT, FtutterKeys.PADDING_BOTTOM, FtutterKeys.PADDING_HEADER_TEXT, FtutterKeys.PADDING_TEXT_FILE,
    FtutterKeys.OP_IMAGE_HIGH_RES, FtutterKeys.OP_IMAGE_MAX_HEIGHT, FtutterKeys.RES_IMAGE_HIGH_RES, FtutterKeys.RES_IMAGE_POSITION,
    FtutterKeys.RES_IMAGE_MAX_HEIGHT, FtutterKeys.RES_IMAGE_MAX_WIDTH, FtutterKeys.RES_IMAGE_FIXED_WIDTH, FtutterKeys.APU_AUTO_LOAD,
    FtutterKeys.APU_THUMB_SIZE, FtutterKeys.THREAD_SCROLLBAR, FtutterKeys.THREAD_SCROLLBAR_THICKNESS, FtutterKeys.THREAD_SCROLLBAR_ALWAYS,
    FtutterKeys.THREAD_SCROLLBAR_LEFT, FtutterKeys.THREAD_PULL_TOP, FtutterKeys.THREAD_PULL_BOTTOM, FtutterKeys.SHOW_NEW_COUNT,
    FtutterKeys.SHOW_REPLY_COUNT, FtutterKeys.POPULAR_REPLIES, FtutterKeys.POPULAR_SOUDANE, FtutterKeys.HISTORY_LIMIT,
    FtutterKeys.THREAD_ICON_WIDTH, FtutterKeys.THREAD_ICON_HEIGHT, FtutterKeys.THREAD_TOOLBAR_LEFT, FtutterKeys.THREAD_TOOLBAR_RIGHT,
    FtutterKeys.TAB_ON_TOP, FtutterKeys.TAB_HEIGHT, FtutterKeys.TAB_WIDTH, FtutterKeys.MOVE_TAB_BY_SWIPE,
    FtutterKeys.REFRESH_ON_TAB_MOVE_CATALOG, FtutterKeys.REFRESH_ON_TAB_MOVE_THREAD, FtutterKeys.DISABLE_TAB_DRAG, FtutterKeys.BACK_WHEN_NO_TAB,
    FtutterKeys.DRAWER_SIDE, FtutterKeys.DRAWER_OPEN_BY_DRAG, FtutterKeys.TAB_DISMISS_DIRECTION, FtutterKeys.DRAWER_BUTTON_TOP_LEFT,
    FtutterKeys.DRAWER_CONTENT, FtutterKeys.SHOW_TAB, FtutterKeys.IS_TREE, FtutterKeys.IS_PRIVACY,
    FtutterKeys.CONNECT_ALL_IMAGES, FtutterKeys.EXCLUDE_DELETED_IMAGES, FtutterKeys.VIDEO_AUTO_PLAY, FtutterKeys.VIDEO_LOOP,
    FtutterKeys.GALLERY_TAB_SIZE, FtutterKeys.USE_MEM_SIZE, FtutterKeys.MEM_SIZE, FtutterKeys.SHOW_GALLERY_TAB, FtutterKeys.GALLERY_COLUMNS,
    FtutterKeys.SAVE_HTML_THUMB, FtutterKeys.SAVE_HTML_FILE, FtutterKeys.SAVE_HTML_APU, FtutterKeys.SAVE_HTML_ZIP,
    FtutterKeys.NG_CHAIN, FtutterKeys.CONFIRM_BEFORE_POST, FtutterKeys.USE_GESTURE, FtutterKeys.BACK_BY_PREVIOUS_CATALOG,
    FtutterKeys.BACK_BY_PREVIOUS_THREAD, FtutterKeys.USE_TABLET, FtutterKeys.TABLET_MIN_WIDTH, FtutterKeys.TABLET_ROW_PORTRAIT,
    FtutterKeys.TABLET_ROW_LANDSCAPE, FtutterKeys.THREAD_ON_LEFT, FtutterKeys.DIVIDE_PERCENTAGE, FtutterKeys.DELETE_KEY,
    FtutterKeys.USE_CUSTOM_TAB, FtutterKeys.GESTURE_SENSITIVITY,
    FtutterKeys.IMAGE_SEARCH_ASCII2D, FtutterKeys.ALBUM_NAME, FtutterKeys.DISABLE_ALBUM_NAME
) + FTUTTER_GESTURE_DEFAULTS.keys.map(FtutterKeys::gesture)

/** 色の個別設定：原作は`<名前>ColorOverride`（既定なし）と`<名前>Color`（既定あり）。 */
private val OVERRIDE_COLORS = listOf(
    "background", "text", "toolBar", "onToolBar", "onToolBarActive", "popUp", "highlight", "dividerCard", "onDividerCard",
    "inTab", "gesture", "scrollBar", "indicator", "outline", "kidoku", "midoku", "onMidoku", "headerText"
)
private val PLAIN_COLORS = listOf("resuSuu", "onResuSuu", "pickUp", "onPickUp", "kakikomi", "henshin")

/** 原作のツールバー・メニューの並びは、ボタンの表示名を`"…"`で囲んで保存している。 */
private fun <T> mapLabels(values: List<String>, entries: List<T>, label: (T) -> String, stored: (T) -> String): List<String> =
    values.map { raw -> raw.trim().removeSurrounding("\"") }.map { name -> entries.firstOrNull { label(it) == name }?.let(stored) ?: name }

/** 取り込む内容。 */
internal data class FtutterBackupPlan(
    val preferences: Map<String, String>,
    val boards: List<BoardSummary>,
    val addedBoards: Int,
    val history: List<ThreadHistoryEntry>,
    val marks: List<Pair<String, String>>,
    val ngCount: Int,
    val settingCount: Int,
    /** 取り込まないNG（正規表現が作れない・照合に時間がかかりすぎる等。手で登録する時と同じ検査）。 */
    val rejectedNg: Int = 0,
    /** 追加のファイル選択やアクセス許可が必要なもの。取り込み前に知らせる。 */
    val limitations: List<String> = emptyList(),
    /** Header only; import into the shared cookie jar without logging its contents. */
    val cookieHeader: String? = null,
    /** Source drafts remain separate so confirmation-time edits are merged again when saved. */
    val importedDrafts: Map<String, FtutterDraft> = emptyMap(),
    val draftImport: FtutterDraftImport? = null,
    /** 取り込むタブの内訳（L132）。上限で閉じる数も確認の時に出す。 */
    val tabCounts: FtutterImportTabCounts? = null
) {
    override fun toString(): String = summary

    val summary: String
        get() = buildList {
            add("設定 ${settingCount}項目")
            add("板 ${addedBoards}件")
            add("履歴 ${history.size}件")
            tabCounts?.let { add(it.summary) } ?: preferences[FtutterKeys.TABS]?.let { add("タブ ${decodeFtutterList(it).orEmpty().size}件") }
            add("NG・ピックアップ ${ngCount}件")
            if (rejectedNg > 0) add("使えないNG ${rejectedNg}件は取り込みません")
            if (marks.isNotEmpty()) add("マーク ${marks.size}件")
            draftImport?.let { add(it.summary) }
            val cookieCount = com.valoser.futacha.shared.repository.parseWebViewCookieHeader(cookieHeader).size
            if (cookieCount > 0) add("Cookie ${cookieCount}件（全モード共有・ふたば全体。既存Cookieと同名のものは取り込みません）")
        }.joinToString("、") + if (limitations.isEmpty()) "" else "\n引き継ぎの制限：${limitations.joinToString("、")}"
}

private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.takeIf { it.isString || it.content != "null" }?.content
private fun JsonObject.str(name: String): String? = this[name].text()?.takeIf { it != "null" }
private fun JsonObject.int(name: String): Int? = (this[name] as? JsonPrimitive)?.intOrNull
private fun JsonObject.bool(name: String): Boolean? = (this[name] as? JsonPrimitive)?.booleanOrNull

/**
 * 設定XMLの中身から、このモードへ入れる内容を作る。[boards]は今の板（足した後の一覧を返す）、[existingNg]は今のNG
 * （同じものは足さない）、[now]は履歴の時刻（原作の並び順を保つため、先頭ほど新しくする）。
 */
internal fun ftutterBackupPlan(
    prefs: Map<String, FtutterPrefValue>,
    boards: List<BoardSummary>,
    existingNg: List<FtutterNgRule>,
    existingPreferences: Map<String, String>,
    now: Long,
    ensureActive: () -> Unit = {},
    /** 今の履歴の並び（最後に開いた順のスレURL）。取り込んだ後に上限で閉じるタブを決める（L132）。 */
    existingHistoryOrder: List<String> = emptyList()
): FtutterBackupPlan {
    val out = LinkedHashMap<String, String>()
    fun scalar(value: FtutterPrefValue): String? = when (value) {
        is FtutterPrefValue.Bool -> value.value.toString()
        is FtutterPrefValue.Number -> value.value
        is FtutterPrefValue.Text -> value.value
        is FtutterPrefValue.Texts -> null
    }
    FTUTTER_BACKUP_SAME_NAME_SETTINGS.forEach { key ->
        prefs[key.removePrefix(FtutterKeys.PREFIX)]?.let(::scalar)?.let { out[key] = it }
    }
    OVERRIDE_COLORS.forEach { name -> prefs["${name}ColorOverride"]?.let(::scalar)?.let { out[FtutterKeys.colorOverride(name)] = it } }
    PLAIN_COLORS.forEach { name -> prefs["${name}Color"]?.let(::scalar)?.let { out[FtutterKeys.colorOverride(name)] = it } }
    fun texts(name: String): List<String>? = (prefs[name] as? FtutterPrefValue.Texts)?.values
    texts("catTBOrderName")?.let {
        out[FtutterKeys.CATALOG_TOOLBAR_ORDER] = encodeFtutterList(mapLabels(it, FtutterCatalogTool.entries, { t -> t.label }, { t -> t.stored }))
    }
    texts("catTBOrderBool")?.let { out[FtutterKeys.CATALOG_TOOLBAR_ENABLED] = encodeFtutterList(it) }
    texts("sureTBOrderName")?.let {
        out[FtutterKeys.THREAD_TOOLBAR_ORDER] = encodeFtutterList(mapLabels(it, FtutterThreadTool.entries, { t -> t.label }, { t -> t.stored }))
    }
    texts("sureTBOrderBool")?.let { out[FtutterKeys.THREAD_TOOLBAR_ENABLED] = encodeFtutterList(it) }
    texts("resuDOrderName")?.let {
        out[FtutterKeys.POST_MENU_ORDER] = encodeFtutterList(mapLabels(it, FtutterPostMenuItem.entries, { t -> t.label }, { t -> t.stored }))
    }
    texts("resuDOrderBool")?.let { out[FtutterKeys.POST_MENU_ENABLED] = encodeFtutterList(it) }
    texts("applyPickupOrderMode")?.let { out[FtutterKeys.PICKUP_ORDER_MODES] = if (it.isEmpty()) FTUTTER_PICKUP_ORDERS_NONE else encodeFtutterList(it) }
    val settingCount = out.size
    var hasDraftAttachment = false
    val importedDrafts = LinkedHashMap<String, FtutterDraft>()
    fun importedDraft(element: JsonElement): FtutterDraft? {
        if ((element as? JsonObject)?.str("upFile")?.isNotBlank() == true) hasDraftAttachment = true
        return draftOf(element)
    }
    // 非表示：今のものに足す。
    texts("hihyouji")?.let { hidden ->
        val merged = (parseFtutterHiddenThreads(existingPreferences[FtutterKeys.HIDDEN_THREADS]) + hidden.map(::ftutterCanonicalThreadUrl)).distinct()
        out[FtutterKeys.HIDDEN_THREADS] = encodeFtutterList(merged)
    }
    // NG・ピックアップ：1件ずつJSON。今のものに無いものだけ足す。手で登録する時と同じ検査（正規表現が作れるか、照合に
    // 時間がかかりすぎないか等）を通らないものは取り込まない（以前は検査なしで保存し、照合で固まり得た）。
    val importedNg = mutableListOf<FtutterNgRule>()
    var rejectedNg = 0
    parseFtutterNgList(texts("NGList").orEmpty().joinToString("\n")).forEach { rule ->
        ensureActive()
        if (rule in existingNg || rule in importedNg) return@forEach
        if (ftutterNgValidationError(rule, emptyList()) != null) rejectedNg++ else importedNg += rule
    }
    if (importedNg.isNotEmpty()) out[FtutterKeys.NG_LIST] = encodeFtutterNgList(existingNg + importedNg)

    // 板：未登録の板を足し、原作のお気に入りは星を付ける。
    var nextBoards = boards
    var added = 0
    val totals = linkedMapOf(
        "sureOpenCount" to FtutterKeys.STATS_OPENED, "suretateCount" to FtutterKeys.STATS_CREATED,
        "resuCount" to FtutterKeys.STATS_REPLIED, "soudaneCount" to FtutterKeys.STATS_SOUDANE
    )
    val counts = mutableMapOf<String, Long>()
    texts("itaList").orEmpty().mapNotNull { runCatching { looseJson.parseToJsonElement(it) as? JsonObject }.getOrNull() }.forEach { ita ->
        val url = ita.str("url") ?: return@forEach
        val name = ita.str("name").orEmpty()
        if (ftutterBoardForUrl(nextBoards, url) == null) {
            val grown = com.valoser.futacha.shared.ui.futaber.futaberAddDiscoveredBoards(nextBoards, listOf(name to url))
            if (grown.size > nextBoards.size) {
                added += grown.size - nextBoards.size
                nextBoards = if (ita.bool("favorite") == true) {
                    val newIds = grown.map { it.id }.toSet() - nextBoards.map { it.id }.toSet()
                    grown.map { if (it.id in newIds) it.copy(pinned = true) else it }
                } else grown
            }
        }
        if (ita.bool("favorite") == true) {
            val id = ftutterBoardForUrl(nextBoards, url)?.id
            nextBoards = nextBoards.map { if (it.id == id) it.copy(pinned = true) else it }
        }
        totals.forEach { (original, target) ->
            ita.int(original)?.takeIf { it >= 0 }?.let { count ->
                counts[target] = ((counts[target] ?: 0L) + count).coerceAtMost(Int.MAX_VALUE.toLong())
            }
        }
        ita["kakikomiData"]?.let(::importedDraft)?.let { draft ->
            ftutterBoardForUrl(nextBoards, url)?.let { board ->
                importedDrafts.getOrPut(ftutterDraftKey(FtutterPostTarget.Create(board.id), board.url)) { draft }
            }
        }
    }

    counts.forEach { (key, value) -> out[key] = value.toString() }
    (prefs["openingCatalogUrl"] as? FtutterPrefValue.Text)?.value?.let { url ->
        ftutterBoardForUrl(nextBoards, url)?.let { out[FtutterKeys.LAST_BOARD] = it.id }
    }
    // 履歴・既読・保護・マーク・返信の下書き：スレッドのデータ（キー＝スレのURL）から。
    val history = mutableListOf<ThreadHistoryEntry>()
    val readCounts = parseFtutterReadCounts(existingPreferences[FtutterKeys.READ_COUNTS]).toMutableMap()
    val protected = parseFtutterProtected(existingPreferences).toMutableSet()
    val marks = mutableListOf<Pair<String, String>>()
    val historyUrls = texts("rirekiUrlList").orEmpty()
    historyUrls.forEachIndexed { order, rawUrl ->
        ensureActive()
        val data = (prefs[rawUrl] as? FtutterPrefValue.Text)?.value?.let {
            runCatching { looseJson.parseToJsonElement(it) as? JsonObject }.getOrNull()
        } ?: return@forEachIndexed
        val url = ftutterCanonicalThreadUrl(rawUrl)
        val board = ftutterBoardForUrl(nextBoards, url) ?: return@forEachIndexed
        val threadId = ftutterThreadIdFromUrl(url) ?: return@forEachIndexed
        val posts = (data["resuList"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        history += ThreadHistoryEntry(
            threadId = threadId,
            boardId = board.id,
            title = data.str("title").orEmpty(),
            titleImageUrl = posts.firstOrNull()?.str("fileThumbUrl").orEmpty(),
            boardName = board.name,
            boardUrl = url,
            lastVisitedEpochMillis = now - order,
            replyCount = maxOf(posts.size, (data.int("resuSuu") ?: 0) + 1),
            isAutoRefreshDisabled = data.bool("sureOti") == true
        )
        data.int("kokomade")?.takeIf { it >= 0 }?.let { readCounts[url] = maxOf(readCounts[url] ?: 0, it) }
        if (data.bool("protected") == true) protected += url
        posts.filter { it.int("marked") == 1 }.mapNotNull { it.int("no")?.toString() }.forEach { marks += url to it }
        data["kakikomiData"]?.let(::importedDraft)?.let { draft ->
            importedDrafts.getOrPut(ftutterDraftKey(FtutterPostTarget.Reply(board.id, threadId), board.url)) { draft }
        }
    }
    if (history.isNotEmpty()) {
        // 取り込んだスレは履歴の上に並ぶので、その既読件数を先に残す（監査L14：今の記録の後ろに足していたので、今の記録が多いと
        // 上限で取り込んだ分が捨てられた）。
        val importedUrls = history.mapTo(LinkedHashSet()) { it.boardUrl }
        val ordered = readCounts.entries.sortedBy { if (it.key in importedUrls) 0 else 1 }
        // 次の書き込みと同じ規則（字数・今のタブと保護したスレを先に）で収め、取り込んだ分が次の書き込みで減らないようにする（L129）。
        out[FtutterKeys.READ_COUNTS] = encodeFtutterReadCounts(ftutterTrimReadCounts(ordered.associate { it.key to it.value },
            importedUrls + ftutterReadRecordsToKeep(existingPreferences)))
    }
    if (protected.isNotEmpty()) out[FtutterKeys.PROTECTED] = encodeFtutterList(protected.toList())
    // タブ：今のタブの前に、原作のタブを（重複を除いて）入れる。取り込んだ履歴は今の履歴より上に並ぶので、履歴の保存数の
    // 上限は今のタブから閉じる。後で黙って閉じず、ここで上限に合わせ、閉じる数を確認の文に出す（L132）。
    var tabCounts: FtutterImportTabCounts? = null
    texts("tabs")?.map(::ftutterCanonicalThreadUrl)?.takeIf { it.isNotEmpty() }?.let { tabs ->
        val current = parseFtutterTabs(existingPreferences).urls
        val merged = (tabs + current).distinct()
        val order = (history.map { ftutterCanonicalThreadUrl(it.boardUrl) } + tabs + existingHistoryOrder + current).distinct()
        val limit = ftutterHistoryLimit(existingPreferences + out)
        val keep = listOfNotNull(existingPreferences[FtutterKeys.OPENING], (prefs["opening"] as? FtutterPrefValue.Text)?.value)
            .filter { it.isNotBlank() }.mapTo(HashSet(), ::ftutterCanonicalThreadUrl)
        val kept = ftutterLimitTabs(merged, order, protected, limit, keep, dropMissing = false)
        out[FtutterKeys.TABS] = encodeFtutterList(kept)
        tabCounts = FtutterImportTabCounts(imported = tabs.distinct().size, current = current.size, merged = merged.size,
            closed = merged.size - kept.size, limit = limit)
    }
    (prefs["opening"] as? FtutterPrefValue.Text)?.value?.let(::ftutterCanonicalThreadUrl)?.let { opening ->
        if (opening in decodeFtutterList(out[FtutterKeys.TABS] ?: existingPreferences[FtutterKeys.TABS]).orEmpty()) {
            out[FtutterKeys.OPENING] = opening
        }
    }
    val limitations = linkedSetOf<String>()
    fun hasValue(key: String): Boolean = prefs[key]?.let {
        when (it) {
            is FtutterPrefValue.Text -> it.value.isNotBlank()
            is FtutterPrefValue.Texts -> it.values.isNotEmpty()
            else -> true
        }
    } == true
    mapOf(
        "userFontPath" to "フォントのファイル（デザイン→カスタムフォントから再選択）",
        "savePath" to "画像の保存先（保存先・キャッシュから再選択）",
        "savePathList" to "画像の保存先（保存先・キャッシュから再選択）"
    ).forEach { (key, label) -> if (hasValue(key)) limitations += label }
    // Preserve source paths as recovery information; a copied URI does not confer the source app's grant.
    listOf("userFontPath", "savePath").forEach { key -> prefs[key]?.let(::scalar)?.let { out[FtutterKeys.PREFIX + "import." + key] = it } }
    texts("savePathList")?.let { out[FtutterKeys.PREFIX + "import.savePathList"] = encodeFtutterList(it) }
    // What was chosen when importing: once the user chooses another one, the notice about the original goes away (L19).
    if (FtutterKeys.PREFIX + "import.savePath" in out || FtutterKeys.PREFIX + "import.savePathList" in out) {
        out[FTUTTER_IMPORT_SAVE_PATH_CHOSEN] = existingPreferences[FTUTTER_SHARED_DESTINATION_KEY].orEmpty()
    }
    if (FtutterKeys.PREFIX + "import.userFontPath" in out) {
        out[FTUTTER_IMPORT_FONT_CHOSEN] = existingPreferences[com.valoser.futacha.shared.ui.futaber.FUTABER_CUSTOM_FONT_PREFERENCE_KEY].orEmpty()
    }
    if (hasDraftAttachment) limitations += "下書きの添付ファイル（本文は取り込みます）"
    val draftImport = importedDrafts.takeIf { it.isNotEmpty() }?.let {
        ftutterMergeImportedDrafts(existingPreferences[FtutterKeys.DRAFTS], it).also { fit -> out[FtutterKeys.DRAFTS] = fit.encoded }
    }
    return FtutterBackupPlan(out, nextBoards, added, history, marks, importedNg.size, settingCount, rejectedNg,
        limitations.toList(), (prefs["cookie"] as? FtutterPrefValue.Text)?.value, importedDrafts, draftImport, tabCounts)
}

/** 取り込むタブの内訳（L132）。[closed] は履歴の保存数 [limit] を超えて閉じる数（保護したタブは数えない）。 */
internal data class FtutterImportTabCounts(val imported: Int, val current: Int, val merged: Int, val closed: Int, val limit: Int) {
    val summary: String
        get() = buildString {
            append("タブ：取り込み ${imported}件")
            val details = listOfNotNull(
                "今のタブ ${current}件と合わせて ${merged}件".takeIf { current > 0 },
                "履歴の保存数 ${limit} を超える古いタブ ${closed}件は閉じます".takeIf { closed > 0 }
            )
            if (details.isNotEmpty()) append("（${details.joinToString("。")}）")
        }
}

internal val FTUTTER_SHARED_DESTINATION_KEY = com.valoser.futacha.shared.ui.compat.compatPreferenceStorageKey("storage", "dummyDownloadDir")
internal val FTUTTER_IMPORT_SAVE_PATH_CHOSEN = FtutterKeys.PREFIX + "import.savePathChosen"
internal val FTUTTER_IMPORT_FONT_CHOSEN = FtutterKeys.PREFIX + "import.userFontPathChosen"

/**
 * Whether the notice about an imported original (save location or font) still applies (L19): it does until another one
 * is chosen. An import made before the choice was recorded keeps its notice until it is dismissed.
 */
internal fun ftutterImportNoticePending(original: String?, chosenAtImport: String?, current: String?): Boolean =
    !original.isNullOrBlank() && (chosenAtImport == null || current.orEmpty() == chosenAtImport)

private fun draftOf(element: JsonElement): FtutterDraft? {
    val obj = element as? JsonObject ?: return null
    val draft = FtutterDraft(
        title = obj.str("title").orEmpty(), name = obj.str("name").orEmpty(),
        mail = obj.str("mail").orEmpty(), comment = obj.str("comment").orEmpty(),
        sourceAttachmentPath = obj.str("upFile").orEmpty()
    )
    return draft.takeIf { !it.isEmpty }
}

/**
 * The read counts an import writes, against the value stored at that moment: the imported threads' counts from the plan,
 * then every other count as stored now (re-audit L28), within the import's limit.
 */
internal fun ftutterMergeImportedReadCounts(planValue: String, stored: String?, importedUrls: Set<String>): String {
    val merged = LinkedHashMap<String, Int>()
    parseFtutterReadCounts(planValue).forEach { (url, count) -> if (url in importedUrls) merged[url] = count }
    parseFtutterReadCounts(stored).forEach { (url, count) -> if (url !in merged) merged[url] = count }
    return encodeFtutterReadCounts(ftutterTrimReadCounts(merged, importedUrls))
}
