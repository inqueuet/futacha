package com.valoser.futacha.shared.ui.aimoge

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import com.valoser.futacha.shared.compat.AimogeAvailability
import com.valoser.futacha.shared.compat.siteCanonicalBoardUrlOf
import com.valoser.futacha.shared.compat.siteThreadUrlOf
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.network.aimoge.AIMOGE_API_BASE
import com.valoser.futacha.shared.network.aimoge.AimogeApi
import com.valoser.futacha.shared.network.aimoge.createAimogeHttpClient
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.util.rememberUrlLauncher
import com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation
import io.ktor.http.encodeURLQueryComponent
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

// ---- The data (GET /events, no sign-in) ----

@Serializable
internal data class AimogeEventsDto(val events: List<AimogeEventDto> = emptyList())

@Serializable
internal data class AimogeEventDto(
    val id: String = "",
    val slug: String = "",
    val content: AimogeEventContentDto = AimogeEventContentDto(),
    val occurrences: List<AimogeEventOccurrenceDto> = emptyList(),
    val attachments: List<AimogeEventAttachmentDto> = emptyList()
)

@Serializable
internal data class AimogeEventContentDto(val title: String = "", val body: String = "", val durationMinutes: Int? = null)

@Serializable
internal data class AimogeEventOccurrenceDto(
    val startsAt: String = "",
    val state: String = "",
    val count: Int = 0,
    val threadId: String? = null,
    val reason: String? = null
)

@Serializable
internal data class AimogeEventAttachmentDto(val url: String = "", val thumbnailUrl: String = "", val mime: String = "")

/** One held time of an event: a row of the calendar. */
data class AimogeCalendarRow(
    val eventId: String,
    val slug: String,
    /** The board's canonical address (`https://nijiurachan.net/b/{slug}/`). */
    val boardUrl: String,
    val title: String,
    val body: String,
    val thumbnailUrl: String?,
    val startsAt: Instant,
    val startsAtRaw: String,
    val durationMinutes: Int?,
    val state: String,
    val reason: String?,
    val interestCount: Int,
    val threadId: String?,
    /** The thread the held time made, when it has one. */
    val threadUrl: String?
) {
    /** The calendar file of this held time (opened by the browser or the OS calendar; no headers needed). */
    val icsUrl: String get() = "$AIMOGE_API_BASE/events/$eventId/ics?at=${startsAtRaw.encodeURLQueryComponent()}"

    /** The event's page on the Web. */
    val webUrl: String get() = "https://nijiurachan.net/b/$slug/events/$eventId?at=${startsAtRaw.encodeURLQueryComponent()}"
}

private val aimogeEventsJson = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true; explicitNulls = false }

/** The rows of a GET /events answer, one per held time, in time order. Unreadable times are skipped. */
fun parseAimogeCalendar(json: String): List<AimogeCalendarRow> {
    val dto = aimogeEventsJson.decodeFromString(AimogeEventsDto.serializer(), json)
    return dto.events.filter { it.id.isNotBlank() }.flatMap { event ->
        val boardUrl = aimogeCalendarBoardUrl(event.slug) ?: return@flatMap emptyList()
        val thumbnail = event.attachments.firstNotNullOfOrNull { a -> a.thumbnailUrl.takeIf { it.startsWith("https://") } }
        event.occurrences.mapNotNull { occurrence ->
            val at = runCatching { Instant.parse(occurrence.startsAt) }.getOrNull() ?: return@mapNotNull null
            val threadId = occurrence.threadId?.takeIf { it.isNotBlank() }
            AimogeCalendarRow(
                eventId = event.id,
                slug = event.slug,
                boardUrl = boardUrl,
                title = event.content.title.trim().ifEmpty { "(無題)" },
                body = event.content.body.trim(),
                thumbnailUrl = thumbnail,
                startsAt = at,
                startsAtRaw = occurrence.startsAt,
                durationMinutes = event.content.durationMinutes,
                state = occurrence.state,
                reason = occurrence.reason,
                interestCount = occurrence.count,
                threadId = threadId,
                threadUrl = threadId?.let { siteThreadUrlOf(boardUrl, it) }
            )
        }
    }.distinctBy { it.eventId to it.startsAtRaw }
        .sortedWith(compareBy<AimogeCalendarRow> { it.startsAt }.thenBy { it.slug }.thenBy { it.title })
}

