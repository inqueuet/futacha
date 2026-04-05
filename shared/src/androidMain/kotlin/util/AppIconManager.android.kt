package com.valoser.futacha.shared.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.valoser.futacha.shared.model.AppIconVariant

private const val ANDROID_ICON_MANAGER_TAG = "AndroidAppIconManager"
private const val MAIN_ACTIVITY_CLASS = "com.valoser.futacha.MainActivity"
private const val CURRENT_ALIAS_CLASS = "com.valoser.futacha.MainActivityAliasCurrent"
private const val CLASSIC_ALIAS_CLASS = "com.valoser.futacha.MainActivityAliasClassic"
private const val MIDNIGHT_ALIAS_CLASS = "com.valoser.futacha.MainActivityAliasMidnight"

actual fun applyAppIconVariant(
    platformContext: Any?,
    variant: AppIconVariant
) {
    val context = (platformContext as? Context)?.applicationContext ?: return
    val packageManager = context.packageManager
    listOf(CURRENT_ALIAS_CLASS, CLASSIC_ALIAS_CLASS, MIDNIGHT_ALIAS_CLASS).forEach { className ->
        val shouldEnable = when (variant) {
            AppIconVariant.Current -> className == CURRENT_ALIAS_CLASS
            AppIconVariant.Classic -> className == CLASSIC_ALIAS_CLASS
            AppIconVariant.Midnight -> className == MIDNIGHT_ALIAS_CLASS
        }
        val newState = if (shouldEnable) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        val componentName = ComponentName(context.packageName, className)
        runCatching {
            if (packageManager.getComponentEnabledSetting(componentName) != newState) {
                packageManager.setComponentEnabledSetting(
                    componentName,
                    newState,
                    PackageManager.DONT_KILL_APP
                )
            }
        }.onFailure { error ->
            Logger.w(
                ANDROID_ICON_MANAGER_TAG,
                "Failed to update icon alias $className: ${error.message}"
            )
        }
    }
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
}
