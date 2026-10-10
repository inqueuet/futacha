package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.network.ArchiveSearchItem
import com.valoser.futacha.shared.network.extractArchiveSearchScope
import com.valoser.futacha.shared.network.searchInqueuetArchiveThreads
import com.valoser.futacha.shared.ui.board.CatalogPreviewImage
import com.valoser.futacha.shared.ui.board.archiveSearchItemToCatalogItem
import com.valoser.futacha.shared.ui.board.buildPastThreadSearchEmptyMessage
import com.valoser.futacha.shared.ui.board.buildPastThreadSearchErrorMessage
import com.valoser.futacha.shared.ui.board.buildPastThreadSearchNoticeMessages
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json

/** What the archive search shows: waiting, the threads found, nothing found, or why it failed. */
internal sealed interface FutaberArchiveSearchState {
    data object Loading : FutaberArchiveSearchState
    data object Empty : FutaberArchiveSearchState
    data class Error(val message: String) : FutaberArchiveSearchState
    data class Results(val items: List<ArchiveSearchItem>) : FutaberArchiveSearchState
}

/** The line under a found thread: how many posts it had and what the archive says of it. */
internal fun futaberArchiveItemLine(item: ArchiveSearchItem): String =
    listOfNotNull("${item.replyCount}レス", item.status?.trim()?.takeIf { it.isNotEmpty() }).joinToString("　")

/**
 * The key of a found thread, as ふたちゃ tells two results apart: the archive may return the same thread twice
 * (from two archive servers, or once per page), and a lazy list refuses two items with one key.
 */
internal fun futaberArchiveItemKey(item: ArchiveSearchItem): String =
    "${item.server.lowercase()}/${item.board.lowercase()}/${item.threadId}"

/** [items] with the first of every thread kept (the order is the archive's). */
internal fun futaberDistinctArchiveItems(items: List<ArchiveSearchItem>): List<ArchiveSearchItem> =
    items.distinctBy(::futaberArchiveItemKey)

/** One short line for the notice under the title (the limits ふたちゃ also tells about). */
internal fun futaberArchiveNotice(): String = buildPastThreadSearchNoticeMessages().take(2).joinToString("")

private val archiveSearchJson = Json { ignoreUnknownKeys = true }

/**
 * 過去ログ検索 of the catalog's search (the archive search ふたちゃ uses, run through the same shared function): a card over
 * the catalog with the threads the archive has for [query] on this board. A tap opens a thread, which is shown from the
 * archive when it has fallen off the board.
 */
@Composable
internal fun FutaberArchiveSearchSheet(
    query: String,
    board: BoardSummary,
    httpClient: HttpClient,
    onOpen: (CatalogItem) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalFutaberColors.current
    var state by remember(query, board.id) { mutableStateOf<FutaberArchiveSearchState>(FutaberArchiveSearchState.Loading) }
    var attempt by remember(query, board.id) { mutableIntStateOf(0) }
    var allSources by remember(query, board.id) { mutableStateOf(false) }
    LaunchedEffect(query, board.id, attempt, allSources) {
        state = FutaberArchiveSearchState.Loading
        state = try {
            val found = futaberDistinctArchiveItems(
                searchInqueuetArchiveThreads(httpClient, archiveSearchJson, query.trim(), extractArchiveSearchScope(board), includeAllSources = allSources)
            )
            if (found.isEmpty()) FutaberArchiveSearchState.Empty else FutaberArchiveSearchState.Results(found)
        } catch (cancelled: CancellationException) {
            // A timeout inside the search surfaces as a cancellation too; only leaving the screen is rethrown.
            if (!isActive) throw cancelled
            FutaberArchiveSearchState.Error("過去ログ検索がタイムアウトしました")
        } catch (error: Throwable) {
            FutaberArchiveSearchState.Error(buildPastThreadSearchErrorMessage(error))
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f))
                .clickable(onClickLabel = "過去ログ検索を閉じる", onClick = onDismiss)
                .testTag("futaber-archive-scrim")
        )
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = maxHeight * 0.7f)
                .padding(8.dp).navigationBarsPadding().testTag("futaber-archive-sheet"),
            shape = FutaberShapes.sheet, color = colors.background, shadowElevation = 8.dp
        ) {
            Column(Modifier.padding(vertical = 12.dp)) {
                Text(
                    "過去ログ検索「${query.trim()}」", color = colors.body, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 16.dp)
                )
                Text(
                    futaberArchiveNotice(), color = colors.meta, fontSize = 11.sp,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp)
                )
                TextButton(onClick = { allSources = true; attempt++ }, enabled = state !is FutaberArchiveSearchState.Loading) {
                    Text("他の保存先も検索", color = colors.action)
                }
                HorizontalDivider(color = colors.separator, thickness = 0.5.dp)
                when (val current = state) {
                    FutaberArchiveSearchState.Loading -> Row(
                        Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp).testTag("futaber-archive-loading"), color = colors.accent, strokeWidth = 2.dp)
                        Text("検索中…", color = colors.body, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
                    }
                    FutaberArchiveSearchState.Empty -> Text(
                        buildPastThreadSearchEmptyMessage(), color = colors.meta, fontSize = 14.sp,
                        modifier = Modifier.padding(24.dp).testTag("futaber-archive-message")
                    )
                    is FutaberArchiveSearchState.Error -> Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(current.message, color = colors.danger, fontSize = 14.sp, modifier = Modifier.testTag("futaber-archive-message"))
                        TextButton(onClick = { attempt += 1 }, modifier = Modifier.testTag("futaber-archive-retry")) {
                            Text("再試行", color = colors.link)
                        }
                    }
                    is FutaberArchiveSearchState.Results -> LazyColumn(Modifier.fillMaxWidth().testTag("futaber-archive-list")) {
                        items(current.items, key = ::futaberArchiveItemKey) { item ->
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 58.dp)
                                    .clickable(onClickLabel = "スレッドを開く") { onOpen(archiveSearchItemToCatalogItem(item)) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp).testTag("futaber-archive-row"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CatalogPreviewImage(
                                    thumbnailUrl = item.thumbUrl?.takeIf { it.isNotBlank() }, fullImageUrl = null, targetSizePx = 150,
                                    contentDescription = "", modifier = Modifier.size(46.dp).background(colors.catalogGap),
                                    fallbackTint = colors.meta
                                )
                                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                    Text(
                                        item.title?.takeIf { it.isNotBlank() } ?: "No.${item.threadId}", color = colors.body, fontSize = 14.sp,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis
                                    )
                                    Text(futaberArchiveItemLine(item), color = colors.meta, fontSize = 12.sp)
                                }
                            }
                            HorizontalDivider(color = colors.separator, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
}

/** The pill shown over the catalog while a search word is typed: it runs the same word against the archive. */
@Composable
internal fun FutaberArchiveSearchPill(query: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalFutaberColors.current
    Text(
        "「${query.trim()}」を過去ログから検索", color = colors.link, fontSize = 13.sp, fontWeight = FontWeight.Medium,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(12.dp)
            .background(colors.bar, FutaberShapes.pill).border(1.dp, colors.separator, FutaberShapes.pill)
            .clickable(onClickLabel = "過去ログ検索", onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp).testTag("futaber-archive-search")
    )
}
