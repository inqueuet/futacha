package com.valoser.futacha

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.ExperienceProfile
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.model.AppIconVariant
import com.valoser.futacha.shared.model.BoardSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** Disabled in shipping variants; enabled only in Baseline Profile target variants. */
class BenchmarkFixtureReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SEED_BENCHMARK_FIXTURE) return
        val pendingResult = goAsync()
        Thread {
            try {
                if (intent.getBooleanExtra(EXTRA_RESTORE_LAUNCHER_DEFAULTS, false)) {
                    restoreLauncherDefaults(context)
                    pendingResult.resultCode = Activity.RESULT_OK
                    return@Thread
                }
                val app = context.applicationContext as FutachaApplication
                runBlocking(Dispatchers.IO) {
                    app.appStateStore.setBoards(listOf(TUTORIAL_BOARD))
                    val targetProfile = intent.getStringExtra(EXTRA_PROFILE)
                        ?.let(ExperienceProfile::valueOf)
                        ?: ExperienceProfile.FUTACHA
                    app.modeSwitchCoordinator.switchTo(
                        target = targetProfile,
                        preferredFutachaIcon = AppIconVariant.Current
                    ).getOrThrow()
                    app.compatibilityStore.upsertBenchmarkTutorialBoard(TUTORIAL_COMPAT_BOARD)
                }
                pendingResult.resultCode = Activity.RESULT_OK
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    private fun restoreLauncherDefaults(context: Context) {
        val packageManager = context.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val settings = LAUNCHER_ALIASES.map { className ->
                PackageManager.ComponentEnabledSetting(
                    ComponentName(context.packageName, className),
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                    PackageManager.DONT_KILL_APP
                )
            }
            packageManager.setComponentEnabledSettings(settings)
        } else {
            LAUNCHER_ALIASES.forEach { className ->
                packageManager.setComponentEnabledSetting(
                    ComponentName(context.packageName, className),
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                    PackageManager.DONT_KILL_APP
                )
            }
        }
    }

    private companion object {
        const val ACTION_SEED_BENCHMARK_FIXTURE =
            "com.valoser.futacha.action.SEED_BENCHMARK_FIXTURE"
        const val EXTRA_PROFILE = "profile"
        const val EXTRA_RESTORE_LAUNCHER_DEFAULTS = "restoreLauncherDefaults"

        val LAUNCHER_ALIASES = listOf(
            "com.valoser.futacha.MainActivityAliasCurrent",
            "com.valoser.futacha.MainActivityAliasClassic",
            "com.valoser.futacha.MainActivityAliasMidnight",
            "com.valoser.futacha.MainActivityAliasToshiakiCompat"
        )

        val TUTORIAL_BOARD = BoardSummary(
            id = "t",
            name = "チュートリアル＠ふたちゃ",
            category = "チュートリアル",
            url = "https://www.example.com/t/futaba.php",
            description = "チュートリアル",
            pinned = false
        )

        val TUTORIAL_COMPAT_BOARD = CompatBoard(
            key = compatBoardKey("https://img.2chan.net/b/"),
            name = "チュートリアル＠ふたちゃ",
            // Keep navigation URLs valid for the compatibility URL parser while
            // originalUrl selects the checked-in, network-free repository.
            canonicalUrl = "https://img.2chan.net/b/",
            originalUrl = "https://www.example.com/t/futaba.php",
            sortOrder = 0
        )
    }
}
