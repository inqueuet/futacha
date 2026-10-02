package com.valoser.futacha.shared.util

import android.app.Activity
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.valoser.futacha.shared.model.AppIconVariant
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.EmptyCoroutineContext

private const val ANDROID_ICON_MANAGER_TAG = "AndroidAppIconManager"
private const val MAIN_ACTIVITY_CLASS = "com.valoser.futacha.MainActivity"
private const val CURRENT_ALIAS_CLASS = "com.valoser.futacha.MainActivityAliasCurrent"
private const val CLASSIC_ALIAS_CLASS = "com.valoser.futacha.MainActivityAliasClassic"
private const val MIDNIGHT_ALIAS_CLASS = "com.valoser.futacha.MainActivityAliasMidnight"
private const val TOSHIAKI_COMPAT_ALIAS_CLASS = "com.valoser.futacha.MainActivityAliasToshiakiCompat"
private const val ICON_STATE_PREFS = "app_icon_state"
private const val ICON_STATE_KEY = "last_reconciled_variant"

@Volatile
private var lastAppliedVariant: AppIconVariant? = null
private val iconUpdateLock = Any()
@Volatile
private var lastRequestedVariant: AppIconVariant? = null

// A launcher alias change emits a package-changed event. From API 37 the
// system then closes the task that is on screen even with DONT_KILL_APP, so
// selecting an icon in settings closed the app (G-12). A request made from a
// visible Activity is held and applied once the app's tasks are hidden.
private const val FIRST_SDK_CLOSING_TASK_ON_ALIAS_CHANGE = 37
@Volatile
private var deferredVariant: AppIconVariant? = null

internal fun shouldDeferLauncherAliasChange(sdkInt: Int, requestedFromActivity: Boolean): Boolean =
    requestedFromActivity && sdkInt >= FIRST_SDK_CLOSING_TASK_ON_ALIAS_CHANGE

/**
 * True when a held icon change may be applied as the launcher Activity stops:
 * the stop is not a configuration change and no task of the app is visible
 * any more. A document or photo picker shown in the app's task keeps it
 * visible, so a pending result is never lost, and a root relaunched by a mode
 * switch keeps the new task visible while the old Activity finishes.
 */
internal fun shouldApplyDeferredIconOnStop(
    isChangingConfigurations: Boolean,
    anyAppTaskVisible: Boolean
): Boolean = !isChangingConfigurations && !anyAppTaskVisible

actual fun applyAppIconVariant(
    platformContext: Any?,
    variant: AppIconVariant
) {
    val context = (platformContext as? Context)?.applicationContext ?: return
    if (shouldDeferLauncherAliasChange(Build.VERSION.SDK_INT, platformContext is Activity)) {
        deferredVariant = variant
        return
    }
    synchronized(iconUpdateLock) {
        if (lastRequestedVariant == variant) return
        lastRequestedVariant = variant
    }
    if (Looper.getMainLooper().thread === Thread.currentThread()) {
        // PackageManager's component-state update can synchronously touch the
        // system package database. Do not put that binder work in the first
        // Compose frame.
        Dispatchers.IO.dispatch(EmptyCoroutineContext) {
            applyAppIconVariantNow(context, variant)
        }
        return
    }
    applyAppIconVariantNow(context, variant)
}

/**
 * Called from the launcher Activity's onStop. Applies an icon change held by
 * [applyAppIconVariant] once the app is no longer on screen.
 */
fun applyDeferredAppIconVariantOnStop(activity: Activity) {
    if (deferredVariant == null || activity.isChangingConfigurations) return
    if (tryApplyDeferredAppIconVariant(activity)) return
    // The task can still report itself visible while the stop is delivered.
    // Check once more shortly after, unless the Activity came back meanwhile.
    Handler(Looper.getMainLooper()).postDelayed({
        val restarted = (activity as? LifecycleOwner)
            ?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true
        if (!restarted) tryApplyDeferredAppIconVariant(activity)
    }, DEFERRED_ICON_RECHECK_DELAY_MILLIS)
}

private const val DEFERRED_ICON_RECHECK_DELAY_MILLIS = 1_000L

