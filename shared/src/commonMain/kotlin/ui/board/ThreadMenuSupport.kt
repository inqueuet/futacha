package com.valoser.futacha.shared.ui.board

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.valoser.futacha.shared.model.ThreadMenuEntryConfig
import com.valoser.futacha.shared.model.ThreadMenuEntryId
import com.valoser.futacha.shared.model.ThreadMenuEntryPlacement
import com.valoser.futacha.shared.model.normalizeThreadMenuEntries

internal data class ThreadPostActionSheetState(
    val isSaidaneEnabled: Boolean
)

internal fun resolveThreadPostActionSheetState(
    isSelfPost: Boolean
): ThreadPostActionSheetState {
    return ThreadPostActionSheetState(
        isSaidaneEnabled = !isSelfPost
    )
}

internal enum class ThreadScrollTarget {
    Top,
    Bottom
}

internal data class ThreadMenuActionState(
    val showReplyDialog: Boolean = false,
    val applyReplyDeleteKeyAutofill: Boolean = false,
    val scrollTarget: ThreadScrollTarget? = null,
    val showRefreshBusyMessage: Boolean = false,
    val startRefresh: Boolean = false,
    val delegateToSaveHandler: Boolean = false,
    val showGallery: Boolean = false,
    val showFilterSheet: Boolean = false,
    val showSettingsSheet: Boolean = false,
    val showNgManagement: Boolean = false,
    val clearNgHeaderPrefill: Boolean = false,
    val openExternalApp: Boolean = false,
    val showReadAloudControls: Boolean = false,
    val togglePrivacy: Boolean = false
)

internal fun resolveThreadMenuActionState(
    entryId: ThreadMenuEntryId,
    isRefreshing: Boolean
): ThreadMenuActionState {
    return when (entryId) {
        ThreadMenuEntryId.Reply -> ThreadMenuActionState(
            showReplyDialog = true,
            applyReplyDeleteKeyAutofill = true
        )
        ThreadMenuEntryId.ScrollToTop -> ThreadMenuActionState(scrollTarget = ThreadScrollTarget.Top)
        ThreadMenuEntryId.ScrollToBottom -> ThreadMenuActionState(scrollTarget = ThreadScrollTarget.Bottom)
        ThreadMenuEntryId.Refresh -> when (resolveThreadRefreshAvailability(isRefreshing)) {
            ThreadRefreshAvailability.Busy -> ThreadMenuActionState(showRefreshBusyMessage = true)
            ThreadRefreshAvailability.Ready -> ThreadMenuActionState(startRefresh = true)
        }
        ThreadMenuEntryId.Gallery -> ThreadMenuActionState(showGallery = true)
        ThreadMenuEntryId.Save -> ThreadMenuActionState(delegateToSaveHandler = true)
        ThreadMenuEntryId.Filter -> ThreadMenuActionState(showFilterSheet = true)
        ThreadMenuEntryId.Settings -> ThreadMenuActionState(showSettingsSheet = true)
        ThreadMenuEntryId.NgManagement -> ThreadMenuActionState(
            showNgManagement = true,
            clearNgHeaderPrefill = true
        )
        ThreadMenuEntryId.ExternalApp -> ThreadMenuActionState(openExternalApp = true)
        ThreadMenuEntryId.ReadAloud -> ThreadMenuActionState(showReadAloudControls = true)
        ThreadMenuEntryId.Privacy -> ThreadMenuActionState(togglePrivacy = true)
    }
}

internal enum class ThreadBackAction {
    CloseDrawer,
    NavigateBack
}

internal fun resolveThreadBackAction(
    isDrawerOpen: Boolean
): ThreadBackAction {
    return if (isDrawerOpen) {
        ThreadBackAction.CloseDrawer
    } else {
        ThreadBackAction.NavigateBack
    }
}