/** `https://nijiurachan.net/b/{slug}/` for a board slug, or null for a slug that is not a plain board name. */
internal fun aimogeCalendarBoardUrl(slug: String): String? {
    if (slug.isBlank() || !slug.all { it.isLetterOrDigit() || it == '-' || it == '_' }) return null
    return siteCanonicalBoardUrlOf("https://nijiurachan.net/b/$slug/")
}

/** The registered board a calendar row belongs to, if any. */
fun aimogeCalendarBoardIn(boards: List<BoardSummary>, row: AimogeCalendarRow): BoardSummary? =
    boards.firstOrNull { siteCanonicalBoardUrlOf(it.url) == row.boardUrl }

/** What the person reads for a held time's state (the API's SCHEDULED / PENDING / CREATED / FAILED and the reason). */
fun aimogeCalendarStateLabel(row: AimogeCalendarRow): String = when (row.state) {
    "SCHEDULED" -> "予定"
    "PENDING" -> "まもなく"
    "CREATED" -> "開催スレあり"
    "FAILED" -> when (row.reason) {
        "INSUFFICIENT_INTEREST" -> "中止（興味ありが足りません）"
        "CANCELLED" -> "中止（主催者が取り消し）"
        "CANCELLED_BY_MODERATOR" -> "中止（管理者が取り消し）"
        "ORGANIZER_BANNED" -> "中止"
        "BOARD_CLOSED" -> "中止（板が閉鎖中）"
        "MODERATION_REJECTED" -> "中止（内容の確認で止まりました）"
        "ATTACHMENT_UNAVAILABLE" -> "中止（画像を使えません）"
        "RETRY_EXHAUSTED" -> "中止（スレを立てられませんでした）"
        else -> "中止"
    }
    else -> row.state
}

/** The window the calendar reads: from 3 days ago (recent held times and their threads) to 60 days ahead. */
internal fun aimogeCalendarWindow(now: Instant): Pair<String, String> =
    (now - 3.days).toString() to (now + 60.days).toString()

private fun Instant.localLabel(): Pair<String, String> {
    val local = toLocalDateTime(TimeZone.currentSystemDefault())
    val weekday = "月火水木金土日"[local.dayOfWeek.ordinal]
    val date = "${local.year}/${(local.month.ordinal + 1).toString().padStart(2, '0')}/${local.day.toString().padStart(2, '0')}（$weekday）"
    val time = "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
    return date to time
}

// ---- The screen (shared by the four modes) ----

/** Whether the calendar's entry is shown: only while あいもげ is on in this build. */
fun aimogeEventCalendarAvailable(): Boolean = AimogeAvailability.sitesEnabled

/**
 * あいもげのイベントカレンダー：全板の予定を日付ごとに並べる（読むだけ）。開催スレのある回は [onOpenThread] で
 * そのモードのスレの開き方に渡す。「カレンダーに追加」は .ics を開き、端末のカレンダーへ追加する。
 * 興味あり・登録・取り消しは書き込みなので、ここでは扱わない（Web で）。
 */
