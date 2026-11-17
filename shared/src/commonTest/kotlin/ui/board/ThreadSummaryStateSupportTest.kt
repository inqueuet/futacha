package com.valoser.futacha.shared.ui.board

import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

class ThreadSummaryStateSupportTest {
    @Test
    fun resolveInitialThreadSummaryUiStateReturnsLoadingWhenSummaryFeatureIsEnabled() {
        assertSame(
            ThreadSummaryUiState.Loading,
            resolveInitialThreadSummaryUiState(shouldShowThreadSummary = true)
        )
    }

    @Test
    fun resolveInitialThreadSummaryUiStateReturnsNullWhenSummaryFeatureIsDisabled() {
        assertNull(resolveInitialThreadSummaryUiState(shouldShowThreadSummary = false))
    }
}
