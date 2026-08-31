package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.QuoteReference
import kotlinx.serialization.Serializable

@Serializable
data class CompatBoard(
    val key: String,
    val name: String,
    val canonicalUrl: String,
    val originalUrl: String,
    val sortOrder: Int
) {
    fun toBoardSummary(): BoardSummary = BoardSummary(
        id = key,
        name = name,
        category = "互換モード",
        url = originalUrl,
        description = canonicalUrl
    )
}

@Serializable
data class ScrollAnchor(
    val postNo: String? = null,
    val offsetPx: Int = 0,
    val fallbackIndex: Int = 0,
    val snapshotRevision: Long = 0L
)

@Serializable
data class CompatTab(
    val key: String,
    val canonicalUrl: String,
    val originalUrl: String,
    val boardKey: String,
    val boardName: String,
    val threadNo: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val replyCount: Int = 0,
    val checkedReplyCount: Int = 0,
    val isDead: Boolean = false,
    /** ThreadData.FLAGS_DELETED (the normal user-deleted state). */
    val isDeleted: Boolean = false,
    val isIsolated: Boolean = false,
    val isExploded: Boolean = false,
    val isOld: Boolean = false,
    val favorite: Boolean = false,
    val insertedAtEpochMillis: Long,
    val contentUpdatedAtEpochMillis: Long,
    val scrollAnchor: ScrollAnchor = ScrollAnchor(),
    val snapshotRevision: Long = 0L,
    /** Whether opening this catalog item should force one live thread request. */
    val refreshOnActivation: Boolean = false
) {
    val unreadCount: Int get() = (replyCount - checkedReplyCount).coerceAtLeast(0)
}

/** Mirrors the reference toolbar's red update variant for the tab button. */
fun hasCompatTabToolbarUpdate(tabs: List<CompatTab>): Boolean =
    tabs.any { !it.isDead && it.unreadCount > 0 }

@Serializable
data class CompatHistoryEntry(
    val canonicalUrl: String,
    val originalUrl: String,
    val boardKey: String,
    val boardName: String,
    val threadNo: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val replyCount: Int = 0,
    val contentUpdatedAtEpochMillis: Long,
    val scrollAnchor: ScrollAnchor = ScrollAnchor()
)

@Serializable
data class CompatReplyDraft(
    val tabKey: String,
    val name: String = "",
    val email: String = "",
    val subject: String = "",
    val comment: String = "",
    val attachmentUri: String? = null,
    val deleteKey: String = "",
    val updatedAtEpochMillis: Long
)

/**
 * PostBuild owns a board-scoped draft rather than a thread-tab draft.
 * Keeping the two records distinct mirrors the target Activity lifetime and
 * prevents opening a reply form from accidentally inheriting a thread build.
 */
@Serializable
data class CompatBuildDraft(
    val boardKey: String,
    val name: String = "",
    val email: String = "",
    val subject: String = "",
    val comment: String = "",
    val attachmentUri: String? = null,
    val deleteKey: String = "",
    val updatedAtEpochMillis: Long
)

@Serializable
data class CompatPostSnapshot(
    val position: Int,
    val postNo: String,
    val author: String? = null,
    val subject: String? = null,
    val mail: String? = null,
    val timestamp: String,
    val posterId: String? = null,
    val messageHtml: String,
    val imageUrl: String? = null,
    val thumbnailUrl: String? = null,
    val saidaneLabel: String? = null,
    val isDeleted: Boolean = false,
    val isIsolated: Boolean = false,
    /** True only for the reference-compatible notice shown while deleted content is hidden. */
    val isContentRedacted: Boolean = false,
    val referencedCount: Int = 0,
    /** Source thumbnail dimensions used to preserve the target's aspect-ratio layout. */
    val thumbnailWidth: Int? = null,
    val thumbnailHeight: Int? = null,
    /** Non-null only for a synthetic gallery/viewer item from an inline media link. */
    val mediaKey: String? = null,
    /** Retained for modern-mode quote navigation when a compatibility cache is reused. */
    val quoteReferences: List<QuoteReference> = emptyList()
)

@Serializable
data class CompatThreadSnapshot(
    val tabKey: String,
    val revision: Long,
    val fetchedAtEpochMillis: Long,
    val boardTitle: String? = null,
    val expiresAtLabel: String? = null,
    val deletedNotice: String? = null,
    val posts: List<CompatPostSnapshot>,
    /** Preserves parser truncation state so a partial server cache cannot replace a fuller local copy. */
    val isTruncated: Boolean = false,
    val truncationReason: String? = null
)

