package com.valoser.futacha.shared.network

class NetworkException(
    message: String,
    val statusCode: Int? = null,
    cause: Throwable? = null
) : Exception(message, cause)
