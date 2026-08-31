package com.valoser.futacha

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Environment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.service.AUTO_SAVE_DIRECTORY
import com.valoser.futacha.shared.service.MANUAL_SAVE_DIRECTORY
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.AndroidFileSystem
import com.valoser.futacha.shared.util.createFileSystem
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidFileSystemInstrumentedTest {
    private lateinit var fileSystem: FileSystem
    private lateinit var privateBasePath: String

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fileSystem = createFileSystem(context)
        privateBasePath = "private/android_fs_test_${System.currentTimeMillis()}"
    }

    @After
    fun tearDown() {
        runBlocking {
            fileSystem.deleteRecursively(privateBasePath)
        }
    }

    @Test
    fun resolveAbsolutePath_routesAutoSaveToPrivateAppStorage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolved = fileSystem.resolveAbsolutePath("$AUTO_SAVE_DIRECTORY/thread/index.json")

        assertTrue(resolved.startsWith(File(context.filesDir, "futacha").absolutePath))
        assertTrue(resolved.contains("$AUTO_SAVE_DIRECTORY/thread/index.json"))
    }

    @Test
    fun resolveAbsolutePath_routesDocumentsAliasToSavedThreadsFolder() {
        val resolved = fileSystem.resolveAbsolutePath("Documents")

        assertTrue(resolved.endsWith("/futacha/$MANUAL_SAVE_DIRECTORY"))
    }

    @Test
    fun defaultExternalStorageIsAppScopedAndDeclaresNoLegacyPermission() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val requestedPermissions = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        ).requestedPermissions.orEmpty().toSet()
        val appScopedDocuments = checkNotNull(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        ).absolutePath

        assertFalse(Manifest.permission.READ_EXTERNAL_STORAGE in requestedPermissions)
        assertFalse(Manifest.permission.WRITE_EXTERNAL_STORAGE in requestedPermissions)
        assertTrue(fileSystem.resolveAbsolutePath("Documents").startsWith(appScopedDocuments))
        assertTrue(fileSystem.resolveAbsolutePath("Download").startsWith(
            checkNotNull(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)).absolutePath
        ))
    }

    @Test
    fun resolveAbsolutePath_onlyCallsExternalStorageForAnExternalAlias() {
        val baseContext = InstrumentationRegistry.getInstrumentation().targetContext
        val countingContext = CountingExternalFilesContext(baseContext)
        val countingFileSystem = AndroidFileSystem(countingContext)

        countingFileSystem.resolveAbsolutePath("/already/absolute.json")
        countingFileSystem.resolveAbsolutePath("private/history_store/manifest.json")
        countingFileSystem.resolveAbsolutePath("$AUTO_SAVE_DIRECTORY/thread/index.json")
        assertEquals(0, countingContext.externalFilesDirCallCount)

        countingFileSystem.resolveAbsolutePath("Documents")
        assertEquals(1, countingContext.externalFilesDirCallCount)
    }

    @Test
    fun saveLocationPath_roundTripsWriteReadAndDelete() {
        val base = SaveLocation.Path(privateBasePath)

        runBlocking {
            fileSystem.createDirectory(base).getOrThrow()
            fileSystem.writeString(base, "nested/thread.txt", "hello android").getOrThrow()

            assertTrue(fileSystem.exists(base, "nested/thread.txt"))
            assertEquals("hello android", fileSystem.readString(base, "nested/thread.txt").getOrThrow())

            fileSystem.delete(base, "nested/thread.txt").getOrThrow()
            assertFalse(fileSystem.exists(base, "nested/thread.txt"))
        }
    }
}

private class CountingExternalFilesContext(base: Context) : ContextWrapper(base) {
    var externalFilesDirCallCount: Int = 0
        private set

    override fun getExternalFilesDir(type: String?): File? {
        externalFilesDirCallCount += 1
        return super.getExternalFilesDir(type)
    }
}
