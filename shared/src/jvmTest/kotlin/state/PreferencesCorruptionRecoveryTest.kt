package com.valoser.futacha.shared.state

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PreferencesCorruptionRecoveryTest {
    private val directory: File = Files.createTempDirectory("prefs-recovery").toFile()
    private val file = File(directory, "settings.preferences_pb")
    private val boards = stringPreferencesKey("boards_json")
    private val ngWords = stringPreferencesKey("ng_words_json")
    private val lock = stringPreferencesKey(APP_LOCK_PASSWORD_HASH_PREFERENCE_KEY)
    private val privacy = booleanPreferencesKey("privacy_filter_enabled")
    private val lockHash = buildAppLockPasswordHash("1234", salt = "00112233445566778899aabbccddeeff")

    @AfterTest
    fun tearDown() {
        SettingsRecoveryNotices.acknowledge()
        directory.deleteRecursively()
    }

    private suspend fun <T> withStore(block: suspend (androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>) -> T): T {
        val job = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(
            corruptionHandler = recoveringPreferencesCorruptionHandler(settingsFile = { file }, nowMillis = { 42L }),
            scope = CoroutineScope(Dispatchers.IO + job)
        ) { file }
        return try { block(store) } finally { job.cancelAndJoin() }
    }

    @Test
    fun damagedFileKeepsReadableSettingsTheLockAndACopyAndPostsANotice() = runBlocking {
        withStore { store ->
            store.edit { prefs ->
                prefs[boards] = "[\"虹裏\"]"
                prefs[ngWords] = "[\"NG\"]"
                prefs[lock] = lockHash
                prefs[privacy] = true
            }
        }
        val original = file.readBytes()
        // Trailing garbage makes DataStore reject the whole file.
        file.writeBytes(original + byteArrayOf(0x7F, 0xFF.toByte(), 0xFF.toByte()))

        val recovered = withStore { store -> store.data.first() }
        assertEquals("[\"虹裏\"]", recovered[boards])
        assertEquals("[\"NG\"]", recovered[ngWords])
        assertEquals(lockHash, recovered[lock])
        assertEquals(true, recovered[privacy])

        val backup = File(directory, "settings.preferences_pb.corrupt-42")
        assertTrue(backup.isFile)
        assertTrue(backup.readBytes().size > original.size)
        val message = assertNotNull(SettingsRecoveryNotices.message.value)
        assertFalse("アプリロック" in message)
        assertTrue(backup.name in message)

        val marker = File(directory, "settings.preferences_pb.recovery-notice.txt")
        assertTrue(marker.isFile)
        SettingsRecoveryNotices.acknowledge()
        assertNull(SettingsRecoveryNotices.message.value)
        assertFalse(marker.isFile)
    }

    @Test
    fun aLostLockIsReportedAndTheNoticeSurvivesARestart() = runBlocking {
        file.writeBytes(byteArrayOf(0x0A, 0x7F) + APP_LOCK_PASSWORD_HASH_PREFERENCE_KEY.encodeToByteArray())
        val recovered = withStore { store -> store.data.first() }
        assertNull(recovered[lock])
        assertTrue("アプリロック" in assertNotNull(SettingsRecoveryNotices.message.value))

        // A new process: the in-memory notice is gone, the marker brings it back.
        val message = SettingsRecoveryNotices.message.value
        SettingsRecoveryNotices.post("stale") {}
        SettingsRecoveryNotices.acknowledge()
        restorePendingSettingsRecoveryNotice(file)
        assertEquals(message, SettingsRecoveryNotices.message.value)
    }
}
