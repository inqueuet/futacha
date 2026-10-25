package com.valoser.futacha.shared.ui.board

/** Shared across modes; absent keeps the established top alignment. */
internal const val BOARDS_FROM_BOTTOM_KEY = "compat.control.controlBoardsFromBottom"

/** The same switch is in each mode's settings; the note says so (L26: ふたばー風 did not). */
internal const val BOARDS_FROM_BOTTOM_NOTE = "4つのモードで共通の設定です。板が少ない時に下へ寄せます。並び順は変わりません。"

internal fun boardsFromBottom(preferences: Map<String, String>): Boolean =
    preferences[BOARDS_FROM_BOTTOM_KEY]?.let { it == "ON" || it.equals("true", ignoreCase = true) } == true
