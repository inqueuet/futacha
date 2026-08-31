package com.valoser.futacha

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Process
import com.valoser.futacha.compat.AndroidExperienceProfileStore
import com.valoser.futacha.shared.compat.ExperienceProfile
import com.valoser.futacha.shared.compat.ModeSwitchPhase
import java.io.File
import java.io.FileOutputStream
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking

/**
 * Debug-only fault injector which kills its own Linux process at a requested durable
 * profile-switch phase. The production journal is used unchanged; only its preference
 * namespace is isolated from the installed app.
 */
class ProfileSwitchCrashTestService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val preferencesName = intent?.getStringExtra(EXTRA_PREFERENCES_NAME)
        val requestedPhase = intent?.getStringExtra(EXTRA_PHASE)
            ?.let { runCatching { ModeSwitchPhase.valueOf(it) }.getOrNull() }
        val markerName = intent?.getStringExtra(EXTRA_MARKER_NAME)
        if (preferencesName.isNullOrBlank() || requestedPhase == null || markerName.isNullOrBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        thread(name = "profile-switch-crash-injector") {
            runCrashSequence(preferencesName, requestedPhase, markerName, startId)
        }
        return START_NOT_STICKY
    }

    private fun runCrashSequence(
        preferencesName: String,
        requestedPhase: ModeSwitchPhase,
        markerName: String,
        startId: Int
    ) = runBlocking {
        val marker = File(noBackupFilesDir, markerName)
        try {
            val store = AndroidExperienceProfileStore(applicationContext, preferencesName)
            var journal = store.beginSwitchWithCommitBarrier(
                ExperienceProfile.FUTACHA,
                ExperienceProfile.TOSHIAKI_COMPAT
            )
            if (requestedPhase >= ModeSwitchPhase.OLD_PROFILE_QUIESCED) {
                journal = store.advanceSwitch(journal, ModeSwitchPhase.OLD_PROFILE_QUIESCED)
            }
            if (requestedPhase >= ModeSwitchPhase.PROFILE_PERSISTED) {
                journal = store.persistRequestedProfileWithCommitBarrier(journal)
            }
            if (requestedPhase >= ModeSwitchPhase.LAUNCHER_ALIAS_UPDATED) {
                journal = store.advanceSwitch(journal, ModeSwitchPhase.LAUNCHER_ALIAS_UPDATED)
            }
            writeMarker(
                marker,
                "phase=${journal.phase.name} active=${store.readActiveProfile().persistedValue} " +
                    "generation=${store.readGeneration()} pid=${Process.myPid()}"
            )
            Thread.sleep(KILL_DELAY_MILLIS)
            Process.killProcess(Process.myPid())
        } catch (error: Throwable) {
            writeMarker(marker, "error=${error.javaClass.name}:${error.message}")
            stopSelf(startId)
        }
    }

    private fun writeMarker(file: File, value: String) {
        FileOutputStream(file).use { output ->
            output.write(value.encodeToByteArray())
            output.fd.sync()
        }
    }

    companion object {
        const val EXTRA_PREFERENCES_NAME = "preferences_name"
        const val EXTRA_PHASE = "phase"
        const val EXTRA_MARKER_NAME = "marker_name"
        private const val KILL_DELAY_MILLIS = 750L
    }
}
