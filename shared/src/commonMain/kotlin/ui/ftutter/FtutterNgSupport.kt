package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.normalizeCatalogSearchText
import com.valoser.futacha.shared.ui.board.canonicalThreadSearchText
import com.valoser.futacha.shared.ui.board.messageHtmlToPlainText
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

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

internal const val FTUTTER_NG_REGEX_INVALID = "正規表現が正しくありません"
internal const val FTUTTER_NG_REGEX_TOO_SLOW = "正規表現の照合に時間がかかりすぎます（繰り返しの入れ子などを見直してください）"

/**
 * 登録の検査。原作の3つ（空・5項目が同じものの重複・IDは「ID」か「IP」で始まる）に、正規表現の検証を足す。
 * 正規表現は、作れるかに加えて、照合に時間がかかりすぎないか（[ftutterRegexIsTooSlow]）も見る。問題が無ければ null。
 * `.ftt`から取り込むNGにも同じ検査をする。
 */
internal fun ftutterNgValidationError(rule: FtutterNgRule, existing: List<FtutterNgRule>): String? = when {
    rule.text.isEmpty() -> "文字列が空です！"
    rule in existing -> "既に同じものが存在します！"
    rule.type == FtutterNgType.ID.stored && !(rule.text.startsWith("ID") || rule.text.startsWith("IP")) ->
        "'ID:'または'IP:'を含めて入力してください！"
    rule.isReg && rule.type != FtutterNgType.ID.stored -> ftutterRegexProblem(rule.text)
    else -> null
}

/** 正規表現として使えない理由（作れない・照合に時間がかかりすぎる）。使えるなら null。 */
internal fun ftutterRegexProblem(pattern: String): String? {
    val regex = ftutterRegexOrNull(pattern) ?: return FTUTTER_NG_REGEX_INVALID
    return if (!ftutterRegexIsSafe(pattern, regex)) FTUTTER_NG_REGEX_TOO_SLOW else null
}

// ---- 正規表現の照合の時間制限 ----

/** NGの正規表現の照合が時間内に終わらなかった（破滅的なバックトラックなど）。 */
internal class FtutterRegexTimeoutException : RuntimeException("NGの正規表現の照合が時間内に終わりませんでした")

private const val FTUTTER_REGEX_CHECK_MASK = 0x3FF

/**
 * 照合する文字列を包み、1,024文字読むごとに期限と取り消しを見る。JVM（java.util.regex）も Kotlin/Native の正規表現も、
 * 照合の間は文字列を CharSequence として1文字ずつ読むので、期限を過ぎると [FtutterRegexTimeoutException] で照合が止まる
 * （`(a|aa)+$`のようなパターンでも固まらない）。
 */
internal class FtutterDeadlineCharSequence(
    private val text: CharSequence,
    private val deadline: TimeMark,
    private val ensureActive: () -> Unit = {},
    private val reads: IntArray = IntArray(1)
) : CharSequence {
    override val length: Int get() = text.length

    override fun get(index: Int): Char {
        val count = reads[0] + 1
        reads[0] = count
        if (count and FTUTTER_REGEX_CHECK_MASK == 0) {
            ensureActive()
            if (deadline.hasPassedNow()) throw FtutterRegexTimeoutException()
        }
        return text[index]
    }

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
        FtutterDeadlineCharSequence(text.subSequence(startIndex, endIndex), deadline, ensureActive, reads)

    override fun toString(): String = text.toString()
}

/** 1回の照合（1つのレス・題名）に使える時間。普通の正規表現は数マイクロ秒で終わる。 */
internal val FTUTTER_NG_REGEX_MATCH_LIMIT: Duration = 200.milliseconds

/**
 * 1回の照合のまとまり（スレッドのレス全部・カタログの題名全部）で、1つのルールに使える時間の合計（長いスレで少しずつ遅い
 * ルールが積み重なるのを防ぐ）。まとまりごとに数え直す（[FtutterNgMatcher.beginPass]。再監査M6：照合器の寿命の間ずっと
 * 足していたので、更新を繰り返すと普通の正規表現まで止められた）。
 */
