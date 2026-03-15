package com.valoser.futacha.shared.network

internal enum class HttpBoardApiPostEncoding {
    SHIFT_JIS,
    UTF8
}

private const val MAX_NAME_LENGTH = 100
private const val MAX_EMAIL_LENGTH = 100
private const val MAX_SUBJECT_LENGTH = 100
private const val MAX_COMMENT_LENGTH = 10000
private const val MAX_PASSWORD_LENGTH = 100
private const val MAX_IMAGE_FILE_SIZE = 8_192_000L

internal fun validateHttpBoardApiPostInput(
    name: String,
    email: String,
    subject: String,
    comment: String,
    password: String,
    imageFile: ByteArray?
) {
    listOf(
        "name" to name,
        "email" to email,
        "subject" to subject,
        "comment" to comment,
        "password" to password
    ).forEach { (fieldName, value) ->
        if (value.contains('\u0000')) {
            throw IllegalArgumentException("$fieldName contains null character")
        }
    }

    if (name.length > MAX_NAME_LENGTH) {
        throw IllegalArgumentException("Name exceeds maximum length ($MAX_NAME_LENGTH): ${name.length}")
    }
    if (email.length > MAX_EMAIL_LENGTH) {
        throw IllegalArgumentException("Email exceeds maximum length ($MAX_EMAIL_LENGTH): ${email.length}")
    }
    if (subject.length > MAX_SUBJECT_LENGTH) {
        throw IllegalArgumentException("Subject exceeds maximum length ($MAX_SUBJECT_LENGTH): ${subject.length}")
    }
    if (comment.length > MAX_COMMENT_LENGTH) {
        throw IllegalArgumentException("Comment exceeds maximum length ($MAX_COMMENT_LENGTH): ${comment.length}")
    }
    if (password.length > MAX_PASSWORD_LENGTH) {
        throw IllegalArgumentException("Password exceeds maximum length ($MAX_PASSWORD_LENGTH): ${password.length}")
    }

    imageFile?.let { file ->
        if (file.size > MAX_IMAGE_FILE_SIZE) {
            throw IllegalArgumentException(
                "Image file size (${file.size} bytes) exceeds maximum ($MAX_IMAGE_FILE_SIZE bytes)"
            )
        }
    }
}

internal fun validateHttpBoardApiDeletionPassword(password: String) {
    if (password.contains('\u0000')) {
        throw IllegalArgumentException("password contains null character")
    }
    if (password.length > MAX_PASSWORD_LENGTH) {
        throw IllegalArgumentException("Password exceeds maximum length ($MAX_PASSWORD_LENGTH): ${password.length}")
    }
    if (password.isBlank()) {
        throw IllegalArgumentException("Password must not be blank")
    }
}

internal fun validateHttpBoardApiReasonCode(reasonCode: String) {
    if (reasonCode.contains('\u0000')) {
        throw IllegalArgumentException("reasonCode contains null character")
    }
    if (reasonCode.length > 50) {
        throw IllegalArgumentException("Reason code exceeds maximum length (50): ${reasonCode.length}")
    }
}

internal fun determineHttpBoardApiEncoding(chrencValue: String): HttpBoardApiPostEncoding {
    val normalized = chrencValue.lowercase()
    return if (normalized.contains("unicode") || normalized.contains("utf")) {
        HttpBoardApiPostEncoding.UTF8
    } else {
        HttpBoardApiPostEncoding.SHIFT_JIS
    }
}
