package com.valoser.futacha

import com.valoser.futacha.shared.compat.ExperienceProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BenchmarkFixtureProfileTest {
    @Test
    fun missingProfileSelectsFutacha() {
        assertEquals(ExperienceProfile.FUTACHA, parseBenchmarkFixtureProfile(null))
    }

    @Test
    fun everyKnownProfileNameIsAccepted() {
        ExperienceProfile.entries.forEach { profile ->
            assertEquals(profile, parseBenchmarkFixtureProfile(profile.name))
        }
    }

    @Test
    fun unknownProfileIsRejectedWithoutThrowing() {
        assertNull(parseBenchmarkFixtureProfile("NOT_A_PROFILE"))
        assertNull(parseBenchmarkFixtureProfile(""))
        assertNull(parseBenchmarkFixtureProfile("futacha"))
    }
}
