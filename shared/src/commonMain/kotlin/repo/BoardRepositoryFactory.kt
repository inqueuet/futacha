package com.valoser.futacha.shared.repo

import com.valoser.futacha.shared.model.CatalogFetchSettings
import com.valoser.futacha.shared.network.HttpBoardApi
import com.valoser.futacha.shared.network.createHttpClient
import com.valoser.futacha.shared.parser.createHtmlParser
import com.valoser.futacha.shared.repository.CookieRepository

fun createRemoteBoardRepository(
    httpClient: io.ktor.client.HttpClient = createHttpClient(),
    cookieRepository: CookieRepository? = null,
    catalogFetchSettingsProvider: suspend () -> CatalogFetchSettings = { CatalogFetchSettings() }
): BoardRepository {
    val api = HttpBoardApi(httpClient)
    val parser = createHtmlParser()
    return DefaultBoardRepository(
        api = api,
        parser = parser,
        cookieRepository = cookieRepository,
        catalogFetchSettingsProvider = catalogFetchSettingsProvider
    )
}
