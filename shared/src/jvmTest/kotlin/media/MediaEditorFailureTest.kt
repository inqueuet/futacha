package com.valoser.futacha.shared.media

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.*

class MediaEditorFailureTest {
    @Test fun exceptionsKeepTheirMessageOrTheFallback() {
        assertEquals("壊れています", mediaEditorFailureMessage(IllegalStateException("壊れています"), "既定"))
        assertEquals("既定", mediaEditorFailureMessage(RuntimeException(), "既定"))
    }

    /** B-13: these Errors passed `catch (Exception)` and crashed the editor. */
    @Test fun allocationAndNativeLinkFailuresAreShownInsteadOfCrashing() {
        assertEquals(MEDIA_EDITOR_OUT_OF_MEMORY_MESSAGE, mediaEditorFailureMessage(OutOfMemoryError("Java heap space"), "既定"))
        assertEquals(MEDIA_EDITOR_NATIVE_LIBRARY_MESSAGE,
            mediaEditorFailureMessage(UnsatisfiedLinkError("dlopen failed: libfutacha_tracking_bridge.so"), "既定"))
        assertEquals(MEDIA_EDITOR_NATIVE_LIBRARY_MESSAGE, mediaEditorFailureMessage(NoClassDefFoundError("TrackingNative"), "既定"))
        assertEquals(MEDIA_EDITOR_NATIVE_LIBRARY_MESSAGE, mediaEditorFailureMessage(ExceptionInInitializerError("ort"), "既定"))
    }

    @Test fun cancellationAndOtherErrorsAreRethrown() {
        val cancelled = CancellationException("closed")
        assertSame(cancelled, assertFailsWith<CancellationException> { mediaEditorFailureMessage(cancelled, "既定") })
        val bug = AssertionError("bug")
        assertSame(bug, assertFailsWith<AssertionError> { mediaEditorFailureMessage(bug, "既定") })
        val overflow = StackOverflowError()
        assertSame(overflow, assertFailsWith<StackOverflowError> { mediaEditorFailureMessage(overflow, "既定") })
    }
}
