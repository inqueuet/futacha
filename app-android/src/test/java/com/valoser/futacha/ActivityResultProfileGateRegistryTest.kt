package com.valoser.futacha

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityResultProfileGateRegistryTest {
    @Test
    fun rawActivityResultApisAreConfinedToGenerationGatedLauncher() {
        val root = findProjectRoot()
        val sourceRoots = listOf(
            File(root, "app-android/src/main"),
            File(root, "shared/src/androidMain")
        )
        val sourceFiles = sourceRoots.flatMap { directory ->
            directory.walkTopDown().filter { it.isFile && it.extension in setOf("kt", "java") }.toList()
        }
        val rawComposeLaunchers = sourceFiles.filter { file ->
            "rememberLauncherForActivityResult(" in file.readText()
        }.map { it.relativeTo(root).invariantSeparatorsPath }.toSet()

        assertEquals(
            "Every Activity Result entry must use the profile/generation-gated wrapper",
            setOf("shared/src/androidMain/kotlin/compat/ExperienceProfileActivityResultLauncher.android.kt"),
            rawComposeLaunchers
        )

        val forbiddenLegacyCalls = listOf("registerForActivityResult(", "startActivityForResult(", "onActivityResult(")
        forbiddenLegacyCalls.forEach { call ->
            val offenders = sourceFiles.filter { call in it.readText() }
                .map { it.relativeTo(root).invariantSeparatorsPath }
            assertTrue("Legacy Activity Result API $call found in $offenders", offenders.isEmpty())
        }
    }

    @Test
    fun gatedLauncherCapturesConsumesAndClearsSessionAuthority() {
        val helper = File(
            findProjectRoot(),
            "shared/src/androidMain/kotlin/compat/ExperienceProfileActivityResultLauncher.android.kt"
        ).readText()

        assertTrue("markLaunched(controllerState.value)" in helper)
        assertTrue("consumeIfCurrent(controllerState.value)" in helper)
        assertTrue("onDispose { resultGate.clear() }" in helper)
        assertTrue("catch (error: Throwable)" in helper && "resultGate.clear()" in helper)
    }

    private fun findProjectRoot(): File {
        val workingDirectory = checkNotNull(System.getProperty("user.dir"))
        var current = File(workingDirectory).absoluteFile
        while (true) {
            if (File(current, "settings.gradle.kts").isFile) return current
            current = current.parentFile ?: error("Could not locate project root from $workingDirectory")
        }
    }
}
