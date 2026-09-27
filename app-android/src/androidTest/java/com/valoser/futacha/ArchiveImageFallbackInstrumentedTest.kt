@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.valoser.futacha

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.BitmapImage
import coil3.memory.MemoryCache
import coil3.request.*
import com.valoser.futacha.shared.ui.compat.fetchCompatArchiveThreadPage
import com.valoser.futacha.shared.ui.image.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
class ArchiveImageFallbackInstrumentedTest {
    @Test fun missingArchiveImagesRecoverForThumbnailAndViewerWithDecodedPixels(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val green = 0xff33cc55.toInt()
        val bitmap = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888).apply { eraseColor(green) }
        val bytes = ByteArrayOutputStream().apply { bitmap.compress(Bitmap.CompressFormat.PNG, 100, this) }.toByteArray()
        bitmap.recycle()
        val source = "https://kako.futakuro.com/futa/may_b/456/"
        val image = source + "123.jpg"
        val recovered = "https://futabaforest.net/b/src/123.jpg"
        val requests = CopyOnWriteArrayList<String>()
        val client = HttpClient(MockEngine { request ->
            requests += request.url.toString()
            when (request.url.toString()) {
                source -> respond("""<div class="thre"><span class="cno">No.456</span>
                    <a href="$image">123.jpg</a><a href="http://kako.futakuro.com/futa/404.png">
                    <img src="http://kako.futakuro.com/futa/404s.png"></a><blockquote>OP</blockquote></div>""")
                image -> respond("missing", HttpStatusCode.NotFound)
                recovered -> respond(bytes, headers = headersOf("Content-Type", "image/png"))
                else -> if (request.url.host == "may.2chan.net") respond("missing", HttpStatusCode.NotFound)
                    else error("Unexpected request: ${request.url}")
            }
        })
        val loader = buildFutachaImageLoader(context, Dispatchers.IO, Dispatchers.Default,
            MemoryCache.Builder().maxSizeBytes(1024 * 1024L).build(), null,
            AdaptiveImageRequestGate(ImageMemoryPressurePolicy(ImageMemoryPressureLevel.NORMAL, 2, 1024 * 1024L, 1)), client)
        try {
            val post = fetchCompatArchiveThreadPage(client, source).posts.single()
            for (url in listOf(post.thumbnailUrl, post.imageUrl, "https://may.2chan.net/b/src/123.jpg")) {
                val result = loader.execute(ImageRequest.Builder(context).data(url).allowHardware(false).build())
                assertTrue((result as? ErrorResult)?.throwable.toString(), result is SuccessResult)
                val decoded = ((result as SuccessResult).image as BitmapImage).bitmap
                assertEquals(32, decoded.width)
                assertEquals(24, decoded.height)
                assertEquals(green, decoded.getPixel(16, 12))
            }
            assertTrue(recovered in requests)
            assertFalse(requests.any { "/404" in it })
        } finally { loader.shutdown(); client.close() }
    }
}