@Composable
fun AimogeEventCalendarDialog(
    onOpenThread: (AimogeCalendarRow) -> Unit,
    onDismiss: () -> Unit,
    loader: (suspend () -> String)? = null
) {
    // The client is made and closed inside each load, off the main thread: closing a Ktor client on the main thread
    // can close its sockets there (NetworkOnMainThreadException on Android, the 13.3/230 crash).
    val load: suspend () -> String = loader ?: {
        withContext(Dispatchers.Default) {
            val client = createAimogeHttpClient(appVersion = "")
            try {
                val (from, to) = aimogeCalendarWindow(Clock.System.now())
                AimogeApi(client).events(from, to)
            } finally {
                withContext(NonCancellable) { client.close() }
            }
        }
    }
    var reload by remember { mutableIntStateOf(0) }
    var rows by remember { mutableStateOf<List<AimogeCalendarRow>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(reload) {
        error = null
        runSuspendCatchingPreservingCancellation {
            val json = load()
            withContext(Dispatchers.Default) { parseAimogeCalendar(json) }
        }
            .onSuccess { rows = it }
            .onFailure { error = it.message?.takeIf(String::isNotBlank) ?: "イベントを読み込めませんでした" }
    }
    val openUrl = rememberUrlLauncher()
    var selected by remember { mutableStateOf<AimogeCalendarRow?>(null) }
    FutachaAppLockAwareWindow {
        AlertDialog(
            modifier = Modifier.testTag("aimoge-event-calendar"),
            onDismissRequest = onDismiss,
            title = { Text("あいもげのイベントカレンダー") },
            text = {
                val current = rows
                when {
                    current == null && error == null -> Row(
                        Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center
                    ) { CircularProgressIndicator() }
                    current == null -> Column {
                        Text(error.orEmpty())
                        TextButton(onClick = { reload++ }) { Text("再読み込み") }
                    }
                    current.isEmpty() -> Text("この先60日の予定はありません")
                    else -> AimogeCalendarList(current) { selected = it }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
            dismissButton = { TextButton(onClick = { rows = null; reload++ }) { Text("更新") } }
        )
    }
    selected?.let { row ->
        FutachaAppLockAwareWindow {
            AlertDialog(
                modifier = Modifier.testTag("aimoge-event-detail"),
                onDismissRequest = { selected = null },
                title = { Text(row.title) },
                text = {
                    Column(Modifier.heightIn(max = 420.dp)) {
                        val (date, time) = row.startsAt.localLabel()
                        Text("$date $time ・ /${row.slug}/")
                        Text(aimogeCalendarStateLabel(row) + " ・ 興味あり ${row.interestCount}")
                        row.durationMinutes?.let { Text("スレの保持 ${it / 60}時間${(it % 60).takeIf { m -> m > 0 }?.let { m -> "${m}分" } ?: ""}") }
                        if (row.body.isNotEmpty()) {
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            LazyColumn { item { Text(row.body) } }
                        }
                    }
                },
                confirmButton = {
                    Column(horizontalAlignment = Alignment.End) {
                        if (row.threadUrl != null) {
                            TextButton(modifier = Modifier.testTag("aimoge-event-open-thread"), onClick = {
                                selected = null
                                onOpenThread(row)
                            }) { Text("開催スレを開く") }
                        }
                        TextButton(onClick = { openUrl(row.icsUrl) }) { Text("端末のカレンダーに追加") }
                        TextButton(onClick = { openUrl(row.webUrl) }) { Text("Webで見る（興味ありはこちら）") }
                        TextButton(onClick = { selected = null }) { Text("閉じる") }
                    }
                }
            )
        }
    }
}

@Composable
private fun AimogeCalendarList(rows: List<AimogeCalendarRow>, onSelect: (AimogeCalendarRow) -> Unit) {
    val imageLoader = LocalFutachaImageLoader.current
    val context = LocalPlatformContext.current
    val grouped = remember(rows) { rows.groupBy { it.startsAt.localLabel().first } }
    LazyColumn(Modifier.heightIn(max = 480.dp).testTag("aimoge-event-list")) {
        grouped.forEach { (date, dayRows) ->
            item(key = "d-$date") {
                Text(date, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            }
            items(dayRows, key = { "${it.eventId}-${it.startsAtRaw}" }) { row ->
                Row(
                    Modifier.fillMaxWidth().clickable { onSelect(row) }.padding(vertical = 6.dp)
                        .testTag("aimoge-event-row-${row.eventId}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    row.thumbnailUrl?.let { url ->
                        Image(
                            painter = rememberAsyncImagePainter(
                                model = ImageRequest.Builder(context).data(url).build(), imageLoader = imageLoader
                            ),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                    Column(Modifier.padding(start = 8.dp).weight(1f)) {
                        Text("${row.startsAt.localLabel().second}  /${row.slug}/", style = MaterialTheme.typography.labelMedium)
                        Text(row.title, maxLines = 2)
                        Text(
                            aimogeCalendarStateLabel(row) + " ・ 興味あり ${row.interestCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (row.state == "FAILED") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
