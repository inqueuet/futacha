package com.valoser.futacha.shared.network

import io.ktor.client.HttpClient
import com.valoser.futacha.shared.util.TextEncoding
import io.ktor.client.request.prepareGet
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.isSuccess
import io.ktor.util.AttributeKey
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Only the official pre-submit function runs here. This browser never submits a post. */
internal interface PostingBrowser {
    suspend fun userAgent(): String
    suspend fun evaluate(pageUrl: String, html: String, script: String): String
}
internal val PostingBrowserKey = AttributeKey<PostingBrowser>("FutabaPostingBrowser")

internal suspend fun prepareOfficialPostingEnvironment(
    client: HttpClient,
    browser: PostingBrowser,
    pageUrl: String,
    config: HttpBoardApiPostingConfig,
    userAgent: String
): HttpBoardApiPostingConfig = withTimeoutOrNull(30_000) {
    val url = Url(pageUrl)
    require(url.protocol.name == "https" && url.host.endsWith(".2chan.net"))
    suspend fun script(path: String): String {
        return client.prepareGet("https://${url.host}$path") {
            attributes.put(HigherLayerRetryManaged, true)
            header(HttpHeaders.UserAgent, userAgent)
            header(HttpHeaders.Referrer, pageUrl)
            header(HttpHeaders.CacheControl, "no-cache")
        }.execute { response ->
            if (!response.status.isSuccess()) throw NetworkException("投稿の準備情報を取得できないため送信していません")
            val bytes = readHttpBoardApiResponseBytesWithLimit(
                response, maxBytes = 512 * 1024, responseReadBufferBytes = 8192,
                maxZeroReadRetries = 8, zeroReadBackoffMillis = 10, responseTotalTimeoutMillis = 15_000
            )
            TextEncoding.decodeToString(bytes, response.headers[HttpHeaders.ContentType])
        }
    }
    val official = script("/bin/base4esc.js?y")
    val clock = script("/bin/cachemt7.php")
    // No thread body, attachment, password, ads or arbitrary inline HTML runs in this view.
    val html = "<html><head><meta charset='utf-8'>" +
        "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; script-src 'unsafe-inline' 'unsafe-eval'; connect-src 'none'; form-action 'none'\">" +
        "</head><body><form id='fm' method='post'>" +
        listOf("js", "scsz", "pthc", "pthb", "pthd", "ptua").joinToString("") {
            "<input id='$it' name='$it' value=''>"
        } + "</form></body></html>"
    val result = browser.evaluate(pageUrl, html, """
        (function() {
          $clock
          $official
          if (typeof ptfk !== 'function' || typeof caco !== 'function') throw Error('Posting preparation unavailable');
          ptfk(0);
          var out = {};
          ['js','scsz','pthc','pthb','pthd','ptua'].forEach(function(k) { out[k] = document.getElementById(k).value; });
          return JSON.stringify(out);
        })()
    """.trimIndent())
    val values = try {
        Json.parseToJsonElement(result).jsonObject.mapValues { it.value.jsonPrimitive.content }
    } catch (e: Exception) {
        // A script error makes the WebView answer "null"; do not surface a JSON parser message.
        throw NetworkException("投稿の準備に失敗したため送信していません（公式の送信前処理から結果を取得できませんでした）", cause = e)
    }
    if (values["js"] != "on" || values["ptua"]?.toLongOrNull() !in 0L..8_589_934_591L ||
        !Regex("[0-9]+x[0-9]+x[0-9]+").matches(values["scsz"].orEmpty()) || values["pthc"].isNullOrBlank()) {
        throw NetworkException("投稿の準備に失敗したため送信していません")
    }
    config.copy(environment = values)
} ?: throw NetworkException("投稿準備が時間内に完了しなかったため送信していません")

/**
 * Environment values for hosts that have no WebView to run the official pre-submit script (Desktop/JVM).
 * These are the values every platform sent before the 12.4 official pre-processing: `js=on`, a client
 * timestamp for `pthc`, empty `pthb`/`pthd`, a fixed screen spec and the `ptua` the live form handed out.
 * Android/iOS never use this: they set [PostingBrowserKey] and run the official script instead.
 */
@OptIn(ExperimentalTime::class)
internal fun applyLegacyPostingEnvironment(
    config: HttpBoardApiPostingConfig,
    currentEpochMillis: Long = Clock.System.now().toEpochMilliseconds()
): HttpBoardApiPostingConfig = config.copy(
    environment = mapOf(
        "js" to "on",
        "scsz" to "1080x1920x24",
        "pthc" to currentEpochMillis.toString(),
        "pthb" to "",
        "pthd" to "",
        "ptua" to config.ptuaValue.orEmpty()
    )
)
