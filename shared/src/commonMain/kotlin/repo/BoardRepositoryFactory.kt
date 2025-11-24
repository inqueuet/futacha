package com.valoser.futacha.shared.repo

import com.valoser.futacha.shared.network.HttpBoardApi
import com.valoser.futacha.shared.network.createHttpClient
import com.valoser.futacha.shared.parser.createHtmlParser

fun createRemoteBoardRepository(httpClient: io.ktor.client.HttpClient = createHttpClient()): BoardRepository {
    val api = HttpBoardApi(httpClient)
    val parser = createHtmlParser()
    return DefaultBoardRepository(api, parser)
}
