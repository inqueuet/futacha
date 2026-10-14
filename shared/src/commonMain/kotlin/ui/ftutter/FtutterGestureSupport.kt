package com.valoser.futacha.shared.ui.ftutter

/**
 * 折れ線ジェスチャー（原作 Gesture.dart。docs/ftutter-mode-research-round6-2026-10-13.md §2）。
 *
 * 方向の番号は 0↑・1→・2←・3↓。軌跡の保存キーは`Gesture`＋番号を連結した文字列（例：→↑は`Gesture10`）。
 */
internal enum class FtutterGestureAction(val stored: Int, val label: String) {
    NONE(0, "なし"),
    PREVIOUS(1, "前へ"),
    NEXT(2, "次へ"),
    BACK(3, "戻る"),
    TOP(4, "一番上へ"),
    BOTTOM(5, "一番下へ"),
    CLOSE_TAB(6, "タブを閉じる"),
    CLOSE_TAB_AND_BACK(7, "タブを閉じて戻る"),
    REOPEN_TAB(8, "閉じたタブを開く"),
    MOST_LEFT(9, "一番左のタブ"),
    MOST_RIGHT(10, "一番右のタブ"),
    REFRESH(11, "更新"),
    REFRESH_ALL_TABS(12, "全てのタブを更新"),
    OPEN_DRAWER(13, "ドロワーを開く"),
    EDIT(14, "書き込み"),
    SEARCH(15, "検索窓を開く"),
    TREE(16, "ツリー表示切り替え"),
    POPULAR(17, "人気レス抽出"),
    IMAGES(18, "画像抽出");

    companion object {
        fun fromStored(value: Int?): FtutterGestureAction? = entries.firstOrNull { it.stored == value }
    }
}

/** 26の軌跡（1区間2・2区間6・3区間18）と既定の割り当て（原作の既定 Map）。 */
internal val FTUTTER_GESTURE_DEFAULTS: Map<String, FtutterGestureAction> = run {
    val codes = buildList {
        listOf(1, 2).forEach { a ->
            add(listOf(a))
            (0..3).filter { it != a }.forEach { b ->
                add(listOf(a, b))
                (0..3).filter { it != b }.forEach { c -> add(listOf(a, b, c)) }
            }
        }
    }.sortedWith(compareBy({ it.size }, { it.joinToString("") }))
    val assigned = mapOf(
        "1" to FtutterGestureAction.PREVIOUS, "2" to FtutterGestureAction.NEXT,
        "10" to FtutterGestureAction.OPEN_DRAWER, "13" to FtutterGestureAction.CLOSE_TAB,
        "20" to FtutterGestureAction.TOP, "23" to FtutterGestureAction.BOTTOM,
        "102" to FtutterGestureAction.REFRESH_ALL_TABS, "201" to FtutterGestureAction.REFRESH
    )
    LinkedHashMap<String, FtutterGestureAction>().apply {
        codes.forEach { code -> val key = code.joinToString(""); put(key, assigned[key] ?: FtutterGestureAction.NONE) }
    }
}

internal fun ftutterGestureKey(code: String): String = FtutterKeys.gesture(code)

/** 軌跡の矢印（設定の表示）。 */
internal fun ftutterGestureArrows(code: String): String = code.map {
    when (it) { '0' -> '↑'; '1' -> '→'; '2' -> '←'; else -> '↓' }
}.joinToString("")

/** 保存された割り当て（軌跡ごとに操作の番号）。無い・範囲外は既定。 */
internal fun parseFtutterGestureMap(p: Map<String, String>): Map<String, FtutterGestureAction> =
    FTUTTER_GESTURE_DEFAULTS.mapValues { (code, default) ->
        FtutterGestureAction.fromStored(p[ftutterGestureKey(code)]?.trim()?.toIntOrNull()) ?: default
    }

internal data class FtutterGestureSettings(
    val enabled: Boolean = true,
    val backByPreviousInCatalog: Boolean = false,
    val backByPreviousInThread: Boolean = true,
    val moveTabBySwipe: Boolean = true,
    val map: Map<String, FtutterGestureAction> = FTUTTER_GESTURE_DEFAULTS
)

internal fun parseFtutterGestureSettings(p: Map<String, String>) = FtutterGestureSettings(
    enabled = p.ftutterBool(FtutterKeys.USE_GESTURE, true),
    backByPreviousInCatalog = p.ftutterBool(FtutterKeys.BACK_BY_PREVIOUS_CATALOG, false),
    backByPreviousInThread = p.ftutterBool(FtutterKeys.BACK_BY_PREVIOUS_THREAD, true),
    moveTabBySwipe = p.ftutterBool(FtutterKeys.MOVE_TAB_BY_SWIPE, true),
    map = parseFtutterGestureMap(p)
)

/** 2区間目以降の閾値（原作`kando`、既定20dp。設定画面に変更する欄は無い）。 */
internal const val FTUTTER_GESTURE_KANDO_DP = 20f

/**
 * 1回のドラッグで方向の列を作る（原作 onPanUpdate）。移動は dp で渡す。
 * - 1区間目は閾値なし：認識が始まった後の最初の移動の横の符号だけで → か ← に決まる（必ず横）。
 * - 2区間目以降：その移動で大きいほうの軸だけを累積し、向きが反転するか軸が替わるとそこから数え直す。累積が[kando]を
 *   超えたら区間を足す（前と同じ向きは足さない）。
 */
internal class FtutterGestureTracker(private val kando: Float = FTUTTER_GESTURE_KANDO_DP) {
    private val directions = mutableListOf<Int>()
    private var accX = 0f
    private var accY = 0f

