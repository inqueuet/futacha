package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.repository.parseWebViewCookieHeader
import kotlinx.coroutines.CancellationException

internal suspend fun ftutterImportCookies(repository: CookieRepository, header: String): Int {
    val pairs = parseWebViewCookieHeader(header)
    if (pairs.isEmpty()) return 0
    val imported = repository.commitOnSuccess {
        // The backup has no original host/path attributes. Keep every existing Futaba scope and
        // value rather than widening a host-only cookie or sending a second cookie with its name.
        val existing = repository.listCookies().filter {
            val domain = it.domain.lowercase().trimStart('.')
            domain == "2chan.net" || domain.endsWith(".2chan.net")
        }.mapTo(HashSet()) { it.name }
        val additions = pairs.filterKeys { it !in existing }
        additions.forEach { (name, value) ->
            repository.setCookie("https://www.2chan.net/", name, value, domain = "2chan.net", importedWithoutScope = true)
        }
        additions.size
    }
    // A prior attempt may have updated RAM but failed to persist. Those names now look like
    // conflicts, so an explicit retry must verify durability even when it adds no new cookies.
    repository.flushPendingChanges()
    return imported
}

internal data class FtutterBackupSupplementResult(
    val cookieSaved: Int = 0,
    val cookieSkipped: Int = 0,
    val cookieFailed: Boolean = false,
    val droppedMarks: Int = 0,
    val marksFailed: Boolean = false
) {
    val incomplete: Boolean get() = cookieFailed || marksFailed
    val notices: List<String> get() = buildList {
        if (cookieSaved > 0) add("Cookie ${cookieSaved}件を保存しました")
        if (cookieSkipped > 0) add("既存Cookieと同名の${cookieSkipped}件は取り込まず、今の値と適用範囲を保持しました")
        if (cookieFailed) add("Cookieの取り込み・保存を完了できませんでした。他の項目の取り込みは続けました")
        if (marksFailed) add("マークの取り込みを完了できませんでした")
    }
}

/** Independent optional stages run after settings/boards/history have already been imported. */
internal suspend fun ftutterApplyBackupSupplements(
    cookieHeader: String?,
    repository: CookieRepository?,
    importMarks: suspend () -> Int
): FtutterBackupSupplementResult {
    val cookieCount = parseWebViewCookieHeader(cookieHeader).size
    var saved = 0
    var skipped = 0
    var cookieFailed = false
    if (cookieCount > 0) {
        try {
            saved = ftutterImportCookies(checkNotNull(repository), cookieHeader.orEmpty())
            skipped = cookieCount - saved
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Do not log the header or arbitrary exception messages from a cookie source.
            cookieFailed = true
        }
    }
    var droppedMarks = 0
    var marksFailed = false
    try {
        droppedMarks = importMarks()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        marksFailed = true
    }
    return FtutterBackupSupplementResult(saved, skipped, cookieFailed, droppedMarks, marksFailed)
}
