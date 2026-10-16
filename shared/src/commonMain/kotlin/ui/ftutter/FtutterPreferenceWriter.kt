package com.valoser.futacha.shared.ui.ftutter

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * このモードの設定の書き込み口（監査M2）。保存は頼まれた順に1つずつ行い、[edit]はその時に**保存されている値**へ変更を当てる。
 *
 * 画面が持つ設定（`preferences`）は保存の後から遅れて届くので、それを元に「1件消す・1件足す・1足す」を作ると、続けて押した時に
 * 1回目の変更が2回目の古い値で上書きされて戻る（ふたばー風の`editPreference`と同じ直し方）。[edit]の変換が null を返したら書かない。
 */
internal class FtutterPreferenceWriter(
    private val scope: CoroutineScope,
    private val load: suspend (String) -> String?,
    private val store: suspend (String, String) -> Unit,
    private val onFailure: (key: String, error: Throwable) -> Unit
) {
    private val mutex = Mutex()

    fun save(key: String, value: String) {
        run(key) { store(key, value) }
    }

    fun edit(key: String, transform: (String?) -> String?) {
        run(key) { transform(load(key))?.let { store(key, it) } }
    }

    /**
     * Several changes at once (an `.ftt` import), in their turn with the other writes: each applied to the value stored
     * at that moment and written together by [storeAll]. Returns once written; a failure is thrown to the caller (re-audit
     * L28: the import wrote beside this queue and could undo a read count written meanwhile).
     */
    suspend fun editAllNow(edits: Map<String, (String?) -> String?>, storeAll: suspend (Map<String, String>) -> Unit) {
        withContext(NonCancellable) {
            mutex.withLock {
                val values = edits.mapNotNull { (key, transform) -> transform(load(key))?.let { key to it } }.toMap()
                if (values.isNotEmpty()) storeAll(values)
            }
        }
    }

    // 頼まれた書き込みは、画面やモードが閉じても最後まで行う（再監査M5：モードを切り替えると、離れる時の既読数の保存が
    // 同じ破棄で取り消される[scope]の中で始まり、書かれなかった）。すぐに始めて、取り消されない形で書く。
    private fun run(key: String, block: suspend () -> Unit) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) {
                try {
                    mutex.withLock { block() }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    onFailure(key, error)
                }
            }
        }
    }
}
