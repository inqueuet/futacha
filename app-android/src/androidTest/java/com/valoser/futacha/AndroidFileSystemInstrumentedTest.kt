package com.valoser.futacha

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.service.AUTO_SAVE_DIRECTORY
import com.valoser.futacha.shared.service.MANUAL_SAVE_DIRECTORY
import com.valoser.futacha.shared.util.FileSystem
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
