@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.valoser.futacha

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.shared.network.createHttpClient
import com.valoser.futacha.shared.ui.compat.COMPAT_UPS_PICKER_MAX_BYTES
import com.valoser.futacha.shared.ui.compat.prepareCompatUpsUpload
import com.valoser.futacha.shared.ui.compat.uploadCompatUps
import com.valoser.futacha.shared.util.readImageDataFromUri
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.http.parameters
import java.io.File
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Live check (only with -e upsLive true): a >9MB photo goes through the あぷ小 path the 4 modes share
 * (picker read at the あぷ小 limit → shrink to 3000KB → up2 upload → fuNNN name), then is deleted
 * with up.php mode=del.  Never posts to a Futaba thread.  The delete key is random and never logged.
 */
@RunWith(AndroidJUnit4::class)
class UpchibiLiveUploadInstrumentedTest {
    @Test
    fun aLargePhotoIsShrunkUploadedAndDeleted() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("upsLive") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "upchibi-live.jpg")
        val random = Random(20261111)
        val w = 4000; val h = 3000
        val pixels = IntArray(w * h) { 0xFF000000.toInt() or random.nextInt(0x1000000) }
        val bitmap = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
        bitmap.recycle()
        val client = createHttpClient(context, null)
        try {
            assertTrue("photo is ${file.length()}", file.length() > 9_000_000)
            val picked = requireNotNull(readImageDataFromUri(context, Uri.fromFile(file), COMPAT_UPS_PICKER_MAX_BYTES))
            val prepared = prepareCompatUpsUpload(picked).getOrThrow()
            assertTrue(prepared.shrunk && prepared.image.bytes.size <= 3_000_000)
            val key = (1..8).map { "abcdefghjkmnpqrstuvwxyz23456789"[random.nextInt(31)] }.joinToString("")
            val name = uploadCompatUps(client, prepared.image, "", key, "test").getOrThrow()
            assertTrue(name, Regex("fu\\d{5,10}\\.jpg").matches(name))
            val no = name.removePrefix("fu").substringBefore('.')
            android.util.Log.i("UpchibiLive", "uploaded $name size=${prepared.image.bytes.size} from=${file.length()}")
            val del = client.submitForm("https://dec.2chan.net/up2/up.php", parameters {
                append("delno", no); append("mode", "del"); append("pass", key)
            })
            android.util.Log.i("UpchibiLive", "delete status=${del.status.value}")
            val gone = client.get("https://dec.2chan.net/up2/src/$name").status.value
            android.util.Log.i("UpchibiLive", "after delete src status=$gone")
            assertTrue("still served: $gone", gone == 404 || gone == 410)
        } finally {
            client.close(); file.delete()
        }
    }
}
