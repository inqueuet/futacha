package com.valoser.futacha.shared.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DamagedPreferencesRecoveryTest {
    private val lockHash = buildAppLockPasswordHash("1234", salt = "0123456789abcdef0123456789abcdef")

    private fun varint(value: Int): ByteArray {
        val out = mutableListOf<Byte>()
        var remaining = value
        while (remaining >= 0x80) {
            out += ((remaining and 0x7f) or 0x80).toByte()
            remaining = remaining ushr 7
        }
        out += remaining.toByte()
        return out.toByteArray()
    }

    private fun field(tag: Int, payload: ByteArray) = byteArrayOf(tag.toByte()) + varint(payload.size) + payload

    private fun entry(key: String, value: ByteArray) =
        field(0x0A, field(0x0A, key.encodeToByteArray()) + field(0x12, value))

    private fun stringEntry(key: String, value: String) = entry(key, field(0x2A, value.encodeToByteArray()))

    private fun boolEntry(key: String, value: Boolean) = entry(key, byteArrayOf(0x08, if (value) 1 else 0))

    private fun string(recovery: DamagedPreferencesRecovery, key: String) =
        (recovery.values[key] as? RecoveredPreference.StringValue)?.value

    @Test
    fun intactEntriesAroundADamagedRegionAreAllRecovered() {
        val bytes = stringEntry("boards_json", "[\"板\"]") +
            byteArrayOf(0x7F, 0xFF.toByte(), 0x03, 0x99.toByte()) +
            stringEntry("ng_words_json", "[\"NG\"]") +
            boolEntry("privacy_filter_enabled", true) +
            stringEntry(APP_LOCK_PASSWORD_HASH_PREFERENCE_KEY, lockHash)
        val recovery = recoverDamagedPreferences(bytes)
        assertEquals("[\"板\"]", string(recovery, "boards_json"))
        assertEquals("[\"NG\"]", string(recovery, "ng_words_json"))
        assertEquals(RecoveredPreference.BooleanValue(true), recovery.values["privacy_filter_enabled"])
        assertEquals(lockHash, string(recovery, APP_LOCK_PASSWORD_HASH_PREFERENCE_KEY))
        assertFalse(recovery.appLockPasswordLost)
    }

    @Test
    fun truncatedTailKeepsEveryCompleteEntry() {
        val complete = stringEntry("boards_json", "[]") + stringEntry(APP_LOCK_PASSWORD_HASH_PREFERENCE_KEY, lockHash)
        val tail = stringEntry("history_json", "[1,2,3]")
        val recovery = recoverDamagedPreferences(complete + tail.copyOf(tail.size - 3))
        assertEquals("[]", string(recovery, "boards_json"))
        assertEquals(lockHash, string(recovery, APP_LOCK_PASSWORD_HASH_PREFERENCE_KEY))
        assertFalse("history_json" in recovery.values)
    }

    @Test
    fun aLockHashWhoseEntryFramingIsBrokenIsSalvagedFromItsText() {
        val lockEntry = stringEntry(APP_LOCK_PASSWORD_HASH_PREFERENCE_KEY, lockHash)
        // Claim far more bytes than the file holds, as a damaged length would.
        lockEntry[1] = 0x7F
        val bytes = stringEntry("boards_json", "[]") + lockEntry + stringEntry("ng_words_json", "[]")
        val recovery = recoverDamagedPreferences(bytes)
        assertEquals(lockHash, string(recovery, APP_LOCK_PASSWORD_HASH_PREFERENCE_KEY))
        assertFalse(recovery.appLockPasswordLost)
        assertEquals("[]", string(recovery, "boards_json"))
    }

    @Test
    fun anUnrecoverableLockPasswordIsReported() {
        val damagedHash = lockHash.replace("sha256-v1:", "sha256-v1;")
        val recovery = recoverDamagedPreferences(
            stringEntry("boards_json", "[]") + stringEntry(APP_LOCK_PASSWORD_HASH_PREFERENCE_KEY, damagedHash)
        )
        assertTrue(recovery.appLockPasswordLost)

        val keyOnly = recoverDamagedPreferences(
            stringEntry("boards_json", "[]") + byteArrayOf(0x0A, 0x7F) + APP_LOCK_PASSWORD_HASH_PREFERENCE_KEY.encodeToByteArray()
        )
        assertTrue(keyOnly.appLockPasswordLost)
    }

    @Test
    fun aTurnedOffLockIsNotReportedAsLost() {
        val recovery = recoverDamagedPreferences(
            stringEntry(APP_LOCK_PASSWORD_HASH_PREFERENCE_KEY, "") + byteArrayOf(0xFF.toByte(), 0xFF.toByte())
        )
        assertEquals("", string(recovery, APP_LOCK_PASSWORD_HASH_PREFERENCE_KEY))
        assertFalse(recovery.appLockPasswordLost)
        assertFalse(recoverDamagedPreferences(byteArrayOf(0xFF.toByte(), 0x01)).appLockPasswordLost)
    }

    @Test
    fun invalidUtf8EntriesAreSkippedWithoutDroppingTheRest() {
        val broken = entry("bad", field(0x2A, byteArrayOf(0xC3.toByte(), 0x28)))
        val recovery = recoverDamagedPreferences(broken + stringEntry("boards_json", "[]"))
        assertFalse("bad" in recovery.values)
        assertEquals("[]", string(recovery, "boards_json"))
    }

    @Test
    fun noticeMentionsALostLockAndTheBackupFile() {
        val message = buildSettingsRecoveryNoticeMessage(3, appLockPasswordLost = true, backupFileName = "x.corrupt-1")
        assertTrue("アプリロック" in message)
        assertTrue("x.corrupt-1" in message)
        assertFalse("アプリロック" in buildSettingsRecoveryNoticeMessage(3, appLockPasswordLost = false, backupFileName = null))
    }
}
