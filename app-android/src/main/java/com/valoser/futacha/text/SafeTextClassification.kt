package com.valoser.futacha.text

import android.util.Log
import androidx.compose.ui.text.TextRange
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext as coroutineWithContext

/** Called only by the pinned Foundation Android call sites patched at build time.
 * Failures of this optional service must leave the ordinary text selection usable.
 */
object SafeTextClassification {
    @JvmStatic
    suspend fun <T> withContext(
        context: CoroutineContext,
        block: suspend CoroutineScope.() -> T
    ): T? = try {
        coroutineWithContext(context, block)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: RuntimeException) {
        // Never log the request, selected text, exception message or stack: a field can
        // contain credentials. VM errors and cancellation deliberately remain failures.
        Log.w("TextSelection", "Smart selection request skipped (${failure.javaClass.simpleName})")
        null
    }

    @JvmStatic
    @JvmName("checkedSelection")
    fun checkedSelection(start: Int, end: Int, text: CharSequence): TextRange {
        require(start >= 0 && end > start && end <= text.length) {
            "Invalid smart selection result"
        }
        return TextRange(start, end)
    }
}
