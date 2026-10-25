package com.valoser.futacha.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.valoser.futacha.shared.compat.COMPAT_WATCH_NOTIFY_KEY
import com.valoser.futacha.shared.compat.canonicalizeThreadUrl
import com.valoser.futacha.shared.compat.compatWatchEnabled
import com.valoser.futacha.shared.network.BoardUrlResolver
import com.valoser.futacha.shared.service.CatalogWatchAlertMatch
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIApplicationState
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatus
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

private const val IOS_NOTIFICATION_SETTINGS_TIMEOUT_MILLIS = 5_000L

/**
 * Posts watch-match notifications from background work (G-10).
 *
 * Background work only reads the current authorization: asking from a
 * BGTask could not show the prompt and its reply could stall while the ledger
 * lock was held. A match is recorded only after its notification was
 * scheduled, so an unauthorized or failed post is retried on a later run, and
 * posting plus recording run without cancellation so an expiring task neither
 * loses nor repeats it.
 */
internal class IosWatchAlertNotificationDispatcher(
    private val isAuthorized: suspend () -> Boolean,
    private val filterNew: (List<CatalogWatchAlertMatch>) -> List<CatalogWatchAlertMatch>,
    private val markNotified: (List<CatalogWatchAlertMatch>) -> Unit,
    /** Returns true once the notification was scheduled; must bound its own wait. */
    private val post: suspend (List<CatalogWatchAlertMatch>) -> Boolean
) {
    /** Guards the read → post → record update of the single ledger. */
    private val ledgerMutex = Mutex()

    /** Returns the matches that were notified now. */
    suspend fun notifyNew(matches: List<CatalogWatchAlertMatch>): List<CatalogWatchAlertMatch> {
        if (matches.isEmpty()) return emptyList()
        // Checked before taking the lock: the ledger is never held across this await.
        if (!isAuthorized()) return emptyList()
        return ledgerMutex.withLock {
            val fresh = filterNew(matches)
            if (fresh.isEmpty()) return@withLock emptyList()
            val posted = withContext(NonCancellable) {
                post(fresh).also { scheduled -> if (scheduled) markNotified(fresh) }
            }
            if (posted) fresh else emptyList()
        }
    }
}

/** userInfo key of the thread a watch notification for one match opens (L119). */
internal const val IOS_WATCH_ALERT_THREAD_URL_KEY = "threadUrl"

/**
 * The thread one watch match opens, as Android and Desktop do: an official 2chan thread URL, or null when none can
 * be made (a tap then just opens the app).
 */
internal fun iosWatchAlertThreadUrl(match: CatalogWatchAlertMatch): String? {
    val threadId = match.threadId.trim()
    if (threadId.isEmpty() || !threadId.all { it in '0'..'9' }) return null
    return runCatching { BoardUrlResolver.resolveThreadUrl(match.boardUrl, threadId) }.getOrNull()
        ?.let { url -> canonicalizeThreadUrl(url)?.canonicalUrl }
}

/**
 * The notification for [matches]. One match carries its thread under [IOS_WATCH_ALERT_THREAD_URL_KEY], so a tap
 * opens it (L119: the notification named no thread and a tap only brought the app forward); several matches open
 * the app, as on Android.
 */
internal fun buildIosWatchAlertNotificationContent(matches: List<CatalogWatchAlertMatch>): UNMutableNotificationContent {
    val first = matches.first()
    val title = if (matches.size == 1) {
        "監視ワードに一致しました"
    } else {
        "監視ワードに ${matches.size} 件一致しました"
    }
    val body = if (matches.size == 1) {
        "${first.boardName}: ${first.title}"
    } else {
        "${first.boardName}: ${first.title} ほか"
    }
    val threadUrl = matches.singleOrNull()?.let(::iosWatchAlertThreadUrl)
    return UNMutableNotificationContent().apply {
        setTitle(title)
        setBody(body)
        setSound(UNNotificationSound.defaultSound())
        if (threadUrl != null) setUserInfo(mapOf<Any?, Any?>(IOS_WATCH_ALERT_THREAD_URL_KEY to threadUrl))
    }
}

/** The thread a tapped notification names, if any; see [buildIosWatchAlertNotificationContent]. */
internal fun iosNotificationThreadUrl(userInfo: Map<Any?, *>): String? =
    (userInfo[IOS_WATCH_ALERT_THREAD_URL_KEY] as? String)?.takeIf(String::isNotBlank)

internal fun isIosNotificationAuthorizationUsable(status: UNAuthorizationStatus): Boolean =
    status == UNAuthorizationStatusAuthorized ||
        status == UNAuthorizationStatusProvisional ||
        status == UNAuthorizationStatusEphemeral

/** Reads the current authorization without prompting; null when it did not answer in time. */
internal suspend fun currentIosNotificationAuthorizationStatus(): UNAuthorizationStatus? =
    withTimeoutOrNull(IOS_NOTIFICATION_SETTINGS_TIMEOUT_MILLIS) {
        suspendCancellableCoroutine { continuation ->
            UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler { settings ->
                if (continuation.isActive) continuation.resume(settings?.authorizationStatus)
            }
        }
    }

internal suspend fun isIosNotificationAuthorized(): Boolean =
    currentIosNotificationAuthorizationStatus()?.let(::isIosNotificationAuthorizationUsable) == true

/** Whether any enabled watch path would post a notification from background work. */
internal fun iosWatchNotificationsWanted(
    futachaWatchAlertEnabled: Boolean,
    compatPreferences: Map<String, String>
): Boolean = futachaWatchAlertEnabled ||
    (compatWatchEnabled(compatPreferences) && compatPreferences[COMPAT_WATCH_NOTIFY_KEY] != "OFF")

/**
 * Asks for notification permission from the visible app only, and only while
 * it is still undetermined. Background runs never ask (see
 * [IosWatchAlertNotificationDispatcher]), so this is where a user who enabled a
 * watch notification gets the system prompt.
 */
internal suspend fun requestIosNotificationAuthorizationIfUndetermined() {
    val status = currentIosNotificationAuthorizationStatus() ?: return
    if (status != UNAuthorizationStatusNotDetermined) return
    val isActive = withContext(kotlinx.coroutines.Dispatchers.Main) {
        UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateActive
    }
    if (!isActive) return
    UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
        options = UNAuthorizationOptionAlert or UNAuthorizationOptionSound
    ) { granted, error ->
        if (error != null) {
            Logger.w("MainViewController", "Failed to request iOS notification authorization: ${error.localizedDescription}")
        } else {
            Logger.d("MainViewController", "iOS notification authorization granted=$granted")
        }
    }
}

/**
 * Whether the app is active, starting from the real state: a scene created
 * during a background launch must not count as visible until it is activated.
 */
@Composable
internal fun rememberIosApplicationActive(): Boolean {
    var active by remember {
        mutableStateOf(UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateActive)
    }
    DisposableEffect(Unit) {
        val center = NSNotificationCenter.defaultCenter
        val activeObserver = center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, null) { active = true }
        val resignObserver = center.addObserverForName(UIApplicationWillResignActiveNotification, null, null) { active = false }
        onDispose {
            center.removeObserver(activeObserver)
            center.removeObserver(resignObserver)
        }
    }
    return active
}