internal val FTUTTER_NG_REGEX_RULE_BUDGET: Duration = 2.seconds

/**
 * 登録の時の試し：パターンに出てくる文字（と英字・数字・空白・かな）を並べ、最後に当たらない文字を置いた文字列で、
 * 長さを少しずつ伸ばしながら照合してみる。1回が[stepLimit]を超えたら「時間がかかりすぎる」とする。
 *
 * 2026-10-15の再監査H1：以前は1回の照合を[FtutterDeadlineCharSequence]の期限で止める前提だったが、Androidの正規表現は
 * 入力を`String`にしてネイティブ（ICU）へ渡すので、照合の途中で期限を見られず、破滅的なパターンでは止まらなかった。
 * 長さを少しずつ伸ばせば、遅くなり始めた所で気づき、次の1回もその何倍かで終わる（`(a|aa)+$`のような指数的なものは
 * 短い長さで見つかる）ので、どの環境でも途中で止める必要が無い。
 * JVM・iOSでは、期限の包みも合わせて使う。
 */
internal fun ftutterRegexIsTooSlow(
    regex: Regex,
    pattern: String,
    stepLimit: Duration = FTUTTER_NG_REGEX_STEP_LIMIT,
    /** The input as the engine sees it; the test passes the plain string to act like Android (no deadline checks). */
    input: (String, TimeMark) -> CharSequence = { probe, deadline -> FtutterDeadlineCharSequence(probe, deadline) }
): Boolean {
    val literals = (pattern.filter { it.isLetterOrDigit() }.toList().distinct().take(8) + listOf('a', '1', ' ', 'あ', '.')).distinct()
    val units = literals.map { it.toString() } + literals.joinToString("")
    return units.any { unit ->
        FTUTTER_NG_REGEX_PROBE_LENGTHS.any { length ->
            val probe = unit.repeat((length / unit.length).coerceAtLeast(1)) + "\u0001"
            val started = TimeSource.Monotonic.markNow()
            try {
                regex.containsMatchIn(input(probe, started + stepLimit * 4))
                started.elapsedNow() > stepLimit
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // 時間切れのほか、照合の中の深い再帰（JVM の StackOverflowError など）も使えないパターンとして扱う。
                true
            }
        }
    }
}

/**
 * 試しの長さ：短い所は細かく（指数的な遅さを早く見つける）、その先は1.5倍ずつ200文字ほどまで（3乗以上の遅さ）。
 * 普通のパターンでも2乗の遅さはある（`a+b`を`aaa…`に当てる）ので、それを断らないよう長くしすぎない（iOSの正規表現は
 * JVMより遅く、2,048文字では`a+b`も25msを超えた）。
 */
private val FTUTTER_NG_REGEX_PROBE_LENGTHS: List<Int> =
    (2..40 step 2).toList() + generateSequence(60) { (it * 3) / 2 }.takeWhile { it <= 256 }.toList()

/** 試しの1回に使える時間。普通の正規表現は数マイクロ秒で終わる。 */
internal val FTUTTER_NG_REGEX_STEP_LIMIT: Duration = 25.milliseconds

/**
 * パターンごとの試しの結果（アプリの起動中だけ覚える）。照合の前に必ず通す（H1：Androidでは照合を途中で止められない
 * ので、以前に保存された・取り込まれたルールも、使う前に試して遅いものは使わない）。重いので、メインスレッドの外で呼ぶ。
 */
@OptIn(ExperimentalAtomicApi::class)
private val ftutterRegexVerdicts = AtomicReference<Map<String, Boolean>>(emptyMap())

