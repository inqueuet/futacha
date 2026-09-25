@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.valoser.futacha.shared.util

import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosPickerInboxCleanupTest {
    private val manager = NSFileManager.defaultManager

    private fun createFile(directory: String, name: String): String {
        manager.createDirectoryAtPath(directory, withIntermediateDirectories = true, attributes = null, error = null)
        val path = "$directory/$name"
        assertTrue(manager.createFileAtPath(path, contents = NSData(), attributes = null))
        return path
    }

    @Test
    fun deletesOnlyThePickerCopyInsideTheTemporaryInbox() {
        val temporary = NSTemporaryDirectory().trimEnd('/')
        val inbox = "$temporary/com.example.test-Inbox"
        val other = "$temporary/${NSUUID().UUIDString}"
        val copied = createFile(inbox, "${NSUUID().UUIDString}.mov")
        val original = createFile(other, "original.mov")
        try {
            assertTrue(isIosDocumentPickerInboxCopy(NSURL.fileURLWithPath(copied)))
            assertFalse(isIosDocumentPickerInboxCopy(NSURL.fileURLWithPath(original)))
            assertFalse(isIosDocumentPickerInboxCopy(NSURL.fileURLWithPath(inbox)))
            assertFalse(isIosDocumentPickerInboxCopy(NSURL.fileURLWithPath("$inbox/../escape.mov")))
            assertFalse(isIosDocumentPickerInboxCopy(NSURL.URLWithString("https://example.com/tmp/a-Inbox/x.mov")!!))

            deleteIosDocumentPickerInboxCopies(
                listOf(NSURL.fileURLWithPath(copied), NSURL.fileURLWithPath(original), "not a url")
            )

            assertFalse(manager.fileExistsAtPath(copied))
            assertTrue(manager.fileExistsAtPath(original))
        } finally {
            manager.removeItemAtPath(inbox, null)
            manager.removeItemAtPath(other, null)
        }
    }
}
