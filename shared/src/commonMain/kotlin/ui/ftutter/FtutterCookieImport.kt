package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.repository.parseWebViewCookieHeader

internal suspend fun ftutterImportCookies(repository: CookieRepository, header: String): Int {
    val pairs = parseWebViewCookieHeader(header)
    repository.commitEvenOnFailure {
        pairs.forEach { (name, value) ->
            repository.setCookie("https://www.2chan.net/", name, value, domain = "2chan.net")
        }
    }
    return pairs.size
}
