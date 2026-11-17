package com.valoser.futacha.shared.state

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.valoser.futacha.shared.util.Logger
import java.io.File

private const val RECOVERY_LOG_TAG = "PreferencesRecovery"
private const val CORRUPT_BACKUP_INFIX = ".corrupt-"
private const val RECOVERY_NOTICE_SUFFIX = ".recovery-notice.txt"
private const val MAX_CORRUPT_BACKUPS = 3
private const val MAX_RECOVERY_INPUT_BYTES = 64L * 1024 * 1024

/**
 * Replaces a damaged settings file with every entry that can still be decoded
 * instead of empty preferences, keeps a copy of the damaged file, and leaves a
 * notice for the user (in particular when the app-lock password was lost).
 * [settingsFile] returns null when the file location is not known yet.
 */
internal fun recoveringPreferencesCorruptionHandler(
    settingsFile: () -> File?,
    nowMillis: () -> Long = System::currentTimeMillis
): ReplaceFileCorruptionHandler<Preferences> = ReplaceFileCorruptionHandler { error ->
    recoverCorruptPreferencesFile(settingsFile(), error, nowMillis())
}

internal fun recoverCorruptPreferencesFile(file: File?, error: CorruptionException, nowMillis: Long): Preferences {
    val bytes = file?.takeIf { it.isFile && it.length() <= MAX_RECOVERY_INPUT_BYTES }
        ?.let { runCatching { it.readBytes() }.getOrNull() }
    val backup = file?.let { runCatching { backUpCorruptFile(it, nowMillis) }.onFailure { failure ->
        Logger.e(RECOVERY_LOG_TAG, "Could not keep a copy of the damaged settings file", failure)
    }.getOrNull() }
    val recovery = bytes?.let(::recoverDamagedPreferences)
    val preferences = mutablePreferencesOf()
    recovery?.values?.forEach { (key, value) -> preferences.putRecovered(key, value) }
    Logger.e(
        RECOVERY_LOG_TAG,
        "Settings file was damaged; recovered ${recovery?.values?.size ?: 0} entries" +
            if (recovery?.appLockPasswordLost == true) " (app-lock password lost)" else "",
        error
    )
    if (file != null) {
        val message = buildSettingsRecoveryNoticeMessage(
            recoveredEntryCount = recovery?.values?.size ?: 0,
            appLockPasswordLost = recovery?.appLockPasswordLost == true,
            backupFileName = backup?.name
        )
        val noticeFile = recoveryNoticeFile(file)
        runCatching { noticeFile.writeText(message) }.onFailure { failure ->
            Logger.e(RECOVERY_LOG_TAG, "Could not persist the settings recovery notice", failure)
        }
        SettingsRecoveryNotices.post(message) { noticeFile.delete() }
    }
    return preferences
}

/** Re-posts a recovery notice that was not acknowledged before the process ended. */
internal fun restorePendingSettingsRecoveryNotice(settingsFile: File) {
    val noticeFile = recoveryNoticeFile(settingsFile)
    if (!noticeFile.isFile) return
    val message = runCatching { noticeFile.readText() }.getOrNull()?.takeIf(String::isNotBlank) ?: run {
        noticeFile.delete()
        return
    }
    SettingsRecoveryNotices.post(message) { noticeFile.delete() }
}

private fun recoveryNoticeFile(settingsFile: File) = File(settingsFile.path + RECOVERY_NOTICE_SUFFIX)

private fun backUpCorruptFile(file: File, nowMillis: Long): File? {
    if (!file.isFile) return null
    val backup = File(file.parentFile, "${file.name}$CORRUPT_BACKUP_INFIX$nowMillis")
    file.copyTo(backup, overwrite = true)
    file.parentFile?.listFiles { candidate -> candidate.name.startsWith("${file.name}$CORRUPT_BACKUP_INFIX") }
        ?.sortedByDescending { it.name.substringAfterLast(CORRUPT_BACKUP_INFIX).toLongOrNull() ?: 0L }
        ?.drop(MAX_CORRUPT_BACKUPS)
        ?.forEach { it.delete() }
    return backup
}

private fun MutablePreferences.putRecovered(key: String, value: RecoveredPreference) {
    when (value) {
        is RecoveredPreference.BooleanValue -> this[booleanPreferencesKey(key)] = value.value
        is RecoveredPreference.StringValue -> this[stringPreferencesKey(key)] = value.value
        is RecoveredPreference.IntValue -> this[intPreferencesKey(key)] = value.value
        is RecoveredPreference.LongValue -> this[longPreferencesKey(key)] = value.value
        is RecoveredPreference.FloatValue -> this[floatPreferencesKey(key)] = value.value
        is RecoveredPreference.DoubleValue -> this[doublePreferencesKey(key)] = value.value
        is RecoveredPreference.StringSetValue -> this[stringSetPreferencesKey(key)] = value.value
        is RecoveredPreference.BytesValue -> this[byteArrayPreferencesKey(key)] = value.value
    }
}
