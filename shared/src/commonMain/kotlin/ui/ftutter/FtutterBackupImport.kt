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
private val SAME_NAME_SETTINGS: List<String> = listOf(
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
    FtutterKeys.TABLET_ROW_LANDSCAPE, FtutterKeys.THREAD_ON_LEFT, FtutterKeys.DIVIDE_PERCENTAGE, FtutterKeys.DELETE_KEY
) + FTUTTER_GESTURE_DEFAULTS.keys.map(FtutterKeys::gesture)

/** 色の個別設定：原作は`<名前>ColorOverride`（既定なし）と`<名前>Color`（既定あり）。 */
private val OVERRIDE_COLORS = listOf(
    "background", "text", "toolBar", "onToolBar", "onToolBarActive", "popUp", "highlight", "dividerCard", "onDividerCard",
    "inTab", "outline", "kidoku", "midoku", "onMidoku", "headerText"
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
    val rejectedNg: Int = 0
) {
    val summary: String
        get() = buildList {
            add("設定 ${settingCount}項目")
            add("板 ${addedBoards}件")
            add("履歴 ${history.size}件")
            preferences[FtutterKeys.TABS]?.let { add("タブ ${decodeFtutterList(it).orEmpty().size}件") }
            add("NG・ピックアップ ${ngCount}件")
            if (rejectedNg > 0) add("使えないNG ${rejectedNg}件は取り込みません")
            if (marks.isNotEmpty()) add("マーク ${marks.size}件")
        }.joinToString("、")
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
    ensureActive: () -> Unit = {}
): FtutterBackupPlan {
    val out = LinkedHashMap<String, String>()
    fun scalar(value: FtutterPrefValue): String? = when (value) {
        is FtutterPrefValue.Bool -> value.value.toString()
        is FtutterPrefValue.Number -> value.value
        is FtutterPrefValue.Text -> value.value
        is FtutterPrefValue.Texts -> null
    }
    SAME_NAME_SETTINGS.forEach { key ->
        prefs[key.removePrefix(FtutterKeys.PREFIX)]?.let(::scalar)?.let { out[key] = it }
    }
    OVERRIDE_COLORS.forEach { name -> prefs["${name}ColorOverride"]?.let(::scalar)?.let { out[FtutterKeys.colorOverride(name)] = it } }
    PLAIN_COLORS.forEach { name -> prefs["${name}Color"]?.let(::scalar)?.let { out[FtutterKeys.colorOverride(name)] = it } }
    val settingCount = out.size
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
        ita["kakikomiData"]?.let { draftOf(it) }?.let { draft ->
            ftutterBoardForUrl(nextBoards, url)?.let { board ->
                out[FtutterKeys.DRAFTS] = ftutterDraftsWith(parseFtutterDrafts(out[FtutterKeys.DRAFTS] ?: existingPreferences[FtutterKeys.DRAFTS]),
                    ftutterDraftKey(FtutterPostTarget.Create(board.id), board.url), draft)
            }
        }
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
        data["kakikomiData"]?.let { draftOf(it) }?.let { draft ->
            out[FtutterKeys.DRAFTS] = ftutterDraftsWith(parseFtutterDrafts(out[FtutterKeys.DRAFTS] ?: existingPreferences[FtutterKeys.DRAFTS]),
                ftutterDraftKey(FtutterPostTarget.Reply(board.id, threadId), board.url), draft)
        }
    }
    if (history.isNotEmpty()) {
        // 取り込んだスレは履歴の上に並ぶので、その既読件数を先に残す（監査L14：今の記録の後ろに足していたので、今の記録が多いと
        // 上限で取り込んだ分が捨てられた）。
        val importedUrls = history.mapTo(LinkedHashSet()) { it.boardUrl }
        val ordered = readCounts.entries.sortedBy { if (it.key in importedUrls) 0 else 1 }
        out[FtutterKeys.READ_COUNTS] = encodeFtutterReadCounts(ordered.take(FTUTTER_IMPORT_READ_COUNT_LIMIT).associate { it.key to it.value })
    }
    if (protected.isNotEmpty()) out[FtutterKeys.PROTECTED] = encodeFtutterList(protected.toList())
    // タブ：今のタブの前に、原作のタブを（重複を除いて）入れる。
    texts("tabs")?.map(::ftutterCanonicalThreadUrl)?.takeIf { it.isNotEmpty() }?.let { tabs ->
        val current = parseFtutterTabs(existingPreferences).urls
        out[FtutterKeys.TABS] = encodeFtutterList((tabs + current).distinct())
    }
    return FtutterBackupPlan(out, nextBoards, added, history, marks, importedNg.size, settingCount, rejectedNg)
}

internal const val FTUTTER_IMPORT_READ_COUNT_LIMIT = 400

private fun draftOf(element: JsonElement): FtutterDraft? {
    val obj = element as? JsonObject ?: return null
    val draft = FtutterDraft(
        title = obj.str("title").orEmpty(), name = obj.str("name").orEmpty(),
        mail = obj.str("mail").orEmpty(), comment = obj.str("comment").orEmpty()
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
    return encodeFtutterReadCounts(merged.entries.take(FTUTTER_IMPORT_READ_COUNT_LIMIT).associate { it.key to it.value })
}
