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

enum class ThreadBodyTextSize(val label: String) {
    Small("小"),
    Standard("標準"),
    Large("大"),
    ExtraLarge("特大")
}

enum class ThreadPostImageSize(val label: String) {
    ExtraSmall("小"),
    Small("中"),
    Medium("大"),
    Large("特大")
}