@OptIn(ExperimentalAtomicApi::class)
internal fun ftutterRegexIsSafe(pattern: String, regex: Regex): Boolean {
    ftutterRegexVerdicts.load()[pattern]?.let { return it }
    val safe = !ftutterRegexIsTooSlow(regex, pattern)
    while (true) {
        val current = ftutterRegexVerdicts.load()
        if (current.containsKey(pattern) || ftutterRegexVerdicts.compareAndSet(current, current + (pattern to safe))) break
    }
    if (!safe) ftutterMarkSlowNgPattern(pattern)
    return safe
}

/**
 * 照合が時間内に終わらなかったパターン（アプリの起動中だけ覚える）。以後の照合では使わず、NGの一覧に
 * 「照合に時間がかかるため使っていません」と出す。
 */
@OptIn(ExperimentalAtomicApi::class)
private val slowFtutterNgPatterns = AtomicReference<Set<String>>(emptySet())

@OptIn(ExperimentalAtomicApi::class)
internal fun ftutterIsSlowNgPattern(pattern: String): Boolean = pattern in slowFtutterNgPatterns.load()

@OptIn(ExperimentalAtomicApi::class)
internal fun ftutterMarkSlowNgPattern(pattern: String) {
    while (true) {
        val current = slowFtutterNgPatterns.load()
        if (pattern in current) return
        if (slowFtutterNgPatterns.compareAndSet(current, current + pattern)) return
    }
}

