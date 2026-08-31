package com.valoser.futacha.shared.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Looper
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

actual fun applyAppIconVariant(
    platformContext: Any?,
    variant: AppIconVariant
) {
    val context = (platformContext as? Context)?.applicationContext ?: return
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
    if (lastAppliedVariant == variant || persistedVariant == variant) {
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
