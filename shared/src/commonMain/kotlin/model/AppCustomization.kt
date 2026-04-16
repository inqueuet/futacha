package com.valoser.futacha.shared.model

enum class ThemeMode(val label: String) {
    System("端末設定に合わせる"),
    Light("ライト"),
    Dark("ダーク")
}

enum class ThemePalette(val label: String) {
    Current("ふたちゃテーマ"),
    FutabaClassic("ふたばクラシック"),
    Midnight("ミッドナイト")
}

enum class AppIconVariant(val label: String) {
    Current("デフォルト"),
    Classic("クラシック"),
    Midnight("ミッドナイト")
}

enum class ThreadDisplayMode(val label: String) {
    Flat("通常表示"),
    Tree("ツリー表示")
}