/** NGの一覧に出す、そのルールが使われない理由（正規表現が作れない・照合に時間がかかる）。使われるなら null。 */
internal fun ftutterNgRuleProblem(rule: FtutterNgRule): String? = when {
    !rule.isReg || rule.type == FtutterNgType.ID.stored -> null
    ftutterRegexOrNull(rule.text) == null -> "正規表現が正しくないため使っていません"
    ftutterIsSlowNgPattern(rule.text) -> "照合に時間がかかるため使っていません"
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
 *
 * 正規表現の照合には時間の制限がある（1回 [matchLimit]、1つのルールの合計 [ruleBudget]）。過ぎたルールは当たらなかった
 * ものとし、以後は使わない（[slowRules]、[ftutterIsSlowNgPattern]）。照合は重くなり得るので、画面はメインスレッドの外で
 * 呼ぶ。[ensureActive]は照合の途中でも取り消しを見るためのもの。
 */
@OptIn(ExperimentalAtomicApi::class)
internal class FtutterNgMatcher(
    rules: List<FtutterNgRule>,
    boardUrl: String?,
    private val matchLimit: Duration = FTUTTER_NG_REGEX_MATCH_LIMIT,
    private val ruleBudget: Duration = FTUTTER_NG_REGEX_RULE_BUDGET
) {
    private class Compiled(val rule: FtutterNgRule, val regex: Regex?, val normalized: String) {
        /** この照合器でこのルールの照合に使った時間（ナノ秒）。 */
        val spentNanos = AtomicLong(0L)
    }

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

    private fun matches(c: Compiled, text: String, ensureActive: () -> Unit, normalizedText: () -> String): Boolean {
        val regex = c.regex ?: return normalizedText().contains(c.normalized)
        if (ftutterIsSlowNgPattern(c.rule.text)) return false
        // 照合を途中で止められない環境（Android）でも固まらないよう、使う前に試す（H1）。
        if (!ftutterRegexIsSafe(c.rule.text, regex)) return false
        val left = ruleBudget - c.spentNanos.load().nanoseconds
        if (!left.isPositive()) {
            ftutterMarkSlowNgPattern(c.rule.text)
            return false
        }
        val started = TimeSource.Monotonic.markNow()
        return try {
            regex.containsMatchIn(FtutterDeadlineCharSequence(text, started + minOf(matchLimit, left), ensureActive))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            // 時間切れ（と JVM の深い再帰の StackOverflowError など）：当たらなかったものとし、以後は使わない。
            ftutterMarkSlowNgPattern(c.rule.text)
            false
        } finally {
            c.spentNanos.fetchAndAdd(started.elapsedNow().inWholeNanoseconds)
        }
    }

    /** 照合のまとまりを始める：ルールごとの時間の合計を数え直す。 */
    fun beginPass() {
        compiled.forEach { it.spentNanos.store(0L) }
    }

    private fun ofType(type: FtutterNgType) = compiled.filter { it.rule.type == type.stored }

    private val titles = ofType(FtutterNgType.TITLE)
    private val words = ofType(FtutterNgType.WORD)
    private val ids = ofType(FtutterNgType.ID)
    private val pickups = ofType(FtutterNgType.PICKUP)

    val isEmpty: Boolean get() = compiled.isEmpty()

    /** 照合に時間がかかりすぎて使わなくなった正規表現のルール。 */
    val slowRules: List<FtutterNgRule>
        get() = compiled.filter { it.regex != null && ftutterIsSlowNgPattern(it.rule.text) }.map { it.rule }

    /** NGタイトル（カタログの題名だけ）。 */
    fun hidesTitle(title: String, ensureActive: () -> Unit = {}): Boolean {
        val normalized by lazy { canonicalThreadSearchText(title.lowercase()) }
        return titles.any { matches(it, title, ensureActive) { normalized } }
    }

    /** ピックアップの一致語（登録順で最初に一致したもの）。 */
    fun pickupWord(title: String, ensureActive: () -> Unit = {}): String? {
        val normalized by lazy { canonicalThreadSearchText(title.lowercase()) }
        return pickups.firstOrNull { matches(it, title, ensureActive) { normalized } }?.rule?.text
    }

    /** NGワード（本文。行は改行でつなぐ）と NGID・IP（表示されたIDとの完全一致）。 */
    fun hidesPost(post: Post, ensureActive: () -> Unit = {}): Boolean {
        val id = post.posterId?.trim()
        if (id != null && ids.any { it.rule.text == id }) return true
        if (words.isEmpty()) return false
        val body = messageHtmlToPlainText(post.messageHtml)
        val normalized by lazy { canonicalThreadSearchText(body.lowercase()) }
        return words.any { matches(it, body, ensureActive) { normalized } }
    }
}

/** 照合の時に時間切れで使わなくなったルールを知らせる文。無ければ null。 */
internal fun ftutterSlowNgNotice(rules: List<FtutterNgRule>): String? =
    rules.takeIf { it.isNotEmpty() }?.joinToString("、", prefix = "NGの正規表現「", postfix = "」は照合に時間がかかるため使っていません") { it.text.take(30) }

/** スレッドでNGに当たったレス（連鎖がONなら、それを引用した返信を段ごとに）。 */
internal fun ftutterNgHiddenPosts(posts: List<Post>, matcher: FtutterNgMatcher, chain: Boolean, ensureActive: () -> Unit = {}): Set<Int> {
    if (matcher.isEmpty) return emptySet()
    matcher.beginPass()
    val direct = posts.indices.filter { matcher.hidesPost(posts[it], ensureActive) }.toSet()
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
    if (raw == FTUTTER_PICKUP_ORDERS_NONE) return emptySet()
    val names = decodeFtutterList(raw) ?: return FtutterOrder.entries.toSet()
    return names.mapNotNull { PICKUP_ORDER_NAMES[it.trim()] }.toSet()
}

/** 何も選ばない時の保存値（監査L12：空の値は「未設定＝すべて」と読まれ、すべて外しても「全て」に戻っていた）。 */
internal const val FTUTTER_PICKUP_ORDERS_NONE = "\n"

internal fun encodeFtutterPickupOrders(orders: Set<FtutterOrder>): String =
    if (orders.isEmpty()) FTUTTER_PICKUP_ORDERS_NONE
    else encodeFtutterList(PICKUP_ORDER_NAMES.filterValues { it in orders }.keys.toList())

/** 1つの題名の照合の結果（NGタイトル・ピックアップの一致語・共通のカタログNG）。 */
internal class FtutterTitleNg(val hidden: Boolean, val pickup: String?, val commonHidden: Boolean)

/** [items]の題名ごとの照合の結果。[matcher]・[commonWords]で作ったもの。 */
internal class FtutterCatalogTitleNg(
    val items: List<CatalogItem>,
    val matcher: FtutterNgMatcher,
    val commonWords: List<String>,
    val byTitle: Map<String, FtutterTitleNg>
) {
    fun isFor(items: List<CatalogItem>, matcher: FtutterNgMatcher, commonWords: List<String>): Boolean =
        this.items === items && this.matcher === matcher && this.commonWords == commonWords
}

/**
 * カタログの題名ごとに、NGタイトル・ピックアップ・共通のカタログNGを1回だけ照合する（正規表現もあり重いので、画面は
 * メインスレッドの外で呼ぶ）。検索語・並び順・非表示が変わっても、これはやり直さない。
 */
internal fun ftutterCatalogTitleNg(
    items: List<CatalogItem>,
    matcher: FtutterNgMatcher,
    commonWords: List<String>,
    ensureActive: () -> Unit = {}
): FtutterCatalogTitleNg {
    val needles = commonWords.map(::normalizeCatalogSearchText).filter { it.isNotEmpty() }
    matcher.beginPass()
    val byTitle = HashMap<String, FtutterTitleNg>()
    items.forEach { item ->
        val title = item.title.orEmpty()
        if (title in byTitle) return@forEach
        ensureActive()
        val normalized = if (needles.isEmpty()) "" else normalizeCatalogSearchText(title)
        byTitle[title] = FtutterTitleNg(
            hidden = !matcher.isEmpty && matcher.hidesTitle(title, ensureActive),
            pickup = if (matcher.isEmpty) null else matcher.pickupWord(title, ensureActive),
            commonHidden = needles.isNotEmpty() && needles.any { normalized.contains(it) }
        )
    }
    return FtutterCatalogTitleNg(items, matcher, commonWords, byTitle)
}

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
    pickupOrders: Set<FtutterOrder>,
    titleNg: Map<String, FtutterTitleNg>? = null
): FtutterCatalogNgResult {
    if (matcher.isEmpty) return FtutterCatalogNgResult(items, emptyMap(), emptyList())
    val (removed, kept) = items.partition { item ->
        val title = item.title.orEmpty()
        titleNg?.get(title)?.hidden ?: matcher.hidesTitle(title)
    }
    if (order !in pickupOrders) return FtutterCatalogNgResult(kept, emptyMap(), removed)
    val words = LinkedHashMap<String, String>()
    kept.forEach { item ->
        val title = item.title.orEmpty()
        val known = titleNg?.get(title)
        (if (known != null) known.pickup else matcher.pickupWord(title))?.let { words[item.threadUrl] = it }
    }
    val picked = kept.filter { it.threadUrl in words }
    return FtutterCatalogNgResult(picked + kept.filter { it.threadUrl !in words }, words, removed)
}

/** 保存されている一覧から[rule]を1件消す（監査M2：画面の古い一覧の位置で消さない）。 */
internal fun ftutterNgWithout(stored: List<FtutterNgRule>, rule: FtutterNgRule): List<FtutterNgRule> {
    val index = stored.indexOf(rule)
    return if (index < 0) stored else stored.filterIndexed { i, _ -> i != index }
}

/** 保存されている一覧の[old]を[saved]に置き換える。他で消されていれば何もしない。 */
internal fun ftutterNgReplaced(stored: List<FtutterNgRule>, old: FtutterNgRule, saved: FtutterNgRule): List<FtutterNgRule> {
    val index = stored.indexOf(old)
    return if (index < 0) stored else stored.mapIndexed { i, rule -> if (i == index) saved else rule }
}

/**
 * NGの一覧を変える口：変換は保存されている一覧に当てる。保存できた時だけ[savedNotice]を出し、上限に入らない時は理由を出して
 * 保存しない（監査A9・M2）。
 */
internal typealias FtutterNgEdit = (transform: (List<FtutterNgRule>) -> List<FtutterNgRule>, savedNotice: String?) -> Unit
