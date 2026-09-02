package com.valoser.futacha.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FutachaNavigationBenchmark : NavigationBenchmark(FutachaProfile.FUTACHA)

@RunWith(AndroidJUnit4::class)
class ToshiakiCompatNavigationBenchmark : NavigationBenchmark(FutachaProfile.TOSHIAKI_COMPAT)

abstract class NavigationBenchmark(private val profile: FutachaProfile) {
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
            metrics = listOf(FrameTimingMetric()),
            compilationMode = compilationMode,
            iterations = BENCHMARK_ITERATIONS,
            startupMode = StartupMode.WARM,
            setupBlock = {
                startActivityAndWait()
            }
        ) {
            runCriticalNavigation(profile)
        }
    }
}
