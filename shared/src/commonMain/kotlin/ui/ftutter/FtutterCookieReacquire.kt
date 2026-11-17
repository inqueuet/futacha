package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.repository.CookieRepository

/** M77：原作の確認の文（そのまま）。 */
internal const val FTUTTER_COOKIE_REACQUIRE_TEXT = "書き込み出来ない場合などに\n\n※クッキーを再取得した場合、一定時間の書き込み制限がかかる場合があります"

/** 原作の再取得の後の通知。 */
internal const val FTUTTER_COOKIE_REACQUIRED = "再取得しました"

/** ふたば（2chan.net とその下）のCookieか。他のサイトのCookie（他モードの画像検索など）は消さない。 */
internal fun ftutterIsFutabaCookieDomain(domain: String): Boolean {
    val host = domain.trim().trimStart('.').lowercase()
    return host == "2chan.net" || host.endsWith(".2chan.net")
}

/**
 * ふたばのCookieを消す。次にふたばへ通信した時（板・スレの取得、書き込みの前のフォームの取得）にサーバーから取り直す。
 * 返り値は消した数。
 */
internal suspend fun ftutterReacquireCookies(repository: CookieRepository): Int {
    val targets = repository.listCookies().filter { ftutterIsFutabaCookieDomain(it.domain) }
    repository.commitEvenOnFailure {
        targets.forEach { repository.deleteCookie(it.domain, it.path, it.name) }
    }
    return targets.size
}
