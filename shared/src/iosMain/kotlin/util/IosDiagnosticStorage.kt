@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.valoser.futacha.shared.util

import platform.Foundation.*

/** Keep diagnostics outside Documents, which is available in the Files app. */
internal fun iosDiagnosticPath(fileName: String): String? {
    val manager = NSFileManager.defaultManager
    val support = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
        .firstOrNull() as? String ?: return null
    val directory = "$support/futacha/diagnostics"
    if (!manager.createDirectoryAtPath(directory, true, null, null)) return null
    val target = "$directory/$fileName"
    val documents = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
        .firstOrNull() as? String
    val legacy = documents?.let { "$it/$fileName" }
    if (legacy != null && manager.fileExistsAtPath(legacy)) {
        val migrationTarget = if (manager.fileExistsAtPath(target)) "$directory/${NSUUID().UUIDString}_$fileName" else target
        if (!manager.fileExistsAtPath(migrationTarget)) {
            manager.moveItemAtPath(legacy, migrationTarget, null)
        }
    }
    return target
}
