package com.valoser.futacha.shared.network

import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders

private const val MAX_CONDITIONAL_VALIDATOR_LENGTH = 256

/** Validators a server sent with a page, echoed back in a conditional GET. */
data class HttpConditionalValidators(
    val etag: String?,
    val lastModified: String?
) {
    val isEmpty: Boolean
        get() = etag.isNullOrBlank() && lastModified.isNullOrBlank()
}

/** A conditional GET's outcome: the new body, or the server's 304 for the previous one. */
sealed interface ConditionalTextFetchResult {
    data class Modified(
        val body: String,
        val validators: HttpConditionalValidators?
    ) : ConditionalTextFetchResult

    data object NotModified : ConditionalTextFetchResult
}

internal fun HttpResponse.conditionalValidatorsOrNull(): HttpConditionalValidators? {
    fun header(name: String): String? = headers[name]
        ?.trim()
        ?.takeIf { it.isNotEmpty() && it.length <= MAX_CONDITIONAL_VALIDATOR_LENGTH && '\n' !in it && '\r' !in it }
    return HttpConditionalValidators(
        etag = header(HttpHeaders.ETag),
        lastModified = header(HttpHeaders.LastModified)
    ).takeUnless { it.isEmpty }
}
