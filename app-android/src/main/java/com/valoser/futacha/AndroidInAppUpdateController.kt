package com.valoser.futacha

import android.app.Activity
import androidx.activity.ComponentActivity
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.valoser.futacha.shared.util.Logger

internal const val FLEXIBLE_UPDATE_STALENESS_DAYS = 0
internal const val IMMEDIATE_UPDATE_STALENESS_DAYS = 7
internal const val IMMEDIATE_UPDATE_PRIORITY = 4

internal enum class AndroidInAppUpdateKind {
    FLEXIBLE,
    IMMEDIATE
}

internal fun isAndroidInAppUpdateEmergency(
    stalenessDays: Int?,
    updatePriority: Int
): Boolean = updatePriority >= IMMEDIATE_UPDATE_PRIORITY ||
    (stalenessDays ?: -1) >= IMMEDIATE_UPDATE_STALENESS_DAYS

/**
 * Chooses the least disruptive Play update flow that is currently allowed.
 *
 * A null staleness value means Google Play cannot report the age, not that the
 * update is new, so it falls back to a flexible update instead of silently
 * ignoring an available release.
 */
internal fun selectAndroidInAppUpdateKind(
    stalenessDays: Int?,
    updatePriority: Int,
    flexibleAllowed: Boolean,
    immediateAllowed: Boolean
): AndroidInAppUpdateKind? {
    val needsImmediateUpdate = isAndroidInAppUpdateEmergency(stalenessDays, updatePriority)
    return when {
        immediateAllowed && needsImmediateUpdate -> AndroidInAppUpdateKind.IMMEDIATE
        flexibleAllowed && (
            needsImmediateUpdate || stalenessDays == null ||
                stalenessDays >= FLEXIBLE_UPDATE_STALENESS_DAYS
            ) -> AndroidInAppUpdateKind.FLEXIBLE
        else -> null
    }
}

/** Owns the Android-only Google Play in-app update lifecycle. */
internal class AndroidInAppUpdateController(
    private val activity: ComponentActivity,
    private val onFlexibleUpdateDownloaded: () -> Unit,
    private val onFlexibleUpdateCompletionFailed: () -> Unit,
    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(activity)
) {
    private var updateCheckInFlight = false
    private var newUpdateCheckPending = false
    private var pendingCheckAllowsOptionalUpdate = true
    private var flowInFlight = false

    private val installStateListener = InstallStateUpdatedListener { state ->
        when (state.installStatus()) {
            InstallStatus.DOWNLOADED -> {
                flowInFlight = false
                onFlexibleUpdateDownloaded()
            }
            InstallStatus.CANCELED,
            InstallStatus.FAILED,
            InstallStatus.INSTALLED -> flowInFlight = false
        }
    }

    fun register() {
        appUpdateManager.registerListener(installStateListener)
    }

    fun unregister() {
        appUpdateManager.unregisterListener(installStateListener)
    }

    /** Checks once per Activity instance for a newly available Play update. */
    fun checkForNewUpdate(allowOptionalUpdate: Boolean = true) {
        if (updateCheckInFlight) {
            newUpdateCheckPending = true
            pendingCheckAllowsOptionalUpdate = allowOptionalUpdate
            return
        }
        requestUpdateInfo(
            allowStartingNewUpdate = true,
            allowOptionalUpdate = allowOptionalUpdate
        )
    }

    /** Restores an interrupted immediate flow and detects a downloaded flexible update. */
    fun resumeUpdateIfNeeded() {
        requestUpdateInfo(
            allowStartingNewUpdate = false,
            allowOptionalUpdate = false
        )
    }

    private fun onUpdateFlowResult(resultCode: Int) {
        if (resultCode != Activity.RESULT_OK) {
            flowInFlight = false
            Logger.w(TAG, "In-app update flow ended with result code $resultCode")
        }
    }

    fun completeFlexibleUpdate() {
        appUpdateManager.completeUpdate()
            .addOnFailureListener { error ->
                Logger.w(TAG, "Could not complete flexible in-app update: ${error.message}")
                onFlexibleUpdateCompletionFailed()
            }
    }

    private fun requestUpdateInfo(
        allowStartingNewUpdate: Boolean,
        allowOptionalUpdate: Boolean
    ) {
        if (updateCheckInFlight || flowInFlight) return
        updateCheckInFlight = true
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { info ->
                updateCheckInFlight = false
                when {
                    info.installStatus() == InstallStatus.DOWNLOADED -> {
                        flowInFlight = false
                        onFlexibleUpdateDownloaded()
                    }
                    info.updateAvailability() ==
                        UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS ->
                        resumeImmediateUpdate(info)
                    allowStartingNewUpdate &&
                        info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE ->
                        startNewUpdate(info, allowOptionalUpdate)
                }
                runPendingNewUpdateCheck()
            }
            .addOnFailureListener { error ->
                updateCheckInFlight = false
                // This is expected for sideloaded/debug builds and devices without Play.
                Logger.w(TAG, "Google Play in-app update check unavailable: ${error.message}")
                runPendingNewUpdateCheck()
            }
    }

    private fun runPendingNewUpdateCheck() {
        if (!newUpdateCheckPending || flowInFlight) return
        newUpdateCheckPending = false
        val allowOptionalUpdate = pendingCheckAllowsOptionalUpdate
        pendingCheckAllowsOptionalUpdate = true
        requestUpdateInfo(
            allowStartingNewUpdate = true,
            allowOptionalUpdate = allowOptionalUpdate
        )
    }

    private fun startNewUpdate(info: AppUpdateInfo, allowOptionalUpdate: Boolean) {
        val isEmergencyUpdate = isAndroidInAppUpdateEmergency(
            stalenessDays = info.clientVersionStalenessDays(),
            updatePriority = info.updatePriority()
        )
        if (!allowOptionalUpdate && !isEmergencyUpdate) {
            return
        }
        val kind = selectAndroidInAppUpdateKind(
            stalenessDays = info.clientVersionStalenessDays(),
            updatePriority = info.updatePriority(),
            flexibleAllowed = info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE),
            immediateAllowed = info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
        ) ?: return
        startUpdate(info, kind)
    }

    private fun resumeImmediateUpdate(info: AppUpdateInfo) {
        if (info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
            startUpdate(info, AndroidInAppUpdateKind.IMMEDIATE)
        }
    }

    private fun startUpdate(info: AppUpdateInfo, kind: AndroidInAppUpdateKind) {
        val appUpdateType = when (kind) {
            AndroidInAppUpdateKind.FLEXIBLE -> AppUpdateType.FLEXIBLE
            AndroidInAppUpdateKind.IMMEDIATE -> AppUpdateType.IMMEDIATE
        }
        flowInFlight = true
        runCatching {
            appUpdateManager.startUpdateFlow(
                info,
                activity,
                AppUpdateOptions.newBuilder(appUpdateType).build()
            )
        }.onFailure { error ->
            flowInFlight = false
            Logger.w(TAG, "Could not start $kind in-app update: ${error.message}")
        }.onSuccess { resultTask ->
            resultTask
                .addOnSuccessListener(::onUpdateFlowResult)
                .addOnFailureListener { error ->
                    flowInFlight = false
                    Logger.w(TAG, "$kind in-app update failed: ${error.message}")
                }
        }
    }

    private companion object {
        const val TAG = "AndroidInAppUpdate"
    }
}
