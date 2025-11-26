package com.valoser.futacha.shared.service

// 手動保存は共有Documents/futacha配下に置き、ユーザーが参照できるようにする
const val MANUAL_SAVE_DIRECTORY = "saved_threads"
const val DEFAULT_MANUAL_SAVE_ROOT = "Documents"

// 自動保存は FileSystem 側でアプリ専用の非公開ディレクトリへ解決される
const val AUTO_SAVE_DIRECTORY = "autosaved_threads"
