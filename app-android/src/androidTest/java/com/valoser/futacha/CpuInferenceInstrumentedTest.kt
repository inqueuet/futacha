@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
package com.valoser.futacha

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.shared.media.analysis.CpuInferenceContract
import okio.Path.Companion.toPath
import org.junit.Assume.assumeFalse
import org.junit.Test
import java.io.File

class CpuInferenceInstrumentedTest {
    @Test fun nativeContoursCoverOnlyTheBoxInteriorAndRequireReview() = contract().nativeContoursCoverOnlyTheBoxInteriorAndRequireReview()
    private fun contract() = CpuInferenceContract(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir.absolutePath.toPath())
    @Test fun nativeExecutionReusesCallerBuffersAndSurvivesInvalidRequests() = contract().nativeExecutionReusesCallerBuffersAndSurvivesInvalidRequests()
    @Test fun cancellationInterruptsNativeExecutionAndSameSessionRunsAgain() {
        assumeCpuCanRunOrtSmeKernels()
        contract().cancellationInterruptsNativeExecutionAndSameSessionRunsAgain()
    }

    @Test fun closingSessionAndTensorsDuringNativeExecutionDefersTheirRelease() {
        assumeCpuCanRunOrtSmeKernels()
        contract().closingSessionAndTensorsDuringNativeExecutionDefersTheirRelease()
    }

    @Test fun invalidModelDoesNotPoisonSubsequentSessionCreation() = contract().invalidModelDoesNotPoisonSubsequentSessionCreation()
    @Test fun genitalDetectionsCoverTheExpectedPixelsAfterNativeInference() = contract().genitalDetectionsCoverTheExpectedPixelsAfterNativeInference()

    /**
     * The two heavy-model contracts (a 1024x1024 MatMul) make ONNX Runtime pick its KleidiAI SME
     * kernels when the CPU reports SME. An Android emulator on an Apple Silicon host reports
     * `sme`/`sme2` in /proc/cpuinfo but cannot execute SME instructions: the first one (`rdsvl`)
     * raises SIGILL and kills the whole instrumentation process (2026-10-13, ORT 1.30.0, API 35
     * emulator). The same tests pass there with ORT's `mlas.disable_kleidiai`, so the crash is the
     * emulated CPU, not these contracts. Only that combination is skipped; physical devices (with
     * or without SME) and emulators that do not report SME still run them.
     */
    private fun assumeCpuCanRunOrtSmeKernels() = assumeFalse(
        "Emulator reports SME but cannot execute it; ONNX Runtime's SME kernels raise SIGILL",
        isEmulator() && cpuReportsSme()
    )

    private fun isEmulator(): Boolean =
        Build.HARDWARE in setOf("ranchu", "goldfish") || Build.PRODUCT.startsWith("sdk_gphone") ||
            Build.PRODUCT.startsWith("sdk_") || Build.MODEL.startsWith("Android SDK built for")

    /** True when the kernel lists any SME feature (`sme`, `sme2`, `smef64f64`, ...). */
    private fun cpuReportsSme(): Boolean = runCatching {
        File("/proc/cpuinfo").readLines()
            .filter { it.startsWith("Features") }
            .flatMap { it.substringAfter(':').trim().split(Regex("\\s+")) }
            .any { it.startsWith("sme") }
    }.getOrDefault(false)
}
