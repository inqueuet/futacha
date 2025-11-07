package com.valoser.futacha.shared.util

data class BoardConfig(
    val scheme: String = "https",
    val host: String = "dat.2chan.net",
    val board: String = "t"
) {
    val baseUrl: String = "$scheme://$host/$board"
}
