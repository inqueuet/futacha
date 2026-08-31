package com.valoser.futacha.shared.compat

internal enum class CompatSelectorActionEffect {
    NONE,
    CHECK_UPDATES,
    RELOAD_CURRENT,
    SELECT_TAB,
    REPLY_CURRENT,
    CLOSE_TAB,
    OPEN_MENU
}

internal fun resolveCompatSelectorLongTapEffect(
    configuredAction: String,
    threadContext: Boolean,
    isCurrentTab: Boolean
): CompatSelectorActionEffect = when (configuredAction) {
    "何もしない" -> CompatSelectorActionEffect.NONE
    "更新の確認" -> CompatSelectorActionEffect.CHECK_UPDATES
    "再読み込み" -> if (threadContext && isCurrentTab) {
        CompatSelectorActionEffect.RELOAD_CURRENT
    } else {
        CompatSelectorActionEffect.SELECT_TAB
    }
    "レスを書き込む" -> if (threadContext && isCurrentTab) {
        CompatSelectorActionEffect.REPLY_CURRENT
    } else {
        CompatSelectorActionEffect.SELECT_TAB
    }
    "スレを閉じる" -> CompatSelectorActionEffect.CLOSE_TAB
    else -> CompatSelectorActionEffect.OPEN_MENU
}

internal fun resolveCompatSelectorMenuEffect(choice: String): CompatSelectorActionEffect = when (choice) {
    "更新の確認" -> CompatSelectorActionEffect.CHECK_UPDATES
    "再読み込み" -> CompatSelectorActionEffect.RELOAD_CURRENT
    "レスを書き込む" -> CompatSelectorActionEffect.REPLY_CURRENT
    "スレを閉じる" -> CompatSelectorActionEffect.CLOSE_TAB
    else -> CompatSelectorActionEffect.NONE
}

internal fun compatSelectorContextChoices(
    threadContext: Boolean,
    isCurrentTab: Boolean
): List<String> = buildList {
    add("更新の確認")
    if (threadContext && isCurrentTab) {
        add("再読み込み")
        add("レスを書き込む")
    }
    add("スレを閉じる")
}
