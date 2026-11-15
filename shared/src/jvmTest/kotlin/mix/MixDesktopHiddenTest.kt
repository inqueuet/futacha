package com.valoser.futacha.shared.mix

import com.valoser.futacha.shared.compat.MixAvailability
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** The Desktop host never turns まとめカタログ on, and the watch's board list never carries MIX definitions. */
class MixDesktopHiddenTest {
    @Test fun offUntilAHostTurnsItOn() { assertFalse(MixAvailability.enabled) }

    @Test fun noDesktopOrWatchSourceTurnsItOnOrSyncsMixes() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
        val hits = root.walkTopDown().onEnter { it.name !in setOf("build", ".git", ".claude", "node_modules") }
            .filter { it.isFile && it.extension == "kt" && (it.relativeTo(root).path.lowercase().let { r -> "desktop" in r || "wear" in r }) }
            .filter { !it.name.contains("Test") }.filter { f -> f.readText().let { "MixAvailability.enabled = true" in it || "MIX_DEFINITIONS_KEY" in it } }
            .map { it.path }.toList()
        assertEquals(emptyList<String>(), hits)
    }

    @Test fun siriAndAppFunctionsNeverSeeMixes() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
        val hits = root.walkTopDown().onEnter { it.name !in setOf("build", ".git", ".claude", "node_modules", "Pods") }
            .filter { it.isFile && (it.extension == "swift" || it.extension == "kt") }
            .filter { "iosApp/iosApp" in it.path || it.name.contains("AppFunction", ignoreCase = true) }
            .filter { !it.path.contains("Test") && Regex("(?i)mix(Store|Definition|Catalog)|MIX_DEFINITIONS").containsMatchIn(it.readText()) }
            .map { it.path }.toList()
        assertEquals(emptyList<String>(), hits)
    }
}
