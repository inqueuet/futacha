package com.valoser.futacha

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.shared.util.readImageDataFromUri
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Review 2026-11-11 (あぷ小 on img): a 9MB phone photo read with the picker default limit comes back null,
 * which the pickers show as 「添付ファイルが読み込めません」.  The あぷ小 pickers now read up to 32MB.
 */
@RunWith(AndroidJUnit4::class)
class UpchibiLargePhotoReadInstrumentedTest {
    @Test
    fun aNineMegabytePhotoFailsWithTheDefaultLimitAndReadsWithTheUpsLimit() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "upchibi-review-9mb.jpg")
        file.writeBytes(ByteArray(9_000_000) { (it % 251).toByte() })
        try {
            val uri = Uri.fromFile(file)
            assertNull(readImageDataFromUri(context, uri))
            assertEquals(9_000_000, readImageDataFromUri(context, uri, 32_000_000L)?.bytes?.size)
        } finally {
            file.delete()
        }
    }
}
