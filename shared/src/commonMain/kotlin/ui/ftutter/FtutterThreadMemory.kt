package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.model.ThreadPage

internal class FtutterThreadMemory {
    private val pages = LinkedHashMap<String, ThreadPage>()
    private val refresh = mutableSetOf<String>()
    fun open(url: String, refreshOnOpen: Boolean) { if (refreshOnOpen) refresh += url else refresh -= url }
    fun page(url: String): ThreadPage? = pages[url]
    fun takeRefresh(url: String): Boolean = refresh.remove(url)
    fun put(url: String, page: ThreadPage) {
        pages.remove(url); pages[url] = page
        while (pages.size > 12) pages.remove(pages.keys.first())
    }
    fun remove(url: String) { pages.remove(url); refresh.remove(url) }
}
