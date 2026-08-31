@file:OptIn(io.ktor.utils.io.InternalAPI::class)

package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.stableCompatHash
import com.valoser.futacha.shared.util.ImageData
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.http.content.PartData
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompatUpsUploadTest {
    @Test
    fun validationMessagesMatchBothReferenceApks() = runBlocking {
        val client = HttpClient(MockEngine) {
            engine { addHandler { error("network must not be reached") } }
        }
        try {
            assertEquals(
                "ファイルの準備に失敗しました",
                uploadCompatUps(
                    client,
                    ImageData(byteArrayOf(), "empty.png"),
                    "",
                    "248600",
                    "test",
                    publishDelayMillis = 0L
                ).exceptionOrNull()?.message
            )
            assertEquals(
                "削除キーがありません",
                uploadCompatUps(
                    client,
                    ImageData(byteArrayOf(1), "image.png"),
                    "",
                    "",
                    "test",
                    publishDelayMillis = 0L
                ).exceptionOrNull()?.message
            )
        } finally {
            client.close()
        }
    }

    @Test
    fun uploaderAndIndexFailuresKeepTheReferenceHeadlines() = runBlocking {
        suspend fun failureFor(
            uploadBody: String = "ok",
            indexStatus: HttpStatusCode = HttpStatusCode.OK,
            indexBody: String = "missing"
        ): String? {
            val client = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        when (request.url.encodedPath) {
                            "/up2/up.php" -> respond(uploadBody, HttpStatusCode.OK, headersOf())
                            "/up2/up.htm" -> respond(indexBody, indexStatus, headersOf())
                            else -> error("unexpected URL: ${request.url}")
                        }
                    }
                }
            }
            return try {
                uploadCompatUps(
                    client,
                    ImageData(byteArrayOf(1), "image.png"),
                    "",
                    "248600",
                    "test",
                    publishDelayMillis = 0L
                ).exceptionOrNull()?.message
            } finally {
                client.close()
            }
        }

        assertEquals(
            "アップローダーからエラーが返されました\n拒否されました",
            failureFor("<font color=red size=5><b>拒否されました<br><br>")
        )
        assertEquals(
            "トップページが取得できませんでした\nHTTP status 503 error",
            failureFor(indexStatus = HttpStatusCode.ServiceUnavailable)
        )
        assertEquals(
            "アップロードしたファイルが見つかりません",
            failureFor()
        )
    }

    @Test
    fun multipartUploadIsPublishedAndGeneratedFileNameIsReadFromIndex() = runBlocking {
        val fileName = "test.png"
        val now = 123456789L
        val token = stableCompatHash("$fileName:$now")
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    when (request.url.encodedPath) {
                        "/up2/up.php" -> {
                            val multipart = request.body as? MultiPartFormDataContent
                                ?: error("expected multipart request body: ${request.body::class}")
                            val formValues = multipart.parts
                                .filterIsInstance<PartData.FormItem>()
                                .associate { it.name to it.value }
                            val binary = multipart.parts
                                .filterIsInstance<PartData.BinaryItem>()
                                .single()
                            assertEquals("reg", formValues["mode"])
                            assertEquals("delete-key", formValues["pass"])
                            assertTrue(formValues["com"].orEmpty().contains(token))
                            assertEquals("up", binary.name)
                            assertTrue(
                                binary.headers.getAll(HttpHeaders.ContentDisposition).orEmpty()
                                    .any { it.contains("test.png") },
                                "binary headers: ${binary.headers}"
                            )
                            respond("ok", HttpStatusCode.OK, headersOf())
                        }
                        "/up2/up.htm" -> respond(
                            "<tr><td><a href=\"src/fu12345.png\">fu12345.png</a></td><td>$token</td></tr>" +
                                "<tr><td><a href=\"src/fu99999.png\">fu99999.png</a></td></tr>",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, "text/html")
                        )
                        else -> error("unexpected あぷ小 URL: ${request.url}")
                    }
                }
            }
        }

        try {
            val result = uploadCompatUps(
                client = client,
                attachment = ImageData("PNGDATA".encodeToByteArray(), fileName),
                comment = "comment",
                deleteKey = "delete-key",
                appVersion = "test",
                nowEpochMillis = now,
                publishDelayMillis = 0L
            )
            assertEquals("fu12345.png", result.getOrThrow())
        } finally {
            client.close()
        }
    }
}