@Serializable
data class ClosedCompatTab(
    val tab: CompatTab,
    val originalIndex: Int,
    val draft: CompatReplyDraft? = null,
    val snapshot: CompatThreadSnapshot? = null
)

@Serializable
data class ClosedTabBatch(
    val id: String,
    val tabs: List<ClosedCompatTab>,
    val selectedTabKey: String?,
    val expiresAtEpochMillis: Long
)

enum class SelectorPresentation { ABOVE, OVER }

@Serializable
data class CompatWorkspaceRecord(
    val activeTabKey: String? = null,
    val catalogHostBoardKey: String? = null,
    val mainSelectorOpen: Boolean = false,
    val catalogSelectorOpen: Boolean = false,
    val threadSelectorOpen: Boolean = false,
    val selectorPresentation: SelectorPresentation = SelectorPresentation.ABOVE,
    val generation: Long = 0L
)

enum class CompatCatalogSort { CATALOG, NEW, OLD, MANY, FEW, LIVELY }

enum class CompatCatalogLayout { GRID, LIST }

data class CompatCatalogSnapshot(
    val boardKey: String,
    val sort: CompatCatalogSort,
    val revision: Long,
    val fetchedAtEpochMillis: Long,
    val items: List<CatalogItem>,
    val itemStates: Map<String, CompatCatalogItemState> = emptyMap()
)

enum class CompatCatalogDroppedClass { DIE, ISOLATED, DELETED }

data class CompatCatalogItemState(
    val createdAtEpochSeconds: Long,
    val isOld: Boolean
)

data class CompatDroppedCatalogItem(
    val boardKey: String,
    val item: CatalogItem,
    val droppedAtEpochMillis: Long,
    val lastSeenAtEpochMillis: Long = droppedAtEpochMillis,
    val classification: CompatCatalogDroppedClass = CompatCatalogDroppedClass.DIE
)

@Serializable
data class CompatCatalogSnapshotItem(
    val id: String,
    val threadUrl: String,
    val title: String?,
    val thumbnailUrl: String?,
    val fullImageUrl: String?,
    val thumbnailWidth: Int?,
    val thumbnailHeight: Int?,
    val replyCount: Int,
    val expiresAtEpochMillis: Long?,
    val createdAtEpochSeconds: Long = 0L,
    val isOld: Boolean = false
) {
    fun toCatalogItem(): CatalogItem = CatalogItem(
        id = id,
        threadUrl = threadUrl,
        title = title,
        thumbnailUrl = thumbnailUrl,
        fullImageUrl = fullImageUrl,
        thumbnailWidth = thumbnailWidth,
        thumbnailHeight = thumbnailHeight,
        replyCount = replyCount,
        expiresAtEpochMillis = expiresAtEpochMillis
    )

    companion object {
        fun from(
            item: CatalogItem,
            state: CompatCatalogItemState? = null
        ): CompatCatalogSnapshotItem = CompatCatalogSnapshotItem(
            id = item.id,
            threadUrl = item.threadUrl,
            title = item.title,
            thumbnailUrl = item.thumbnailUrl,
            fullImageUrl = item.fullImageUrl,
            thumbnailWidth = item.thumbnailWidth,
            thumbnailHeight = item.thumbnailHeight,
            replyCount = item.replyCount,
            expiresAtEpochMillis = item.expiresAtEpochMillis,
            createdAtEpochSeconds = state?.createdAtEpochSeconds ?: 0L,
            isOld = state?.isOld ?: false
        )
    }

    fun toState(): CompatCatalogItemState = CompatCatalogItemState(createdAtEpochSeconds, isOld)
}

@Serializable
data class CompatCatalogPreference(
    val boardKey: String,
    val sort: CompatCatalogSort = CompatCatalogSort.CATALOG,
    val layout: CompatCatalogLayout = CompatCatalogLayout.GRID,
    /** 1.apk's CatalogSortData.Default() enables both reply-priority flags. */
    val replyPriorityEnabled: Boolean = true,
    val showNonPriority: Boolean = true,
    val fewRepliesDelay: Int = 0
)

