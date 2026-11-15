package com.valoser.futacha.shared.mix

import com.valoser.futacha.shared.compat.MixAvailability
import kotlin.test.Test
import kotlin.test.assertFalse

class MixAvailabilityTest {
    @Test
    fun offByDefaultHidesAllEntries() {
        assertFalse(MixAvailability.enabled)
    }
}
