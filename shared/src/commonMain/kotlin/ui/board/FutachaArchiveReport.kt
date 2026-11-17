package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.valoser.futacha.shared.analytics.AnalyticsTracker
import com.valoser.futacha.shared.compat.ARCHIVE_REPORT_ENABLED_PREFERENCE_KEY
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.isDesktop
import com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * "閲覧スレ通知" for ふたちゃ and ふたばー風 (としあき(仮) has had it from the start): the address of a thread that was
 * read from the board is put in the shared outbox, and the host's sender tells the archive service about it later. Only
 * the thread's address goes out; the body, the pictures, any user or device id do not. The outbox itself decides which
 * boards take part ([com.valoser.futacha.shared.compat.normalizeArchiveReportThreadUrl]).
 *
 * The one switch is the shared key [ARCHIVE_REPORT_ENABLED_PREFERENCE_KEY]: nothing stored (or anything but "OFF") is on,
 * the same in every mode. The desktop app has no sender, so nothing is noted there (it would only pile up).
 */
internal fun viewedThreadArchiveReportDue(enabledPreference: String?, liveFetch: Boolean, desktop: Boolean): Boolean =
    enabledPreference != "OFF" && liveFetch && !desktop

/**
 * Whether the page ふたちゃ accepted was read from the board just now: not the device's copy shown while it loads
 * ([isCachedPage]), not an archive's copy ([isArchived]), not a read-only file (an opened MHT, [staticView]) and not the
 * page "更新前に戻す" brought back ([restoring]).
 */
internal fun futachaArchiveReportLive(isCachedPage: Boolean, isArchived: Boolean, staticView: Boolean, restoring: Boolean): Boolean =
    !isCachedPage && !isArchived && !staticView && !restoring

/**
 * Notes [threadUrl] in the outbox when [viewedThreadArchiveReportDue] says so; [onEnqueued] gets the number waiting to be
 * sent (the host schedules its sender). A store failure is only logged: reading the thread must not fail for this.
 */
internal suspend fun enqueueViewedThreadArchiveReport(
    store: CompatibilityStore,
    threadUrl: String,
    enabledPreference: String?,
    liveFetch: Boolean,
    nowEpochMillis: Long,
    onEnqueued: ((Int) -> Unit)?,
    desktop: Boolean = isDesktop()
): Boolean {
    if (!viewedThreadArchiveReportDue(enabledPreference, liveFetch, desktop)) return false
    var noted = false
    runSuspendCatchingPreservingCancellation { store.enqueueArchiveReport(threadUrl, nowEpochMillis) }
        .onSuccess { queued ->
            noted = queued.inserted
            if (queued.inserted || queued.sendableCount > 0) onEnqueued?.invoke(queued.sendableCount)
        }
        .onFailure { Logger.e("FutachaArchiveReport", "Failed to note a viewed thread", it) }
    return noted
}

/** The stored switch once the store has loaded ("OFF" = off); a failed read counts as off, so nothing is sent by mistake. */
internal suspend fun storedArchiveReportSwitch(store: CompatibilityStore): String? = try {
    store.isLoaded.first { it }
    store.loadPreference(ARCHIVE_REPORT_ENABLED_PREFERENCE_KEY)
} catch (cancelled: kotlinx.coroutines.CancellationException) {
    throw cancelled
} catch (error: Throwable) {
    Logger.e("FutachaArchiveReport", "Failed to read the archive report switch", error)
    "OFF"
}

/** The host's sender of the notes, handed down to ふたちゃ's thread screens through [FutachaSharedFeatures]. */
internal class FutachaArchiveReportHost {
    /** The number waiting to be sent after a thread was noted; null where the host sends nothing (the default). */
    var onEnqueued: ((Int) -> Unit)? = null
    /** The switch was changed (the host starts or stops its sender). */
    var onEnabledChanged: (Boolean) -> Unit = {}
}

