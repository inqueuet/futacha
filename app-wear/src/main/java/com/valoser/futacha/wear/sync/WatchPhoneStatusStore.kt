package com.valoser.futacha.wear.sync

import android.content.Context
import com.valoser.futacha.shared.watch.estimateWatchClockOffsetMillis
import com.valoser.futacha.shared.watch.resolveWatchClockOffsetMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The phone clock offset learned from a snapshot request/response exchange. The watch clock can be
 * minutes off the phone's; every comparison with a phone-made timestamp (snapshot age, read-aloud
 * freshness, the command DataItem age the phone checks) then used the wrong time and the phone's
 * data was rejected or shown as stale. Without a fresh sample the offset is 0 and the watch behaves
 * as before.
 */
object WatchClockOffsetStore {
    private const val PREFS_NAME = "futacha_watch_clock"
    private const val KEY_OFFSET_MILLIS = "offset_millis"
    private const val KEY_LEARNED_AT_MILLIS = "learned_at_watch_millis"

    private class Learned(val offsetMillis: Long, val learnedAtWatchMillis: Long)

    @Volatile
    private var learned: Learned? = null

    @Volatile
    private var loaded = false

    private fun learnedState(context: Context): Learned? {
        if (!loaded) {
            synchronized(this) {
                if (!loaded) {
                    val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    learned = if (prefs.contains(KEY_OFFSET_MILLIS)) {
                        Learned(
                            offsetMillis = prefs.getLong(KEY_OFFSET_MILLIS, 0L),
                            learnedAtWatchMillis = prefs.getLong(KEY_LEARNED_AT_MILLIS, 0L)
                        )
                    } else {
                        null
                    }
                    loaded = true
                }
            }
        }
        return learned
    }

    /** The offset to add to the watch clock, or null when none is currently trusted. */
    fun offsetMillisOrNull(context: Context, watchNowMillis: Long = System.currentTimeMillis()): Long? {
        val state = learnedState(context)
        return resolveWatchClockOffsetMillis(
            learnedOffsetMillis = state?.offsetMillis,
            learnedAtWatchMillis = state?.learnedAtWatchMillis ?: 0L,
            watchNowMillis = watchNowMillis
        )
    }

    fun isCalibrated(context: Context): Boolean = offsetMillisOrNull(context) != null

    /** The phone's current time, as far as the watch can tell. */
    fun phoneNowMillis(context: Context, watchNowMillis: Long = System.currentTimeMillis()): Long =
        watchNowMillis + (offsetMillisOrNull(context, watchNowMillis) ?: 0L)

    /** Returns false when the sample was unusable (queued request, no echo, clock moved). */
    fun recordSample(
        context: Context,
        watchSentAtMillis: Long,
        phoneNowMillis: Long,
        watchReceivedAtMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val offset = estimateWatchClockOffsetMillis(
            watchSentAtMillis = watchSentAtMillis,
            watchReceivedAtMillis = watchReceivedAtMillis,
            phoneNowMillis = phoneNowMillis
        ) ?: return false
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_OFFSET_MILLIS, offset)
            .putLong(KEY_LEARNED_AT_MILLIS, watchReceivedAtMillis)
            .apply()
        synchronized(this) {
            learned = Learned(offset, watchReceivedAtMillis)
            loaded = true
        }
        return true
    }
}

/**
 * Whether the phone said it cannot serve the watch (only the ふたちゃ mode does). The watch
 * showed "要求しました" for commands the phone silently dropped.
 */
object WatchPhoneModeStore {
    private val unsupported = MutableStateFlow(false)

    fun observeUnsupported(): StateFlow<Boolean> = unsupported.asStateFlow()

    fun setUnsupported(value: Boolean) {
        unsupported.value = value
    }
}
