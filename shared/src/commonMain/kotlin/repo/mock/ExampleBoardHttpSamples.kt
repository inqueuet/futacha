package com.valoser.futacha.shared.repo.mock

/**
 * HTTP interaction samples that mirror the captures under `/example`.
 *
 * These snippets document how the Futaba endpoints behave and can be used by tests or
 * documentation generators without hitting the real network.
 */
object ExampleBoardHttpSamples {

    data class HttpSample(
        val label: String,
        val requestUrl: String,
        val method: String,
        val contentType: String,
        val statusCode: Int,
        val requestBodySnippet: String,
        val responseBodySnippet: String
    )

    val postReply = HttpSample(
        label = "Thread reply (mode=regist)",
        requestUrl = "https://may.2chan.net/b/futaba.php?guid=on",
        method = "POST",
        contentType = "multipart/form-data; boundary=----WebKitFormBoundarymBLTraM363UngAbs",
        statusCode = 200,
        requestBodySnippet = """
            mode=regist
            resto=1364373631
            com=荒らさないようになに
            pwd=12345678
            responsemode=ajax
        """.trimIndent(),
        responseBodySnippet = "Shift_JIS HTML (88 bytes)"
    )

    val voteSo = HttpSample(
        label = "そうだね投票",
        requestUrl = "https://may.2chan.net/sd.php?b.1364374856",
        method = "GET",
        contentType = "text/plain; charset=utf-8",
        statusCode = 200,
        requestBodySnippet = "n/a",
        responseBodySnippet = "1"
    )

    val requestDeletion = HttpSample(
        label = "del依頼",
        requestUrl = "https://may.2chan.net/del.php",
        method = "POST",
        contentType = "application/x-www-form-urlencoded",
        statusCode = 200,
        requestBodySnippet = "mode=post&b=b&d=1364377046&reason=110&responsemode=ajax",
        responseBodySnippet = "Shift_JIS HTML (2 bytes)"
    )

    val deleteByUser = HttpSample(
        label = "本人削除 (mode=usrdel)",
        requestUrl = "https://may.2chan.net/b/futaba.php?guid=on",
        method = "POST",
        contentType = "application/x-www-form-urlencoded",
        statusCode = 200,
        requestBodySnippet = "1364378280=delete&responsemode=ajax&pwd=12345678&onlyimgdel=&mode=usrdel",
        responseBodySnippet = "Shift_JIS HTML (2 bytes)"
    )
}