@Serializable
enum class CompatNgKind {
    CATALOG_THREAD,
    CATALOG_WORD,
    /** The legacy CatalogExtract word: highlight/sort, but do not hide. */
    CATALOG_EXTRACT,
    /** The legacy CatalogIgnore word: hide matching catalog entries. */
    CATALOG_IGNORE,
    /** The legacy CatalogRefuse entry: hide one exact thread. */
    CATALOG_REFUSE,
    CATALOG_IMAGE,
    CATALOG_IMAGE_PHASH,
    THREAD_POST_NO,
    THREAD_POSTER_ID,
    THREAD_WORD,
    /** Body-only thread ignore rule from the legacy keyword database. */
    THREAD_IGNORE,
    /** Header-only thread refuse rule from the legacy keyword database. */
    THREAD_REFUSE,
    THREAD_IMAGE,
    THREAD_IMAGE_PHASH
}

@Serializable
data class CompatNgRule(
    val id: String,
    val kind: CompatNgKind,
    val scopeKey: String,
    val normalizedValue: String,
    val createdAtEpochMillis: Long,
    /** pHash rules store a 16-digit hexadecimal perceptual hash here. */
    val imageUrl: String? = null,
    /** Optional user-facing note used by the reference image-NG editor. */
    val memo: String = ""
)

const val MAX_COMPAT_NG_RULES = 10_000
const val MAX_COMPAT_NG_RULE_ID_CHARS = 300
const val MAX_COMPAT_NG_SCOPE_KEY_CHARS = 300
const val MAX_COMPAT_NG_VALUE_CHARS = 10_000
const val MAX_COMPAT_NG_IMAGE_URL_CHARS = 8_192
const val MAX_COMPAT_NG_MEMO_CHARS = 300

fun isValidCompatNgRule(rule: CompatNgRule): Boolean =
    rule.id.length in 1..MAX_COMPAT_NG_RULE_ID_CHARS &&
        rule.scopeKey.length in 1..MAX_COMPAT_NG_SCOPE_KEY_CHARS &&
        rule.normalizedValue.length in 1..MAX_COMPAT_NG_VALUE_CHARS &&
        (rule.imageUrl?.length ?: 0) <= MAX_COMPAT_NG_IMAGE_URL_CHARS &&
        rule.memo.length <= MAX_COMPAT_NG_MEMO_CHARS

fun requireValidCompatNgRule(rule: CompatNgRule) {
    require(isValidCompatNgRule(rule)) { "Compatibility NG rule is invalid or too large" }
}

fun compatNgRuleId(kind: CompatNgKind, scopeKey: String, normalizedValue: String): String =
    "compat_ng_${kind.name.lowercase()}_${stableCompatHash("$scopeKey\u0000$normalizedValue")}"

fun isCompatNgScopeValid(
    kind: CompatNgKind,
    scopeKey: String,
    boardKeys: Set<String>,
    tabKeys: Set<String>
): Boolean = when (kind) {
    CompatNgKind.CATALOG_THREAD,
    CompatNgKind.CATALOG_WORD,
    CompatNgKind.CATALOG_EXTRACT,
    CompatNgKind.CATALOG_IGNORE,
    CompatNgKind.CATALOG_REFUSE,
    CompatNgKind.CATALOG_IMAGE,
    CompatNgKind.CATALOG_IMAGE_PHASH -> scopeKey == "*" || scopeKey in boardKeys

    CompatNgKind.THREAD_IMAGE,
    CompatNgKind.THREAD_IMAGE_PHASH -> scopeKey == "*" || scopeKey in boardKeys || scopeKey in tabKeys

    CompatNgKind.THREAD_POST_NO,
    CompatNgKind.THREAD_POSTER_ID,
    CompatNgKind.THREAD_WORD,
    CompatNgKind.THREAD_IGNORE,
    CompatNgKind.THREAD_REFUSE -> scopeKey == "*" || scopeKey in tabKeys
}

/**
 * 1.apk stores image NG's "この板のみ" scope as the board key. `tabKey` is
 * retained as a read-compatible fallback for rules written by earlier
 * Futacha builds which accidentally scoped the rule to one thread.
 */
fun CompatNgRule.appliesToThreadImage(boardKey: String, tabKey: String): Boolean =
    scopeKey == "*" || scopeKey == boardKey || scopeKey == tabKey

fun compatThreadImageNgScopeKey(boardKey: String, localOnly: Boolean): String =
    if (localOnly) boardKey else "*"
