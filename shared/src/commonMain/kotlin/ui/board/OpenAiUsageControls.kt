package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
import com.valoser.futacha.shared.ai.*
import com.valoser.futacha.shared.util.rememberUrlLauncher
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow

internal fun openAiWaitText(until: Long, now: Long): String {
    val seconds = ((until - now).coerceAtLeast(0) + 999) / 1_000
    return "${seconds / 3600}時間${seconds / 60 % 60}分${seconds % 60}秒"
}

private fun usageTime(time: Long): String = runCatching {
    Instant.fromEpochMilliseconds(time).toLocalDateTime(TimeZone.currentSystemDefault())
        .toString().replace('T', ' ').substringBefore('.')
}.getOrDefault("時刻を表示できません")

internal fun openAiUsageLines(s: OpenAiUsageState, now: Long, includeWaitStatus: Boolean = true): List<String> = buildList {
    if (includeWaitStatus) {
        if (s.permanentQuotaError) {
            add("OpenAIの利用枠・契約の確認が必要です。時間経過だけで解除されるとは限りません。")
        } else if (s.waitUntil > now) {
            val reason = when (s.waitReason) {
                "server_retry" -> "OpenAIの利用制限（429）"
                "server_requests" -> "OpenAIが通知したリクエスト残量による待機"
                "server_tokens" -> "OpenAIが通知したトークン残量による待機"
                "local_tokens" -> "本文の送信量を抑えるための待機"
                "local_requests" -> "この端末の送信回数上限による待機"
                else -> "利用制限後の再試行待ち（APIの待機時間は未通知）"
            }
            add("$reason：残り${openAiWaitText(s.waitUntil, now)}")
            add("再試行できる目安：${usageTime(s.waitUntil)}。解除・判定成功を保証する時刻ではありません。")
        } else if (s.lastStatus == 429) {
            add("前回は利用制限（429）で判定できませんでした。待機は終了しています。スレを開き直すと再試行します。")
        }
    }
    val hours = s.recentHours(now)
    add("この端末・直近約24時間の送信試行：${hours.sumOf { it.requests }}回 / アプリ上限${OPENAI_LOCAL_REQUESTS_PER_DAY}回")
    add("送信本文の推定トークン量：${hours.sumOf { it.tokens }}（直近約24時間）")
    add("直近1分の推定トークン量：${s.recentMinute(now).sumOf { it.tokens }} / アプリ上限${OPENAI_LOCAL_TOKENS_PER_MINUTE}")
    add("推定はUTF-8のバイト数に余裕を加えた多めの見積もりです。実消費量・課金額ではありません。失敗・再送も含み、キャッシュ利用分は含みません。24時間の集計は最大1時間多めです。")
    add("最後のAPI応答：${s.lastStatus?.let { "HTTP $it（${usageTime(s.lastResponseAt)}）" } ?: "未取得"}")
    add("記録開始からの成功応答：${s.successfulRequests}回 / 429応答：${s.rateLimitedRequests}回")
    if (s.lastStatus == 429) add("APIの待機指定（Retry-After）：${s.serverRetryAfterMillis?.let { openAiWaitText(it, 0) } ?: "未通知。制限の種類・解除時刻は確定できません。"}")
    fun remaining(label: String, value: Long?, limit: Long?, reset: Long?) {
        add("API通知の$label：${value?.toString() ?: "不明"} / ${limit?.toString() ?: "上限不明"}" +
            (reset?.let { "（リセット目安 ${usageTime(it)}）" } ?: ""))
    }
    remaining("リクエスト残量", s.remainingRequests, s.requestLimit, s.requestsResetAt)
    remaining("トークン残量", s.remainingTokens, s.tokenLimit, s.tokensResetAt)
    if (s.remainingProjectTokens != null || s.projectTokenLimit != null) {
        remaining("プロジェクトのトークン残量", s.remainingProjectTokens, s.projectTokenLimit, s.projectTokensResetAt)
    }
    add("API残量は最後の応答時点の値です。未通知の値は不明と表示します。他の端末・アプリの利用は端末集計に含まず、実際の上限はOpenAI側の設定に従います。")
    if (s.storageError) add("利用状況を保存・復元できませんでした。再起動後に集計や待機期限を引き継げない可能性があります。")
}

