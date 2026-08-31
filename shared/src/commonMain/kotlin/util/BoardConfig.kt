package com.valoser.futacha.shared.util

data class BoardConfig(
    val scheme: String = "https",
    val host: String = "www.example.com",
    val board: String = "t"
) {
    val baseUrl: String = "$scheme://$host/$board"
}
