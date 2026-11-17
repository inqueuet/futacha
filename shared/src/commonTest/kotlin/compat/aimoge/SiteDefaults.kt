package com.valoser.futacha.shared.compat.aimoge

/**
 * [text] (a data class `toString()`) without the あいもげ fields added in AM-B2 when they hold their defaults, so a
 * Futaba golden written before AM-B2 still compares. A Futaba value never sets these fields, so nothing real is hidden.
 */
internal fun withoutSiteDefaults(text: String): String = text
    .replace(", attachments=[]", "")
    .replace(", siteUuid=null", "")
    .replace(", displayNumber=null", "")
    .replace(", siteOrder=null", "")
    .replace(
        ", expiresAtEpochMillis=null, closedAtEpochMillis=null, isPermanent=false, archived=false, " +
            "allowImageReplies=null, threadNgWords=[], maxSeq=null",
        ""
    )