@Composable
private fun rememberUsageNow(): Long {
    var now by remember { mutableStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(Unit) { while (true) { delay(1_000); now = Clock.System.now().toEpochMilliseconds() } }
    return now
}

@Composable
internal fun OpenAiUsageControls(store: AiConnectionStore) {
    val usage by store.usage.state.collectAsState()
    val connection by store.state.collectAsState()
    val now = rememberUsageNow()
    val openUrl = rememberUrlLauncher()
    Column(Modifier.fillMaxWidth().testTag("openai-usage"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("OpenAIの利用状況", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        connection.apiKeys.firstOrNull { it.id == usage.activeKeyId }?.let {
            Text("最後の送信先：${it.name}。下記の送信量は全APIの合計、API通知の残量は最後の応答の値です。キーごとの状態はAPI管理で確認できます。", color = MaterialTheme.colorScheme.onSurface)
        }
        if (connection.apiKeys.size > 1) {
            val usable = connection.apiKeys.filter { it.enabled && usage.keys[it.id]?.blockedReason.isNullOrEmpty() }
            val waits = usable.map { usage.keys[it.id]?.requiredWait(now, 1) ?: 0L }
            val status = when {
                usage.waitReason in setOf("local_tokens", "local_requests") && usage.waitUntil > now -> "この端末全体の送信量制限による待機：残り${openAiWaitText(usage.waitUntil, now)}"
                waits.any { it == 0L } -> "利用可能なAPIがあります。制限中のAPIを避けて自動で切り替えます。"
                waits.isNotEmpty() -> "全APIが待機中です。最も早い再試行の目安：残り${openAiWaitText(now + waits.min(), now)}"
                else -> "利用可能なAPIがありません。API管理で有効設定・キー・利用枠を確認してください。"
            }
            Text(status, color = MaterialTheme.colorScheme.onSurface)
        }
        openAiUsageLines(usage, now, includeWaitStatus = connection.apiKeys.size <= 1).forEach { Text(it, color = MaterialTheme.colorScheme.onSurface) }
        TextButton(onClick = { openUrl("https://platform.openai.com/usage") }) { Text("OpenAIで実際の利用状況を確認") }
        TextButton(onClick = { openUrl("https://platform.openai.com/settings/organization/limits") }) { Text("OpenAIで利用上限を確認") }
    }
}

/** A failure notice only; normal analysis never displays a progress panel. */
@Composable
internal fun OpenAiLimitNotice(enabled: Boolean, connectionStore: AiConnectionStore? = null) {
    if (!enabled) return
    val context = LocalPlatformContext.current
    val store = connectionStore ?: remember(context) { getAiConnectionStore(context) }
    val connection by store.state.collectAsState()
    if (connection.moderationProvider == AiProvider.DEVICE) return
    val usage by store.usage.state.collectAsState()
    var dismissed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(usage.lastStatus) { if (usage.lastStatus in 200..299) dismissed = false }
    if (usage.lastStatus != 429 || dismissed) return
    // Checked before starting the 1-second ticker: while another key is usable nothing is shown,
    // and only a usage change (a new response) can make every key wait again.
    val current = Clock.System.now().toEpochMilliseconds()
    if (connection.apiKeys.size > 1 && connection.apiKeys.any { key ->
        val status = usage.keys[key.id]
        key.enabled && status?.blockedReason.isNullOrEmpty() && (status?.requiredWait(current, 1) ?: 0) == 0L
    }) return
    rememberUsageNow()
    FutachaAppLockAwareWindow { AlertDialog(
        onDismissRequest = { dismissed = true },
        title = { Text("OpenAIの判定を一時停止（429）") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text("利用制限のため、一部のレスを判定できません。未判定のレスは表示したままです。利用状況は「設定 → AI・補助機能」でも確認できます。",
                    color = MaterialTheme.colorScheme.onSurface)
                OpenAiUsageControls(store)
            }
        },
        confirmButton = { TextButton(onClick = { dismissed = true }) { Text("閉じる") } }
    ) }
}
