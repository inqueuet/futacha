package com.valoser.futacha.shared.model

import kotlinx.serialization.Serializable

/** Last confirmed board response. A network failure never changes this value. */
@Serializable
data class ThreadStatus(
    val isDead: Boolean = false,
    val isIsolated: Boolean = false,
    val isDeleted: Boolean = false,
    val isAdminDeleted: Boolean = false
)
