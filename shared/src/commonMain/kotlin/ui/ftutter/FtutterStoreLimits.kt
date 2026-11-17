package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.compat.MAX_COMPAT_PREFERENCE_VALUE_CHARS
import com.valoser.futacha.shared.compat.isValidCompatPreference
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/*
 * 保存値の上限（1つの設定は2万字、MAX_COMPAT_PREFERENCE_VALUE_CHARS）と、戻るの順。
 * 2026-10-14の監査（docs/code-audit-2026-10-14.md）A3・A9。
 *
 * 方針：上限を超える値は、ストアが例外を出してログに残るだけで、利用者には「登録しました」と見えたまま消えていた。
 * - どの保存も、送る前に上限を確かめ、超える時は保存せずに理由を出す（FtutterAppの保存の入口）。
 * - NG：既定値を書き出さない形にして、短い語なら約600件、板を限ったものでも約200件入る。登録の時点で入らなければ
 *   ダイアログで断る（勝手に古いNGを消さない）。複数の設定に分けて保存する方法は、設定ごとに書き込みが分かれて
 *   途中で止まると壊れ、設定のバックアップ・復元とも食い違うので採らない。
 * - 非表示にしたスレ：原作もカタログを読むたびに落ちたスレを消すので、入らない時は古い記録から消し、件数を知らせる。
 * - 下書き：件数・長さ・全体の大きさを決める（FtutterPost.kt）。送信後に消す時は必ず収まる。
 * - .fttの取り込み：入る分だけ取り込み、入らなかったものを知らせる（[ftutterFitPreferencesForStore]）。
 */

// ---- 戻る ----

internal enum class FtutterBackStep { CLOSE_DRAWER, CATALOG_PANE, LEAVE }

/**
 * 画面の戻るの順：開いているドロワーを閉じる → 二画面のカタログ側の検索・過去ログ検索を閉じる → 画面を戻る。
 * Androidの戻るは最後に登録した受け手が先に受けるので、登録の順に頼らず、どの受け手もこの順で決める。
 */
internal fun ftutterBackStep(drawerOpen: Boolean, catalogPaneHandlesBack: Boolean): FtutterBackStep = when {
    drawerOpen -> FtutterBackStep.CLOSE_DRAWER
    catalogPaneHandlesBack -> FtutterBackStep.CATALOG_PANE
    else -> FtutterBackStep.LEAVE
}

// ---- 保存の入口 ----

internal const val FTUTTER_SAVE_REFUSED_NOTICE = "保存できる量を超えたため、保存できませんでした"

// ---- NG ----

private val ngStoreJson = Json { ignoreUnknownKeys = true; encodeDefaults = false }
private val ngStoreSerializer = ListSerializer(FtutterNgRule.serializer())

/** 保存用：既定値（isReg=false・isActive=true・ita=null）は書かない。[parseFtutterNgList]でそのまま読める。 */
internal fun encodeFtutterNgListForStore(rules: List<FtutterNgRule>): String = ngStoreJson.encodeToString(ngStoreSerializer, rules)

internal fun ftutterNgListFits(rules: List<FtutterNgRule>): Boolean =
    encodeFtutterNgListForStore(rules).length <= MAX_COMPAT_PREFERENCE_VALUE_CHARS

/** 登録した後の一覧が保存できない時の理由。収まれば null。 */
internal fun ftutterNgCapacityError(after: List<FtutterNgRule>): String? =
    if (ftutterNgListFits(after)) null else "NGが多すぎて保存できません。使っていないNGを消してから登録してください"

internal data class FtutterNgFit(val rules: List<FtutterNgRule>, val skipped: Int)

/** 今のNG（[existing]）は必ず残し、[added]を先頭から入る分だけ足す。 */
internal fun ftutterFitNgRules(existing: List<FtutterNgRule>, added: List<FtutterNgRule>): FtutterNgFit {
    // 1件ずつ書き出すと遅いので、1件の長さ（区切りの1文字を含む）を足していく。
    var length = encodeFtutterNgListForStore(existing).length
    val kept = ArrayList<FtutterNgRule>(existing.size + added.size).apply { addAll(existing) }
    var skipped = 0
    added.forEach { rule ->
        val extra = encodeFtutterNgListForStore(listOf(rule)).length - 2 + if (kept.isEmpty()) 0 else 1
        if (length + extra <= MAX_COMPAT_PREFERENCE_VALUE_CHARS) {
            kept += rule
            length += extra
        } else {
            skipped++
        }
    }
    return FtutterNgFit(kept, skipped)
}

// ---- 非表示にしたスレ ----

