package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.repository.InMemoryFileSystem
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.util.FileSystem
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.*
import kotlin.test.*

class PhotoSaveDestinationTest {
    private fun fileSystem(): FileSystem = object : FileSystem by InMemoryFileSystem() {
        // This fake stores relative writes at its root, without a platform Documents prefix.
        override suspend fun resolveSavedFile(base: SaveLocation, relativePath: String): Result<String> =
            Result.success((base as SaveLocation.Path).path.trimEnd('/') + "/" + relativePath)
    }

    @Test fun permissionRefusalDoesNotReadMediaOrCreateAnExport() = runBlocking {
        val fs=fileSystem()
        HttpClient(MockEngine { error("must not download") }).use { client ->
            assertFailsWith<IllegalStateException> {
                saveMediaToPhotos("https://may.2chan.net/b/src/test.png",client,fs,
                    requestPermission={error("denied")}, saveFile={_,_->error("must not save")})
            }
            assertFalse(fs.exists("photo_export"))
        }
    }
    @Test fun photosReceivesOriginalBytesAndCompletionAlwaysCleansOnlyTemporaryCopy() = runBlocking {
        for(fail in listOf(false,true)) {
            val fs=fileSystem()
            val original=byteArrayOf(1,2,3,4)
            fs.writeBytes("/source/original.png",original).getOrThrow()
            var exported:String?=null
            HttpClient(MockEngine { error("local media must not download") }).use { client ->
                val result=runCatching {
                    saveMediaToPhotos("/source/original.png",client,fs,requestPermission={},saveFile={path,video->
                        exported=path
                        assertFalse(video)
                        assertContentEquals(original,fs.readBytes(path).getOrThrow())
                        if(fail) error("unsupported format")
                    })
                }
                assertEquals(fail,result.isFailure, result.exceptionOrNull()?.stackTraceToString())
                assertNotNull(exported)
                assertFalse(fs.exists(exported!!))
                assertContentEquals(original,fs.readBytes("/source/original.png").getOrThrow())
            }
        }
    }
}
