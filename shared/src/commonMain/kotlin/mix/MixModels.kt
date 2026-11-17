package com.valoser.futacha.shared.mix

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogItem
import kotlinx.serialization.Serializable

/** まとめカタログ（MIX）の保存の形。キー `compat.mix.definitions`（4モード共有）。 */
const val MIX_DEFINITIONS_KEY = "compat.mix.definitions"
const val MIX_FORMAT_VERSION = 1
const val MAX_MIX_COUNT = 10
const val MAX_MIX_MEMBERS = 10
const val MAX_MIX_NAME_CHARS = 50

@Serializable
data class MixDefinition(
    val id: String,
    val name: String,
    val members: List<String>,
    val order: MixOrderKind = MixOrderKind.ALTERNATE,
    val createdAt: Long
)

@Serializable
enum class MixOrderKind { ALTERNATE, NEW, OLD, MANY, FEW, MOMENTUM, SO }

data class MixEntryKey(val boardUrl: String, val threadId: String) {
    fun asString() = "$boardUrl#$threadId"
}

enum class MixCountKind { REPLIES, SOUDANE }

data class MixEntry(
    val board: BoardSummary,
    val item: CatalogItem,
    val key: MixEntryKey,
    val createdAtMillis: Long?,
    val createdAtEstimated: Boolean,
    val serverRank: Int,
    val serverCount: Int,
    val countKind: MixCountKind = MixCountKind.REPLIES
) {
    /** タブ・履歴・既読の種へ渡す数。そうだねの数は渡さない（null）。 */
    val replyCountOrNull: Int? get() = item.replyCount.takeIf { countKind == MixCountKind.REPLIES }
}

enum class MixSkipReason { NOT_REGISTERED, UNSUPPORTED_SITE, AIMOGE_DISABLED, TUTORIAL_BOARD, DUPLICATE, SITE_PAUSED }
enum class MixFetchFailure { TIMEOUT, RATE_LIMITED, SERVER_ERROR, NOT_FOUND, NETWORK, PARSE, UNKNOWN }

data class MixFetchResult(
    val generation: Long,
    val entries: List<MixEntry>,
    val cutByLimit: Set<String>,
    val failures: Map<String, MixFetchFailure>,
    val skipped: Map<String, MixSkipReason>,
    val fetchedAt: Long
)

/** 利用者に見える文では「まとめカタログ」と書く。 */
const val MIX_CREATE_THREAD_REFUSED = "まとめカタログではスレッドを立てられません。板のカタログから立ててください"
const val MIX_BOARD_ONLY = "この操作は板のカタログで使ってください"
const val MIX_AI_REFUSED = "まとめカタログではAIの操作は使えません。板のカタログで使ってください"
const val MIX_NO_NEIGHBOR_BOARD = "まとめカタログでは隣の板へ移れません"
