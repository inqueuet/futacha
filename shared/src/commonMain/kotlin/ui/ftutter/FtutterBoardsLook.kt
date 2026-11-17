package com.valoser.futacha.shared.ui.ftutter

import androidx.compose.ui.graphics.Color

/**
 * T-B10 M32：板一覧を原作（ItaList）に合わせる決まり。
 *
 * - お気に入りの星は黄色、行は高さ72で、板名は17・アドレスは14の灰色。
 * - 長押しは題も「閉じる」も無い2行（ブラウザで開く／削除）。
 * - 板の追加は下線の欄（×で消す）と「キャンセル／OK」。空なら閉じ、形式の誤りは閉じてトースト（M62）。
 * - 「URLから開く」で登録していない板は、その板を足して開く。ふたばでないURLは「URLが対応していない形式です」（M63）。
 * - 今までの形は「従来の動き」のスイッチで残す。
 */
internal val FTUTTER_LEGACY_BOARDS_KEY: String get() = FtutterKeys.PREFIX + "legacyBoards"
internal const val FTUTTER_LEGACY_BOARDS_LABEL = "板一覧の見た目を前の形にする"
internal const val FTUTTER_LEGACY_BOARDS_NOTE =
    "ONにすると、板一覧の星を文字の色に、行を詰めた形に、長押しを板名の題と「閉じる」のあるダイアログに、板の追加を枠の欄と「追加」のボタン（形式の誤りは欄の下に出して閉じない）に、「URLから開く」を登録した板だけに戻します。OFFは原作と同じ、黄色の星・高さのある行・題の無い長押し・下線の欄と「OK」（空なら閉じ、形式の誤りは閉じてトースト）、「URLから開く」で登録していない板はその板を足して開きます"

internal fun ftutterLegacyBoards(preferences: Map<String, String>): Boolean =
    preferences.ftutterBool(FTUTTER_LEGACY_BOARDS_KEY, false)

/** 原作のお気に入りの星の黄色。 */
internal val FtutterStarYellow = Color(0xFFFFC107)
