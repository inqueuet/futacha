package com.valoser.futacha

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.valoser.futacha.shared.util.Logger
import java.util.concurrent.atomic.AtomicBoolean

internal object WatchDataLayerAvailability {
    private const val TAG = "WatchDataLayerAvailability"
    private const val CHINA_WEAR_COMPANION_PACKAGE = "com.google.android.wearable.app.cn"
    private val hasLoggedInspectionFailure = AtomicBoolean(false)
    private val hasLoggedMissingSignatures = AtomicBoolean(false)

    fun canUseWearableApis(context: Context): Boolean {
        return !hasChinaWearCompanionWithMissingSignatures(context.packageManager)
    }

    @Suppress("DEPRECATION")
    private fun hasChinaWearCompanionWithMissingSignatures(packageManager: PackageManager): Boolean {
        val packageInfo = try {
            packageManager.getSignaturePackageInfo(CHINA_WEAR_COMPANION_PACKAGE)
        } catch (_: PackageManager.NameNotFoundException) {
            return false
        } catch (error: RuntimeException) {
            if (hasLoggedInspectionFailure.compareAndSet(false, true)) {
                Logger.w(
                    TAG,
                    "Skipping Wear OS sync because wearable companion package cannot be inspected: ${error.message}"
                )
            }
            return true
        }

        return if (packageInfo.signatures.isNullOrEmpty()) {
            if (hasLoggedMissingSignatures.compareAndSet(false, true)) {
                Logger.w(
                    TAG,
                    "Skipping Wear OS sync because $CHINA_WEAR_COMPANION_PACKAGE has no signatures; " +
                        "play-services-wearable 20.0.1 crashes while verifying this package state."
                )
            }
            true
        } else {
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun PackageManager.getSignaturePackageInfo(packageName: String): PackageInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNATURES.toLong())
            )
        } else {
            getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
        }
    }
}
