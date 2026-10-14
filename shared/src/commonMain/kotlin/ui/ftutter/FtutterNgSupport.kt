package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.ui.board.canonicalThreadSearchText
import com.valoser.futacha.shared.ui.board.messageHtmlToPlainText
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * NG・ピックアップ（原作 NGData。docs/ftutter-mode-research-round6-2026-10-13.md §4）。
 *
 * 保存は`compat.ftutter.NGList`（JSONの配列）。形は原作と同じ text・type・isReg・isActive・ita で、
 * 原作のバックアップ（第8段階の`.ftt`取り込み）の値をそのまま入れられる。他モードのNGには書かない。
 */
@Serializable
internal data class FtutterNgBoard(val name: String = "", val url: String = "")

@Serializable
internal data class FtutterNgRule(
    val text: String,
    val type: Int,
    val isReg: Boolean = false,
    val isActive: Boolean = true,
    /** null は全板。 */
    val ita: FtutterNgBoard? = null
)

internal enum class FtutterNgType(val stored: Int, val label: String) {
    TITLE(0, "NGタイトル"),
    WORD(1, "NGワード"),
    ID(2, "NGID・IP"),
    PICKUP(3, "ピックアップタイトル");

    companion object {
        fun of(rule: FtutterNgRule): FtutterNgType? = entries.firstOrNull { it.stored == rule.type }
    }
}

private val ngJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
private val ngListSerializer = ListSerializer(FtutterNgRule.serializer())

/** 保存値を読む。原作の StringList（1行に1件のJSON）も読める。type 0〜3 以外は取り込まない。 */
internal fun parseFtutterNgList(raw: String?): List<FtutterNgRule> {
    val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyList()
    val rules = if (text.startsWith("[")) {
        runCatching { ngJson.decodeFromString(ngListSerializer, text) }.getOrDefault(emptyList())
    } else {
        text.lines().mapNotNull { line -> line.trim().takeIf { it.isNotEmpty() }?.let { runCatching { ngJson.decodeFromString(FtutterNgRule.serializer(), it) }.getOrNull() } }
    }
    return rules.filter { it.type in 0..3 && it.text.isNotEmpty() }
}

internal fun encodeFtutterNgList(rules: List<FtutterNgRule>): String = ngJson.encodeToString(ngListSerializer, rules)

/** 正規表現が作れるか。原作は検証せず、不正な`[`で本文やカタログが出なくなった。ここでは保存の前に断る。 */
internal fun ftutterRegexOrNull(pattern: String): Regex? = runCatching { Regex(pattern) }.getOrNull()

/**
 * 登録の検査。原作の3つ（空・5項目が同じものの重複・IDは「ID」か「IP」で始まる）に、正規表現の検証を足す。
 * 問題が無ければ null。
 */
internal fun ftutterNgValidationError(rule: FtutterNgRule, existing: List<FtutterNgRule>): String? = when {
    rule.text.isEmpty() -> "文字列が空です！"
    rule in existing -> "既に同じものが存在します！"
    rule.type == FtutterNgType.ID.stored && !(rule.text.startsWith("ID") || rule.text.startsWith("IP")) ->
        "'ID:'または'IP:'を含めて入力してください！"
    rule.isReg && rule.type != FtutterNgType.ID.stored && ftutterRegexOrNull(rule.text) == null -> "正規表現が正しくありません"
    else -> null
}

/** 板の範囲を比べる鍵（http・https、`futaba.php`、大文字小文字の違いをそろえる）。 */
internal fun ftutterNgBoardKey(url: String): String {
    val base = if (url.contains("/res/")) url.substringBefore("/res/") + "/" else ftutterBoardBaseUrl(url)
    return base.substringAfter("://").lowercase()
}

/**
 * 1つの板で使う照合器。ルールごとに正規表現を1回だけ作り、作れないルールは飛ばす（原作のように全体を止めない）。
 * 文字列の照合は全角半角・かなの種類・大文字小文字を同一視する（ふたったー風の検索と同じ。原作は区別する）。
 * 正規表現とID/IPは正規化しない。
 */
internal class FtutterNgMatcher(rules: List<FtutterNgRule>, boardUrl: String?) {
    private class Compiled(val rule: FtutterNgRule, val regex: Regex?, val normalized: String)

    private val compiled: List<Compiled> = run {
        val boardKey = boardUrl?.let(::ftutterNgBoardKey)
        rules.filter { rule ->
            rule.isActive && (rule.ita == null || rule.ita.url.isBlank() || (boardKey != null && ftutterNgBoardKey(rule.ita.url) == boardKey))
        }.mapNotNull { rule ->
            if (rule.isReg && rule.type != FtutterNgType.ID.stored) {
                ftutterRegexOrNull(rule.text)?.let { Compiled(rule, it, "") }
            } else {
                Compiled(rule, null, canonicalThreadSearchText(rule.text.lowercase()))
            }
        }
    }

    private fun matches(c: Compiled, text: String, normalizedText: () -> String): Boolean =
        c.regex?.containsMatchIn(text) ?: normalizedText().contains(c.normalized)

