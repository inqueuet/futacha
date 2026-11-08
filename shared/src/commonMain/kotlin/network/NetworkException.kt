package com.valoser.futacha.shared.network

class NetworkException(
    message: String,
    val statusCode: Int? = null,
    cause: Throwable? = null,
    /**
     * The server's own answer to a post, as it came (null when there was none or it was not read). The other modes show
     * [message]; ふたったー風 shows this, as the original does (T-B3).
     */
    val serverBody: String? = null
) : Exception(message, cause)
