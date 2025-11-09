package com.valoser.futacha.shared.network

import io.ktor.client.HttpClient

expect fun createHttpClient(): HttpClient
