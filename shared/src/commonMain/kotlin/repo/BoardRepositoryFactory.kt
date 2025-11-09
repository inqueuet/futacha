package com.valoser.futacha.shared.repo

import com.valoser.futacha.shared.network.HttpBoardApi
import com.valoser.futacha.shared.network.createHttpClient
import com.valoser.futacha.shared.parser.createHtmlParser

fun createRemoteBoardRepository(): BoardRepository {
    val api = HttpBoardApi(createHttpClient())
    val parser = createHtmlParser()
    return DefaultBoardRepository(api, parser)
}
