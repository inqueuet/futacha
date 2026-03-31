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
    val targetAliasClass = when (variant) {
        AppIconVariant.Current -> CURRENT_ALIAS_CLASS
        AppIconVariant.Classic -> CLASSIC_ALIAS_CLASS
        AppIconVariant.Midnight -> MIDNIGHT_ALIAS_CLASS
    }
    val componentClassNames = listOf(
        CURRENT_ALIAS_CLASS,
        CLASSIC_ALIAS_CLASS,
        MIDNIGHT_ALIAS_CLASS
    )
    componentClassNames.forEach { className ->
        val newState = if (className == targetAliasClass) {
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
        if (packageManager.getComponentEnabledSetting(mainActivityComponent) !=
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        ) {
            packageManager.setComponentEnabledSetting(
                mainActivityComponent,
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
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
