package com.valoser.futacha.shared.ai

import com.valoser.futacha.shared.desktop.DesktopPlatform
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.file.Files
import java.util.UUID
import kotlin.test.*

class DesktopAiConnectionStorageTest {
    @Test fun macKeychainRestoresReplacesAndRemovesKeysWithoutPlaintextFiles() = runBlocking {
        assumeTrue(DesktopPlatform.isMac)
        val directory = Files.createTempDirectory("futacha-keychain-test").toFile()
        val account = "test-" + UUID.randomUUID()
        val credentials = MacAiCredentials(account)
        try {
            assertNull(credentials.read())
            val first = AiConnectionStore(DesktopAiConnectionStorage(credentials, File(directory, "cache.json")))
            first.load()
            first.save(AiProvider.DEVICE, AiProvider.OPENAI, DEFAULT_OPENAI_SUMMARY_MODEL, "test-desktop-key", 0.9f, false, setOf("harassment"))
            val reopened = AiConnectionStore(DesktopAiConnectionStorage(MacAiCredentials(account), File(directory, "cache.json")))
            reopened.load()
            assertNull(reopened.state.value.storageError)
            assertEquals("test-desktop-key", reopened.apiKey(reopened.state.value.revision))
            assertEquals(0.9f, reopened.state.value.moderationThreshold)
            assertFalse(reopened.state.value.moderationAutoHide)
            assertEquals(setOf("harassment"), reopened.state.value.moderationCategories)
            reopened.save(AiProvider.DEVICE, AiProvider.OPENAI, DEFAULT_OPENAI_SUMMARY_MODEL, "test-replacement-key")
            assertFalse(credentials.read()!!.contains("test-desktop-key"))
            reopened.save(AiProvider.DEVICE, AiProvider.DEVICE, DEFAULT_OPENAI_SUMMARY_MODEL, "")
            assertFalse(reopened.state.value.hasApiKey)
            assertFalse(credentials.read()!!.contains("test-replacement-key"))
            assertTrue(directory.walkTopDown().none { it.isFile })
            credentials.delete()
            assertNull(credentials.read())
        } finally { credentials.delete(); directory.deleteRecursively() }
    }

    @Test fun windowsDpapiRoundTripRejectsTamperingAndNeverWritesPlaintext() {
        assumeTrue(DesktopPlatform.isWindows)
        val directory = Files.createTempDirectory("futacha-dpapi-test").toFile()
        val file = File(directory, "private/openai.dpapi")
        val secret = "test-only-windows-key-日本語"
        try {
            val storage = WindowsAiCredentials(file)
            assertNull(storage.read())
            storage.write(secret)
            assertFalse(file.readBytes().decodeToString().contains(secret))
            assertEquals(secret, WindowsAiCredentials(file).read())
            val modified = file.readBytes().also { it[it.lastIndex] = (it.last().toInt() xor 0x40).toByte() }
            file.writeBytes(modified)
            assertFails { storage.read() }
            storage.write("replacement")
            assertEquals("replacement", storage.read())
            assertEquals(listOf(file.canonicalPath), directory.walkTopDown().filter { it.isFile }.map { it.canonicalPath }.toList())
        } finally { directory.deleteRecursively() }
    }

    @Test fun failedCredentialWriteLeavesTheLastSavedConnectionIntact() = runBlocking {
        var saved: String? = null
        var failing = false
        val credentials = object : DesktopAiCredentials {
            override fun read() = saved
            override fun write(value: String) { check(!failing); saved = value }
        }
        val directory = Files.createTempDirectory("futacha-ai-cache").toFile()
        try {
            val store = AiConnectionStore(DesktopAiConnectionStorage(credentials, File(directory, "cache.json")))
            store.load()
            store.save(AiProvider.DEVICE, AiProvider.OPENAI, DEFAULT_OPENAI_SUMMARY_MODEL, "test-only-key")
            val revision = store.state.value.revision
            failing = true
            assertFailsWith<IllegalStateException> { store.save(AiProvider.DEVICE, AiProvider.DEVICE, DEFAULT_OPENAI_SUMMARY_MODEL, "") }
            assertEquals(revision, store.state.value.revision)
            assertEquals("test-only-key", store.apiKey(revision))
            assertTrue(directory.walkTopDown().none { it.isFile })
        } finally { directory.deleteRecursively() }
    }
}
