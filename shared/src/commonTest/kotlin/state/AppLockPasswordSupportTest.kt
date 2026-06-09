package com.valoser.futacha.shared.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppLockPasswordSupportTest {
    @Test
    fun buildsStableSaltedHash() {
        assertEquals(
            "sha256-v1:salt:291e247d155354e48fec2b579637782446821935fc96a5a08a0b7885179c408b",
            buildAppLockPasswordHash("password", salt = "salt")
        )
    }

    @Test
    fun verifiesPasswordAgainstStoredHash() {
        val hash = buildAppLockPasswordHash("secret", salt = "fixed")

        assertTrue(verifyAppLockPassword("secret", hash))
        assertFalse(verifyAppLockPassword("wrong", hash))
    }

    @Test
    fun rejectsInvalidOrBlankStoredHash() {
        assertEquals(null, sanitizeAppLockPasswordHash(""))
        assertEquals(null, sanitizeAppLockPasswordHash("legacy:plain"))
        assertFalse(verifyAppLockPassword("secret", null))
    }

    @Test
    fun validatesMinimumPasswordLength() {
        assertFalse(isValidAppLockPassword("123"))
        assertTrue(isValidAppLockPassword("1234"))
    }
}
