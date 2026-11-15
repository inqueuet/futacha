package com.valoser.futacha.shared.repo

import com.valoser.futacha.shared.model.CatalogFetchSettings
import com.valoser.futacha.shared.network.HttpBoardApi
import com.valoser.futacha.shared.network.createHttpClient
import com.valoser.futacha.shared.parser.createHtmlParser
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.network.aimoge.createAimogeHttpClient
import com.valoser.futacha.shared.repo.aimoge.AimogeBoardRepository

fun createRemoteBoardRepository(
    httpClient: io.ktor.client.HttpClient = createHttpClient(),
    cookieRepository: CookieRepository? = null,
    catalogFetchSettingsProvider: suspend () -> CatalogFetchSettings = { CatalogFetchSettings() }
): BoardRepository {
    val api = HttpBoardApi(httpClient)
    val parser = createHtmlParser()
    return withAimogeRouting(
        DefaultBoardRepository(
            api = api,
            parser = parser,
            cookieRepository = cookieRepository,
            catalogFetchSettingsProvider = catalogFetchSettingsProvider
        )
    )
}

/**
 * [futaba] with あいもげ boards sent to an [AimogeBoardRepository] on its own cookie-less client (made on the first
 * あいもげ request, closed with the returned repository). While あいもげ is off nothing reaches it.
 */
fun withAimogeRouting(
    futaba: BoardRepository,
    makeAimogeClient: () -> io.ktor.client.HttpClient = { createAimogeHttpClient(appVersion = "") }
): BoardRepository = RoutingBoardRepository(
    futaba,
    AimogeBoardRepository.owningClient(
        makeAimogeClient,
        pausedNow = com.valoser.futacha.shared.service.SiteRefreshPause::sitePausedNow,
        onReadError = com.valoser.futacha.shared.service.SiteRefreshPause::recordError
    )
)
