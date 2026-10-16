package com.valoser.futacha.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.produceState
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.withContext

/**
 * 画面より長く持つ、最後に作った値とその材料（2026-10-15の再監査M9〜M11）。
 *
 * 履歴（最大2万件）のように大きい入力から作る表は、以前「最初の1回はその場で作り、その後は解析用のスレッドで」作っていた。
 * それでは画面を開き直すたびに最初の1回がメインスレッドで走り、さらに「最初の材料と同じなら作らない」という省略のせいで、
 * 条件を途中で変えてから最初の条件に戻すと、途中の条件の結果が残った。これは画面の外（ホスト）で持ち、作った値をその材料と
 * 一緒に覚える。
 */
@Stable
internal class OffMainMemo<K, T> {
    internal var entry: Pair<K, T>? = null
}

/**
 * [key]から[compute]で作る値を、解析用のスレッドで作って返す。
 * - [memo]に同じ材料の値があれば、それをすぐ使う（開き直してもメインで作らない）。
 * - 材料が違えば必ず作り直す。できるまでは、[usable]が許す前の値（無ければ[empty]）を出す。
 */
@Composable
internal fun <K, T> rememberOffMain(
    memo: OffMainMemo<K, T>,
    key: K,
    empty: T,
    usable: (held: K, wanted: K) -> Boolean = { _, _ -> true },
    /** [ensureActive] throws once the inputs changed again, so a long build gives way (re-audit L13). */
    compute: (key: K, ensureActive: () -> Unit) -> T
): T {
    val held = memo.entry?.takeIf { it.first == key || usable(it.first, key) }
    val state = produceState(held, key) {
        if (value?.first == key) return@produceState
        memo.entry?.takeIf { it.first == key }?.let { value = it; return@produceState }
        val made = withContext(AppDispatchers.parsing) {
            val job = kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]
            compute(key) { job?.let { if (!it.isActive) throw kotlinx.coroutines.CancellationException("inputs changed") } }
        }
        val entry = key to made
        memo.entry = entry
        value = entry
    }
    // 材料が変わった直後（作り直しが始まる前）でも、別の材料の値で[usable]が許さないものは出さない。
    val shown = state.value?.takeIf { it.first == key || usable(it.first, key) }
    return shown?.second ?: empty
}

/** 材料の比べ方：大きい表（履歴など）は同じものかどうかだけで比べる（中身を全部比べない）。 */
internal class SameInstance<T>(val value: T) {
    override fun equals(other: Any?): Boolean = other is SameInstance<*> && other.value === value
    override fun hashCode(): Int = 0
}
