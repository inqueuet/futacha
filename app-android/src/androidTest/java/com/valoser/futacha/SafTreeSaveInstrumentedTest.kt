package com.valoser.futacha

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.DocumentsContract
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.util.createFileSystem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/**
 * Audit items 13 and 20 on a real SAF tree: the system picker grants a folder
 * of the external storage provider, then the Android file system writes to it.
 */
@SdkSuppress(minSdkVersion = 30)
class SafTreeSaveInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val fileSystem = createFileSystem(context)
    private val folder = "futacha-saf-${System.nanoTime()}"
    private lateinit var tree: Uri
    private val location get() = SaveLocation.TreeUri(tree.toString())

    @Before
    fun grantTreeThroughThePicker() {
        shell("mkdir -p /sdcard/Download/$folder")
        val initial = DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents", "primary:Download/$folder")
        val result = CompletableDeferred<Uri?>()
        rule.runOnUiThread {
            rule.activity.activityResultRegistry
                .register("saf-tree-test", ActivityResultContracts.OpenDocumentTree()) { result.complete(it) }
                .launch(initial)
        }
        clickText("Use this folder")
        clickText("Allow")
        tree = requireNotNull(runBlocking { withTimeout(10_000) { result.await() } }) { "The picker returned no folder" }
        context.contentResolver.takePersistableUriPermission(
            tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }

    @After
    fun releaseTree() {
        if (::tree.isInitialized) runCatching {
            context.contentResolver.releasePersistableUriPermission(
                tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        shell("rm -rf /sdcard/Download/$folder")
    }

    @Test
    fun failedZipReplacementKeepsThePreviousZipAndLeavesNoPartialFile(): Unit = runBlocking {
        val first = fileSystem.writeByteStreamReplacing(location, "zip/media.zip") { it.write("A".encodeToByteArray()) }
        assertEquals("zip/media.zip", first.getOrThrow())

        val failed = fileSystem.writeByteStreamReplacing(location, "zip/media.zip") { sink ->
            sink.write("partial".encodeToByteArray())
            throw IOException("network lost")
        }
        assertTrue(failed.isFailure)
        assertEquals("A", fileSystem.readString(location, "zip/media.zip").getOrThrow())
        assertEquals(listOf("media.zip"), fileSystem.listFiles(location, "zip"))

        val replaced = fileSystem.writeByteStreamReplacing(location, "zip/media.zip") { it.write("B".encodeToByteArray()) }
        assertEquals("zip/media.zip", replaced.getOrThrow())
        assertEquals("B", fileSystem.readString(location, "zip/media.zip").getOrThrow())
        assertEquals(listOf("media.zip"), fileSystem.listFiles(location, "zip"))
    }

    @Test
    fun saveBatchWritesOverwritesAndRecoversFromOutsideChanges(): Unit = runBlocking {
        withContext(fileSystem.saveBatchContext()) {
            fileSystem.createDirectory(location, "thread/img").getOrThrow()
            repeat(20) { fileSystem.writeBytes(location, "thread/img/$it.jpg", byteArrayOf(it.toByte())).getOrThrow() }
            // Overwrites inside the same batch reuse the indexed documents instead of making "0 (1).jpg".
            repeat(20) { fileSystem.writeBytes(location, "thread/img/$it.jpg", byteArrayOf((it + 100).toByte())).getOrThrow() }
            fileSystem.delete(location, "thread/img/0.jpg").getOrThrow()
            fileSystem.writeBytes(location, "thread/img/0.jpg", byteArrayOf(7)).getOrThrow()
            // A file removed behind the batch's back leaves a stale index entry; the write must still land.
            shell("rm /sdcard/Download/$folder/thread/img/1.jpg")
            fileSystem.writeBytes(location, "thread/img/1.jpg", byteArrayOf(8)).getOrThrow()
        }

        assertEquals((0 until 20).map { "$it.jpg" }.sorted(), fileSystem.listFiles(location, "thread/img").sorted())
        assertArrayEquals(byteArrayOf(7), fileSystem.readBytes(location, "thread/img/0.jpg").getOrThrow())
        assertArrayEquals(byteArrayOf(8), fileSystem.readBytes(location, "thread/img/1.jpg").getOrThrow())
        for (index in 2 until 20) {
            assertArrayEquals(byteArrayOf((index + 100).toByte()),
                fileSystem.readBytes(location, "thread/img/$index.jpg").getOrThrow())
        }
    }

    private fun shell(command: String) {
        val output = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(output).use { it.readBytes() }
    }

    /** DocumentsUI is another app: find its button through accessibility and click it. */
    private fun clickText(text: String) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            val node = automation.rootInActiveWindow
                ?.findAccessibilityNodeInfosByText(text)
                ?.firstOrNull { it.text?.toString().equals(text, ignoreCase = true) }
            var target: AccessibilityNodeInfo? = node
            while (target != null && !target.isClickable) target = target.parent
            if (target != null && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
            SystemClock.sleep(200)
        }
        throw AssertionError("\"$text\" did not appear in the folder picker")
    }
}
