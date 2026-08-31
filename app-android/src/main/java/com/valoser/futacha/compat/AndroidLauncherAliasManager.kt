package com.valoser.futacha.compat

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.valoser.futacha.shared.compat.ExperienceProfile
import com.valoser.futacha.shared.model.AppIconVariant

fun interface AndroidLauncherAliasReconciler {
    fun reconcile(profile: ExperienceProfile, preferredFutachaIcon: AppIconVariant)
}

class AndroidLauncherAliasManager(context: Context) : AndroidLauncherAliasReconciler {
    private val context = context.applicationContext
    private val packageManager = context.packageManager

    override fun reconcile(profile: ExperienceProfile, preferredFutachaIcon: AppIconVariant) {
        val target = when (profile) {
            ExperienceProfile.TOSHIAKI_COMPAT -> TOSHIAKI_COMPAT_ALIAS
            ExperienceProfile.FUTACHA -> when (preferredFutachaIcon) {
                AppIconVariant.Current -> CURRENT_ALIAS
                AppIconVariant.Classic -> CLASSIC_ALIAS
                AppIconVariant.Midnight -> MIDNIGHT_ALIAS
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val needsUpdate = ALL_ALIASES.any { alias ->
                val state = packageManager.getComponentEnabledSetting(
                    ComponentName(context.packageName, alias)
                )
                if (alias == target) {
                    state != PackageManager.COMPONENT_ENABLED_STATE_ENABLED &&
                        !(state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && alias == CURRENT_ALIAS)
                } else {
                    state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED &&
                        !(state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && alias != CURRENT_ALIAS)
                }
            }
            if (!needsUpdate) return
            val settings = ALL_ALIASES.map { alias ->
                PackageManager.ComponentEnabledSetting(
                    ComponentName(context.packageName, alias),
                    if (alias == target) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
            packageManager.setComponentEnabledSettings(settings)
        } else {
            setAliasState(target, enabled = true)
            ALL_ALIASES.filterNot { it == target }.forEach { setAliasState(it, enabled = false) }
        }
    }

    fun enabledAliases(): Set<String> = ALL_ALIASES.filterTo(mutableSetOf()) { alias ->
        val state = packageManager.getComponentEnabledSetting(ComponentName(context.packageName, alias))
        when (state) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> alias == CURRENT_ALIAS
            else -> false
        }
    }

    private fun setAliasState(alias: String, enabled: Boolean) {
        val component = ComponentName(context.packageName, alias)
        val targetState = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        if (packageManager.getComponentEnabledSetting(component) != targetState) {
            packageManager.setComponentEnabledSetting(component, targetState, PackageManager.DONT_KILL_APP)
        }
    }

    companion object {
        const val CURRENT_ALIAS = "com.valoser.futacha.MainActivityAliasCurrent"
        const val CLASSIC_ALIAS = "com.valoser.futacha.MainActivityAliasClassic"
        const val MIDNIGHT_ALIAS = "com.valoser.futacha.MainActivityAliasMidnight"
        const val TOSHIAKI_COMPAT_ALIAS = "com.valoser.futacha.MainActivityAliasToshiakiCompat"
        val ALL_ALIASES = listOf(CURRENT_ALIAS, CLASSIC_ALIAS, MIDNIGHT_ALIAS, TOSHIAKI_COMPAT_ALIAS)
    }
}
