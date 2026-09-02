package com.valoser.futacha.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FutachaColdStartupBenchmark : StartupBenchmark(FutachaProfile.FUTACHA, StartupMode.COLD)

@RunWith(AndroidJUnit4::class)
class FutachaWarmStartupBenchmark : StartupBenchmark(FutachaProfile.FUTACHA, StartupMode.WARM)

@RunWith(AndroidJUnit4::class)
class ToshiakiCompatColdStartupBenchmark :
    StartupBenchmark(FutachaProfile.TOSHIAKI_COMPAT, StartupMode.COLD)

@RunWith(AndroidJUnit4::class)
class ToshiakiCompatWarmStartupBenchmark :
    StartupBenchmark(FutachaProfile.TOSHIAKI_COMPAT, StartupMode.WARM)

abstract class StartupBenchmark(
    private val profile: FutachaProfile,
    private val startupMode: StartupMode
) {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Before
    fun selectProfile() {
        ensureProfile(profile)
    }

    @Test
    fun compilationNone() = benchmark(CompilationMode.None())

    @Test
    fun compilationBaselineProfile() = benchmark(
        CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require)
    )

    @Test
    fun compilationFull() = benchmark(CompilationMode.Full())

    private fun benchmark(compilationMode: CompilationMode) {
        rule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
            compilationMode = compilationMode,
            iterations = BENCHMARK_ITERATIONS,
            startupMode = startupMode
        ) {
            startActivityAndWait()
        }
    }
}
