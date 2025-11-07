package com.valoser.futacha.shared.repo

import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.network.BoardApi
import com.valoser.futacha.shared.parser.HtmlParser

interface BoardRepository {
    suspend fun getCatalog(
        board: String,
        mode: CatalogMode = CatalogMode.default
    ): List<CatalogItem>
    suspend fun getThread(board: String, threadId: String): ThreadPage
}

class DefaultBoardRepository(
    private val api: BoardApi,
    private val parser: HtmlParser
) : BoardRepository {
    override suspend fun getCatalog(
        board: String,
        mode: CatalogMode
    ): List<CatalogItem> {
        val html = api.fetchCatalog(board, mode)
        return parser.parseCatalog(html)
    }

    override suspend fun getThread(board: String, threadId: String): ThreadPage {
        val html = api.fetchThread(board, threadId)
        return parser.parseThread(html)
    }
}