private fun tryApplyDeferredAppIconVariant(activity: Activity): Boolean {
    if (deferredVariant == null) return true
    val anyTaskVisible = runCatching {
        activity.getSystemService(ActivityManager::class.java)
            ?.appTasks
            ?.any { task -> runCatching { task.taskInfo?.isVisible == true }.getOrDefault(false) }
            ?: false
    }.getOrDefault(true)
    if (!shouldApplyDeferredIconOnStop(activity.isChangingConfigurations, anyTaskVisible)) {
        return false
    }
    val variant = deferredVariant ?: return true
    deferredVariant = null
    applyAppIconVariant(activity.applicationContext, variant)
    return true
}

private fun applyAppIconVariantNow(
    context: Context,
    variant: AppIconVariant
) {
    synchronized(iconUpdateLock) {
        if (lastRequestedVariant != variant) return
        val packageManager = context.packageManager
    val targetAlias = when (variant) {
        AppIconVariant.Current -> CURRENT_ALIAS_CLASS
        AppIconVariant.Classic -> CLASSIC_ALIAS_CLASS
        AppIconVariant.Midnight -> MIDNIGHT_ALIAS_CLASS
    }
    // FutachaApp can be recomposed by the profile/settings flows. Avoid
    // repeating the PackageManager binder/SQLite work when the selected icon
    // did not change. Persist this reconciliation across process restarts too;
    // re-writing an already-correct alias emits a package-changed event and can
    // make Android briefly hide the current Activity.
    val persistedVariant = context.getSharedPreferences(ICON_STATE_PREFS, Context.MODE_PRIVATE)
        .getString(ICON_STATE_KEY, null)
        ?.let { name -> runCatching { AppIconVariant.valueOf(name) }.getOrNull() }
    val legacyModeIconEnabled = packageManager.getComponentEnabledSetting(
        ComponentName(context.packageName, TOSHIAKI_COMPAT_ALIAS_CLASS)
    ) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    if (!legacyModeIconEnabled && (lastAppliedVariant == variant || persistedVariant == variant)) {
        lastAppliedVariant = variant
        return
    }
    val aliases = listOf(
        CURRENT_ALIAS_CLASS,
        CLASSIC_ALIAS_CLASS,
        MIDNIGHT_ALIAS_CLASS,
        TOSHIAKI_COMPAT_ALIAS_CLASS
    )
    val needsAliasUpdate = aliases.any { className ->
        val state = packageManager.getComponentEnabledSetting(
            ComponentName(context.packageName, className)
        )
        if (className == targetAlias) {
            state != PackageManager.COMPONENT_ENABLED_STATE_ENABLED &&
                !(state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT &&
                    className == CURRENT_ALIAS_CLASS)
        } else {
            state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED &&
                !(state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT &&
                    className != CURRENT_ALIAS_CLASS)
        }
    }
    if (!needsAliasUpdate) {
        lastAppliedVariant = variant
        return
    }
    val applied = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.setComponentEnabledSettings(
                aliases.map { className ->
                    PackageManager.ComponentEnabledSetting(
                        ComponentName(context.packageName, className),
                        if (className == targetAlias) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }
            )
        } else {
            // Keep at least one launcher entry alive if the process is interrupted.
            val targetComponent = ComponentName(context.packageName, targetAlias)
            packageManager.setComponentEnabledSetting(
                targetComponent,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            aliases.filterNot { it == targetAlias }.forEach { className ->
                packageManager.setComponentEnabledSetting(
                    ComponentName(context.packageName, className),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        }
    }.onFailure { error ->
        Logger.w(ANDROID_ICON_MANAGER_TAG, "Failed to update launcher aliases: ${error.message}")
    }.isSuccess
    runCatching {
        val mainActivityComponent = ComponentName(context.packageName, MAIN_ACTIVITY_CLASS)
        val newState = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        if (packageManager.getComponentEnabledSetting(mainActivityComponent) != newState) {
            packageManager.setComponentEnabledSetting(
                mainActivityComponent,
                newState,
                PackageManager.DONT_KILL_APP
            )
        }
    }.onFailure { error ->
        Logger.w(
            ANDROID_ICON_MANAGER_TAG,
            "Failed to normalize main activity component state: ${error.message}"
        )
    }
        if (applied && lastRequestedVariant == variant) {
            lastAppliedVariant = variant
            context.getSharedPreferences(ICON_STATE_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(ICON_STATE_KEY, variant.name)
                .apply()
        } else if (!applied && lastRequestedVariant == variant) {
            lastRequestedVariant = null
        }
    }
}
