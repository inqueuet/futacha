package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.util.ImageData
import com.valoser.futacha.shared.util.JvmFileSystem
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 2026-10-20の監査L54：投稿画面の添付を一時保存するたびに、最大約8MBの中身のハッシュを呼び出し側（画面のスレッド）で
 * 計算していた。ハッシュは呼び出し側の外で1回だけ計算し、保存先の形式と保存される中身は変わらない。
 */
class L54AttachmentHashDispatcherTest {
    @Test
    fun theAttachmentIsHashedOnceOffTheCallerThreadAndSavedUnchanged() = runBlocking {
        val root = Files.createTempDirectory("futacha-l54").toFile()
        try {
            val fileSystem = JvmFileSystem(root)
            val payload = ByteArray(8 * 1024 * 1024) { index -> (index * 31 + 7).toByte() }
            Executors.newSingleThreadExecutor { task -> Thread(task, "L54-ui") }.asCoroutineDispatcher().use { ui ->
                withContext(ui) {
                    val caller = Thread.currentThread()
                    val hashes = AtomicInteger()
                    val locator = persistCompatPostAttachment(
                        fileSystem = fileSystem,
                        tabKey = "compat_tab_0123",
                        attachment = ImageData(payload, "写真.png"),
                        payloadHash = { bytes ->
                            hashes.incrementAndGet()
                            assertNotSame(caller, Thread.currentThread(), "The attachment must not be hashed on the caller UI thread")
                            assertSame(payload, bytes)
                            "%016x".format(bytes.contentHashCode().toLong())
                        }
                    ).getOrThrow()
                    assertSame(caller, Thread.currentThread(), "The caller must resume on its UI dispatcher")
                    assertEquals(1, hashes.get())
                    assertTrue(isCompatPostAttachmentLocator(locator), locator)
                    assertTrue(locator.startsWith("private/compat_post_attachments/"), locator)
                    assertTrue(locator.endsWith(".png"), locator)
                    val restored = loadCompatPostAttachment(fileSystem, locator).getOrThrow()
                    assertTrue(restored.bytes.contentEquals(payload))
                }
            }
        } finally {
            root.deleteRecursively()
        }
    }
}
