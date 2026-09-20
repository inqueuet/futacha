@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
package com.valoser.futacha

import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.shared.media.analysis.CpuInferenceContract
import okio.Path.Companion.toPath
import org.junit.Test

class CpuInferenceInstrumentedTest {
    @Test fun nativeContoursCoverOnlyTheBoxInteriorAndRequireReview() = contract().nativeContoursCoverOnlyTheBoxInteriorAndRequireReview()
    private fun contract() = CpuInferenceContract(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir.absolutePath.toPath())
    @Test fun nativeExecutionReusesCallerBuffersAndSurvivesInvalidRequests() = contract().nativeExecutionReusesCallerBuffersAndSurvivesInvalidRequests()
    @Test fun cancellationInterruptsNativeExecutionAndSameSessionRunsAgain() = contract().cancellationInterruptsNativeExecutionAndSameSessionRunsAgain()
    @Test fun closingSessionAndTensorsDuringNativeExecutionDefersTheirRelease() = contract().closingSessionAndTensorsDuringNativeExecutionDefersTheirRelease()
    @Test fun invalidModelDoesNotPoisonSubsequentSessionCreation() = contract().invalidModelDoesNotPoisonSubsequentSessionCreation()
    @Test fun genitalDetectionsCoverTheExpectedPixelsAfterNativeInference() = contract().genitalDetectionsCoverTheExpectedPixelsAfterNativeInference()
}