internal data class ThreadSettingsActionState(
    val closeSheet: Boolean = true,
    val showNgManagement: Boolean = false,
    val openExternalApp: Boolean = false,
    val togglePrivacy: Boolean = false,
    val showReadAloudControls: Boolean = false,
    val reopenSettingsSheet: Boolean = false,
    val delegateToMainActionHandler: Boolean = false
)

internal fun resolveThreadSettingsActionState(
    menuEntryId: ThreadMenuEntryId
): ThreadSettingsActionState {
    return when (menuEntryId) {
        ThreadMenuEntryId.NgManagement -> ThreadSettingsActionState(showNgManagement = true)
        ThreadMenuEntryId.ExternalApp -> ThreadSettingsActionState(openExternalApp = true)
        ThreadMenuEntryId.Privacy -> ThreadSettingsActionState(togglePrivacy = true)
        ThreadMenuEntryId.ReadAloud -> ThreadSettingsActionState(showReadAloudControls = true)
        ThreadMenuEntryId.Settings -> ThreadSettingsActionState(reopenSettingsSheet = true)
        else -> ThreadSettingsActionState(delegateToMainActionHandler = true)
    }
}

internal fun resolveThreadActionBarEntries(menuEntries: List<ThreadMenuEntryConfig>): List<ThreadMenuEntryConfig> {
    val normalized = normalizeThreadMenuEntries(menuEntries)
    return normalized
        .filter { it.placement == ThreadMenuEntryPlacement.BAR }
        .sortedWith(compareBy<ThreadMenuEntryConfig> { it.order }.thenBy { it.id.defaultOrder })
}

internal fun resolveThreadSettingsMenuEntries(menuEntries: List<ThreadMenuEntryConfig>): List<ThreadMenuEntryConfig> {
    return normalizeThreadMenuEntries(menuEntries)
        .filter { it.placement == ThreadMenuEntryPlacement.SHEET }
        .sortedWith(compareBy<ThreadMenuEntryConfig> { it.order }.thenBy { it.id.defaultOrder })
}

internal data class ThreadMenuEntryMeta(
    val label: String,
    val icon: ImageVector
)

internal fun ThreadMenuEntryId.toMeta(): ThreadMenuEntryMeta {
    return when (this) {
        ThreadMenuEntryId.Reply -> ThreadMenuEntryMeta("返信", Icons.Rounded.Edit)
        ThreadMenuEntryId.ScrollToTop -> ThreadMenuEntryMeta("最上部", Icons.Filled.ArrowUpward)
        ThreadMenuEntryId.ScrollToBottom -> ThreadMenuEntryMeta("最下部", Icons.Filled.ArrowDownward)
        ThreadMenuEntryId.Refresh -> ThreadMenuEntryMeta("更新", Icons.Rounded.Refresh)
        ThreadMenuEntryId.Gallery -> ThreadMenuEntryMeta("画像", Icons.Outlined.Image)
        ThreadMenuEntryId.Save -> ThreadMenuEntryMeta("保存", Icons.Rounded.Archive)
        ThreadMenuEntryId.Filter -> ThreadMenuEntryMeta("レスフィルター", Icons.Rounded.FilterList)
        ThreadMenuEntryId.Settings -> ThreadMenuEntryMeta("設定", Icons.Rounded.Settings)
        ThreadMenuEntryId.NgManagement -> ThreadMenuEntryMeta("NG管理", Icons.Rounded.Block)
        ThreadMenuEntryId.ExternalApp -> ThreadMenuEntryMeta("外部アプリ", Icons.AutoMirrored.Rounded.OpenInNew)
        ThreadMenuEntryId.ReadAloud -> ThreadMenuEntryMeta("読み上げ", Icons.AutoMirrored.Rounded.VolumeUp)
        ThreadMenuEntryId.Privacy -> ThreadMenuEntryMeta("プライバシー", Icons.Rounded.Lock)
    }
}

internal fun ThreadMenuEntryConfig.toMeta(): ThreadMenuEntryMeta = id.toMeta()
