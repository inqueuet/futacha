package com.valoser.futacha.shared.ui.board

/** Shared across modes; absent keeps the established top alignment. */
internal const val BOARDS_FROM_BOTTOM_KEY = "compat.control.controlBoardsFromBottom"

internal fun boardsFromBottom(preferences: Map<String, String>): Boolean =
    preferences[BOARDS_FROM_BOTTOM_KEY]?.let { it == "ON" || it.equals("true", ignoreCase = true) } == true