/** ふたちゃ's thread screen: notes the thread after a page was accepted (see [FutachaThreadFeatureHost]). */
internal suspend fun FutachaSharedFeatures.noteViewedThreadForArchive(
    sourceUrl: String,
    isCachedPage: Boolean,
    isArchived: Boolean,
    staticView: Boolean,
    restoring: Boolean,
    nowEpochMillis: Long
): Boolean = enqueueViewedThreadArchiveReport(
    store = store,
    threadUrl = sourceUrl,
    // Read from the store, not from the screen's copy of the settings: that copy is empty until loaded, and empty reads as
    // "on", so a thread opened before the settings arrived was noted although the switch was off (audit L27).
    enabledPreference = storedArchiveReportSwitch(store),
    liveFetch = futachaArchiveReportLive(isCachedPage, isArchived, staticView, restoring),
    nowEpochMillis = nowEpochMillis,
    onEnqueued = archiveReport.onEnqueued
)

internal const val VIEWED_THREAD_ARCHIVE_REPORT_LABEL = "閲覧したスレのURLを過去ログ保存サービスへ通知"
internal const val VIEWED_THREAD_ARCHIVE_REPORT_DESCRIPTION =
    "取得に成功して表示した対応板のスレURLだけを通知します。本文・画像・利用者ID・端末IDは送信しません。" +
        "ふたちゃ・としあき(仮)・ふたばー風で共通の設定です。"

/**
 * The switch at the end of ふたちゃ's "バックグラウンド・通信" settings (nothing on the desktop, which sends nothing). It
 * writes the shared key, so turning it off here turns it off in every mode.
 */
@Composable
internal fun FutachaArchiveReportSettingRow() {
    val features = LocalFutachaSharedFeatures.current ?: return
    if (isDesktop()) return
    val scope = rememberCoroutineScope()
    // Only this switch's own value is watched (re-audit L33: reading the whole settings map composed this row again on
    // every write of any setting).
    val storedKey by remember(features) {
        androidx.compose.runtime.derivedStateOf { features.preferences[ARCHIVE_REPORT_ENABLED_PREFERENCE_KEY] }
    }
    // The switch shows the stored value once it is read (audit L27: it showed "on" for a moment before the settings arrived).
    // A read that failed is shown as such, not as "off" (re-audit L33); the sending side still treats it as off.
    val stored by androidx.compose.runtime.produceState<Pair<Boolean, Result<String?>>>(false to Result.success(null), storedKey) {
        value = true to try {
            features.store.isLoaded.first { it }
            Result.success(features.store.loadPreference(ARCHIVE_REPORT_ENABLED_PREFERENCE_KEY))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Logger.e("FutachaArchiveReport", "Failed to read the archive report switch", error)
            Result.failure(error)
        }
    }
    var saving by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val readFailed = stored.second.isFailure
    val enabled = stored.second.getOrNull() != "OFF" && !readFailed
    HorizontalDivider()
    ListItem(
        headlineContent = { Text(VIEWED_THREAD_ARCHIVE_REPORT_LABEL) },
        supportingContent = {
            Column {
                Text(
                    text = VIEWED_THREAD_ARCHIVE_REPORT_DESCRIPTION,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                (problem ?: if (readFailed) "設定を読み込めませんでした" else null)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("futacha-archive-report-problem"))
                }
            }
        },
        trailingContent = {
            Switch(
                checked = enabled,
                enabled = !saving && stored.first && !readFailed,
                modifier = Modifier.testTag("futacha-archive-report-switch"),
                onCheckedChange = { next ->
                    AnalyticsTracker.uiControl(
                        "global_setting_toggle",
                        "閲覧スレ通知を${if (next) "ON" else "OFF"}にする",
                        mapOf("setting_label" to "閲覧スレ通知", "setting_value" to if (next) "ON" else "OFF")
                    )
                    saving = true
                    problem = null
                    scope.launch {
                        try {
                            features.store.savePreference(ARCHIVE_REPORT_ENABLED_PREFERENCE_KEY, if (next) "ON" else "OFF")
                            features.archiveReport.onEnabledChanged(next)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            Logger.e("FutachaArchiveReport", "Failed to save the archive report switch", error)
                            // Said, not left looking unchanged (re-audit L33).
                            problem = "保存できませんでした"
                        } finally {
                            saving = false
                        }
                    }
                }
            )
        },
        modifier = Modifier.fillMaxWidth().testTag("futacha-archive-report-row")
    )
}