/** [url]を末尾に足す。収まらなければ古い記録（先頭）から消し、消した件数を返す。 */
internal fun ftutterHiddenThreadsWith(hidden: List<String>, url: String): Pair<List<String>, Int> {
    val next = (hidden + url).distinct()
    var length = encodeFtutterList(next).length
    var drop = 0
    while (length > MAX_COMPAT_PREFERENCE_VALUE_CHARS && drop < next.size - 1) {
        length -= next[drop].length + 1
        drop++
    }
    return next.drop(drop) to drop
}

internal fun ftutterHiddenNotice(dropped: Int): String =
    if (dropped > 0) "非表示にしました（残せる量を超えたため、古い非表示の記録を${dropped}件消しました）" else "非表示にしました"

// ---- .ftt の取り込み ----

internal data class FtutterStoreFit(val preferences: Map<String, String>, val leftOut: List<String>)

/**
 * 取り込む設定を、1つずつ保存値の上限に収める。今の値（先頭側）を残し、取り込んだ分を入る所まで。
 * 収まらなかったものは[FtutterStoreFit.leftOut]に「NG 12件」のように並べる（確認・結果の表示に使う）。
 */
internal fun ftutterFitPreferencesForStore(
    preferences: Map<String, String>,
    /** 今のタブ。タブは「取り込んだ値＋今の値」の順なので、入らない時は取り込んだタブから捨てる（再監査M8）。 */
    currentTabs: Set<String> = emptySet()
): FtutterStoreFit {
    val out = LinkedHashMap<String, String>()
    val leftOut = ArrayList<String>()
    preferences.forEach { (key, value) ->
        when (key) {
            FtutterKeys.NG_LIST -> {
                val rules = parseFtutterNgList(value)
                val fit = ftutterFitNgRules(emptyList(), rules)
                out[key] = encodeFtutterNgListForStore(fit.rules)
                if (fit.skipped > 0) leftOut += "NG ${fit.skipped}件"
            }
            FtutterKeys.TABS -> {
                // 取り込んだタブ（今のタブに無いもの）を後ろから捨て、それでも入らなければ残りを後ろから。
                val list = decodeFtutterList(value).orEmpty()
                var length = value.length
                val dropped = HashSet<Int>()
                for (i in list.indices.reversed()) {
                    if (length <= MAX_COMPAT_PREFERENCE_VALUE_CHARS) break
                    if (list[i] in currentTabs) continue
                    dropped += i
                    length -= list[i].length + 1
                }
                var kept = list.filterIndexed { i, _ -> i !in dropped }
                while (length > MAX_COMPAT_PREFERENCE_VALUE_CHARS && kept.isNotEmpty()) {
                    length -= kept.last().length + 1
                    kept = kept.dropLast(1)
                }
                out[key] = encodeFtutterList(kept)
                if (kept.size < list.size) leftOut += "タブ ${list.size - kept.size}件"
            }
            FtutterKeys.HIDDEN_THREADS -> {
                // 取り込みは「今の値＋取り込んだ値」の順に並ぶ。入らない分は後ろから。
                val list = decodeFtutterList(value).orEmpty()
                var length = value.length
                var keep = list.size
                while (length > MAX_COMPAT_PREFERENCE_VALUE_CHARS && keep > 0) {
                    keep--
                    length -= list[keep].length + 1
                }
                out[key] = encodeFtutterList(list.take(keep))
                if (keep < list.size) leftOut += "非表示にしたスレ ${list.size - keep}件"
            }
            FtutterKeys.DRAFTS -> {
                // 取り込みで足した下書きも、件数と大きさの決まりに合わせる（新しいものから残る）。
                val drafts = parseFtutterDrafts(value)
                val bounded = ftutterBoundDrafts(drafts)
                out[key] = bounded.encoded
                if (bounded.dropped > 0) leftOut += "下書き ${bounded.dropped}件"
            }
            else -> if (isValidCompatPreference(key, value)) out[key] = value else leftOut += "設定「${key.removePrefix("compat.ftutter.")}」"
        }
    }
    return FtutterStoreFit(out, leftOut)
}

/** What an `.ftt` import tells: what did not fit, and how many older marks the imported ones pushed out (re-audit L22). */
internal fun ftutterImportNotice(leftOut: List<String>, droppedMarks: Int, supplements: FtutterBackupSupplementResult? = null): String {
    val parts = buildList {
        if (leftOut.isNotEmpty()) add("保存できる量を超えたため、次は取り込めませんでした：${leftOut.joinToString("、")}")
        com.valoser.futacha.shared.compat.manualPostMarksDroppedNotice(droppedMarks)?.let(::add)
        supplements?.notices?.let(::addAll)
    }
    val result = if (supplements?.incomplete == true) "一部を取り込みました" else "取り込みました"
    return if (parts.isEmpty()) result else "$result（${parts.joinToString("。")}）"
}