    private fun ofType(type: FtutterNgType) = compiled.filter { it.rule.type == type.stored }

    private val titles = ofType(FtutterNgType.TITLE)
    private val words = ofType(FtutterNgType.WORD)
    private val ids = ofType(FtutterNgType.ID)
    private val pickups = ofType(FtutterNgType.PICKUP)

    val isEmpty: Boolean get() = compiled.isEmpty()

    /** NGタイトル（カタログの題名だけ）。 */
    fun hidesTitle(title: String): Boolean {
        val normalized by lazy { canonicalThreadSearchText(title.lowercase()) }
        return titles.any { matches(it, title) { normalized } }
    }

    /** ピックアップの一致語（登録順で最初に一致したもの）。 */
    fun pickupWord(title: String): String? {
        val normalized by lazy { canonicalThreadSearchText(title.lowercase()) }
        return pickups.firstOrNull { matches(it, title) { normalized } }?.rule?.text
    }

    /** NGワード（本文。行は改行でつなぐ）と NGID・IP（表示されたIDとの完全一致）。 */
    fun hidesPost(post: Post): Boolean {
        val id = post.posterId?.trim()
        if (id != null && ids.any { it.rule.text == id }) return true
        if (words.isEmpty()) return false
        val body = messageHtmlToPlainText(post.messageHtml)
        val normalized by lazy { canonicalThreadSearchText(body.lowercase()) }
        return words.any { matches(it, body) { normalized } }
    }
}

/** スレッドでNGに当たったレス（連鎖がONなら、それを引用した返信を段ごとに）。 */
internal fun ftutterNgHiddenPosts(posts: List<Post>, matcher: FtutterNgMatcher, chain: Boolean): Set<Int> {
    if (matcher.isEmpty) return emptySet()
    val direct = posts.indices.filter { matcher.hidesPost(posts[it]) }.toSet()
    if (!chain || direct.isEmpty()) return direct
    val replies = ftutterReplyIndex(posts)
    val all = direct.toMutableSet()
    val queue = ArrayDeque(direct)
    while (queue.isNotEmpty()) {
        val at = queue.removeFirst()
        replies[posts[at].id].orEmpty().forEach { reply -> if (all.add(reply)) queue.addLast(reply) }
    }
    return all
}

/** ピックアップを効かせる並び順（原作`applyPickupOrderMode`。保存は`OrderMode.xxx`の列。未保存なら7つすべて）。 */
private val PICKUP_ORDER_NAMES = mapOf(
    "OrderMode.catalog" to FtutterOrder.CATALOG, "OrderMode.newnew" to FtutterOrder.NEW, "OrderMode.old" to FtutterOrder.OLD,
    "OrderMode.ooi" to FtutterOrder.MANY, "OrderMode.sukunai" to FtutterOrder.FEW, "OrderMode.ikioi" to FtutterOrder.MOMENTUM,
    "OrderMode.soudane" to FtutterOrder.SOUDANE
)

internal fun parseFtutterPickupOrders(raw: String?): Set<FtutterOrder> {
    val names = decodeFtutterList(raw) ?: return FtutterOrder.entries.toSet()
    return names.mapNotNull { PICKUP_ORDER_NAMES[it.trim()] }.toSet()
}

internal fun encodeFtutterPickupOrders(orders: Set<FtutterOrder>): String =
    encodeFtutterList(PICKUP_ORDER_NAMES.filterValues { it in orders }.keys.toList())

/** カタログにNGタイトルとピックアップを当てた結果。 */
internal data class FtutterCatalogNgResult(
    val items: List<CatalogItem>,
    /** スレURL → ピックアップの一致語（印に出す）。 */
    val pickupWords: Map<String, String>,
    /** NGタイトルで消えたスレ（「非表示・NG」の画面に出す）。 */
    val ngRemoved: List<CatalogItem>
)

/**
 * 原作の順（NGタイトル→ピックアップ）。ピックアップは、今の並び順が対象の時だけ、一致したスレを今の並びのまま先頭へ寄せ、
 * 一致語の印を付ける。対象でない時は寄せも印も行わない。
 */
internal fun ftutterApplyCatalogNg(
    items: List<CatalogItem>,
    matcher: FtutterNgMatcher,
    order: FtutterOrder,
    pickupOrders: Set<FtutterOrder>
): FtutterCatalogNgResult {
    if (matcher.isEmpty) return FtutterCatalogNgResult(items, emptyMap(), emptyList())
    val (removed, kept) = items.partition { matcher.hidesTitle(it.title.orEmpty()) }
    if (order !in pickupOrders) return FtutterCatalogNgResult(kept, emptyMap(), removed)
    val words = LinkedHashMap<String, String>()
    kept.forEach { item -> matcher.pickupWord(item.title.orEmpty())?.let { words[item.threadUrl] = it } }
    val picked = kept.filter { it.threadUrl in words }
    return FtutterCatalogNgResult(picked + kept.filter { it.threadUrl !in words }, words, removed)
}
