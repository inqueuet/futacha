package com.valoser.futacha.shared.media.analysis

import kotlin.test.Test

class CpuInferenceRuntimeTest {
    @Test fun nativeContoursCoverOnlyTheBoxInteriorAndRequireReview() = CpuInferenceContract().nativeContoursCoverOnlyTheBoxInteriorAndRequireReview()
    @Test fun nativeExecutionReusesCallerBuffersAndSurvivesInvalidRequests() = CpuInferenceContract().nativeExecutionReusesCallerBuffersAndSurvivesInvalidRequests()
    @Test fun cancellationInterruptsNativeExecutionAndSameSessionRunsAgain() = CpuInferenceContract().cancellationInterruptsNativeExecutionAndSameSessionRunsAgain()
    @Test fun closingSessionAndTensorsDuringNativeExecutionDefersTheirRelease() = CpuInferenceContract().closingSessionAndTensorsDuringNativeExecutionDefersTheirRelease()
    @Test fun invalidModelDoesNotPoisonSubsequentSessionCreation() = CpuInferenceContract().invalidModelDoesNotPoisonSubsequentSessionCreation()
    @Test fun genitalDetectionsCoverTheExpectedPixelsAfterNativeInference() = CpuInferenceContract().genitalDetectionsCoverTheExpectedPixelsAfterNativeInference()
}