    val code: String get() = directions.joinToString("")

    fun update(dx: Float, dy: Float) {
        if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
            if (accX * dx > 0) accX += dx else { accX = dx; accY = 0f }
        } else {
            if (accY * dy > 0) accY += dy else { accY = dy; accX = 0f }
        }
        if (directions.isEmpty()) {
            if (dx > 0) { directions += 1; accX = 0f; accY = 0f } else if (dx < 0) { directions += 2; accX = 0f; accY = 0f }
            return
        }
        val last = directions.last()
        when {
            accX > kando && last != 1 -> { directions += 1; accX = 0f; accY = 0f }
            accX < -kando && last != 2 -> { directions += 2; accX = 0f; accY = 0f }
            accY < -kando && last != 0 -> { directions += 0; accX = 0f; accY = 0f }
            accY > kando && last != 3 -> { directions += 3; accX = 0f; accY = 0f }
        }
    }

    fun reset() {
        directions.clear(); accX = 0f; accY = 0f
    }
}

/**
 * 入力中の表示に出す操作名。4区間以上は何も出さず、指を離しても実行しない。割り当てが「なし」の軌跡は「なし」と出る。
 */
internal fun ftutterGestureLabel(code: String, map: Map<String, FtutterGestureAction>): String? =
    if (code.isEmpty() || code.length >= 4) null else map[code]?.label ?: ""

/** 指を離した時に実行する操作（4区間以上・割り当てなしは null）。 */
internal fun ftutterGestureActionFor(code: String, map: Map<String, FtutterGestureAction>): FtutterGestureAction? =
    map[code]?.takeIf { it != FtutterGestureAction.NONE && code.length in 1..3 }

/**
 * 認識の始まり。原作は Flutter のパン（受理距離＝タッチスロップの2倍）と縦スクロール（タッチスロップの縦成分）の競合で、
 * 書き始めが下へ30°を超えるとスクロールが勝つ。ここでも同じく、合計の移動が[panSlop]を超える前に縦の移動が
 * [scrollSlop]を超えたらスクロールに譲る。戻り値：true＝ジェスチャーとして受理、false＝譲る、null＝まだ決まらない。
 */
internal fun ftutterGestureAccepts(totalDx: Float, totalDy: Float, scrollSlop: Float, panSlop: Float = scrollSlop * 2): Boolean? {
    if (kotlin.math.abs(totalDy) > scrollSlop) return false
    return if (kotlin.math.hypot(totalDx, totalDy) > panSlop) true else null
}

// ---- 前へ・次へ ----

/** 隣のタブ（[forward]は次へ）。端なら null。 */
internal fun ftutterAdjacentTab(tabs: List<String>, current: String?, forward: Boolean): String? {
    val at = tabs.indexOf(current)
    if (at < 0) return null
    return tabs.getOrNull(if (forward) at + 1 else at - 1)
}

/**
 * カタログの前へ・次へ：お気に入りの板の並びで隣の板（原作のカタログは、お気に入りの板を横に並べたページ）。
 * 戻り値の [FtutterBoardStep.Back] は「前へ」で一覧の先頭か一覧に無い時、`backByPreviousInCatalog`がONなら前の画面へ戻る。
 */
internal sealed interface FtutterBoardStep {
    data class To(val boardId: String) : FtutterBoardStep
    data object Back : FtutterBoardStep
    data object Stay : FtutterBoardStep
}

internal fun ftutterBoardStep(favoriteIds: List<String>, current: String, forward: Boolean, backByPrevious: Boolean): FtutterBoardStep {
    val at = favoriteIds.indexOf(current)
    if (!forward && at <= 0) return if (backByPrevious) FtutterBoardStep.Back else FtutterBoardStep.Stay
    if (at < 0) return FtutterBoardStep.Stay
    val next = favoriteIds.getOrNull(if (forward) at + 1 else at - 1) ?: return FtutterBoardStep.Stay
    return FtutterBoardStep.To(next)
}

// ---- 二画面 ----

internal data class FtutterTabletSettings(
    val enabled: Boolean = false,
    /** この幅（dp）より広い時に二画面（原作どおり「より大きい」）。 */
    val minWidth: Int = 0,
    val columnsPortrait: Int = 3,
    val columnsLandscape: Int = 5,
    val threadOnLeft: Boolean = false,
    /** 左側の割合（%）。原作は0〜100で、0にすると表示が崩れたので20〜80に限る。 */
    val leftPercent: Int = 50
)

internal fun parseFtutterTabletSettings(p: Map<String, String>) = FtutterTabletSettings(
    enabled = p.ftutterBool(FtutterKeys.USE_TABLET, false),
    minWidth = p.ftutterInt(FtutterKeys.TABLET_MIN_WIDTH, 0, 0..2000),
    columnsPortrait = p.ftutterInt(FtutterKeys.TABLET_ROW_PORTRAIT, 3, 1..20),
    columnsLandscape = p.ftutterInt(FtutterKeys.TABLET_ROW_LANDSCAPE, 5, 1..20),
    threadOnLeft = p.ftutterBool(FtutterKeys.THREAD_ON_LEFT, false),
    leftPercent = (p[FtutterKeys.DIVIDE_PERCENTAGE]?.trim()?.toIntOrNull() ?: 50).coerceIn(FTUTTER_TABLET_PERCENT_RANGE)
)

internal val FTUTTER_TABLET_PERCENT_RANGE = 20..80

internal fun ftutterIsTwoPane(settings: FtutterTabletSettings, widthDp: Float): Boolean =
    settings.enabled && widthDp > settings.minWidth
