package com.valoser.futacha.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun futachaStartup() {
        ensureProfile(FutachaProfile.FUTACHA)
        rule.collect(
            packageName = TARGET_PACKAGE,
            includeInStartupProfile = true,
            maxIterations = 10,
            stableIterations = 2
        ) {
            startActivityAndWait()
        }
    }

    @Test
    fun toshiakiCompatStartup() {
        ensureProfile(FutachaProfile.TOSHIAKI_COMPAT)
        rule.collect(
            packageName = TARGET_PACKAGE,
            includeInStartupProfile = true,
            maxIterations = 10,
            stableIterations = 2
        ) {
            startActivityAndWait()
        }
    }

    @Test
    fun futaberStartup() = collectStartup(FutachaProfile.FUTABER)

    @Test
    fun ftutterStartup() = collectStartup(FutachaProfile.FTUTTER)

    private fun collectStartup(profile: FutachaProfile) {
        ensureProfile(profile)
        rule.collect(
            packageName = TARGET_PACKAGE,
            includeInStartupProfile = true,
            maxIterations = 10,
            stableIterations = 2
        ) {
            startActivityAndWait()
        }
    }

    @Test
    fun futachaCriticalNavigation() {
        ensureProfile(FutachaProfile.FUTACHA)
        rule.collect(
            packageName = TARGET_PACKAGE,
            maxIterations = 10,
            stableIterations = 2
        ) {
            startActivityAndWait()
            runCriticalNavigation(FutachaProfile.FUTACHA)
        }
    }

    @Test
    fun toshiakiCompatCriticalNavigation() {
        ensureProfile(FutachaProfile.TOSHIAKI_COMPAT)
        rule.collect(
            packageName = TARGET_PACKAGE,
            maxIterations = 10,
            stableIterations = 2
        ) {
            startActivityAndWait()
            runCriticalNavigation(FutachaProfile.TOSHIAKI_COMPAT)
        }
    }

    @Test
    fun futaberCriticalNavigation() = collectNavigation(FutachaProfile.FUTABER)

    @Test
    fun ftutterCriticalNavigation() = collectNavigation(FutachaProfile.FTUTTER)

    private fun collectNavigation(profile: FutachaProfile) {
        ensureProfile(profile)
        rule.collect(
            packageName = TARGET_PACKAGE,
            maxIterations = 10,
            stableIterations = 2
        ) {
            startActivityAndWait()
            runCriticalNavigation(profile)
        }
    }
}
