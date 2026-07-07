package com.simtop.benchmark.macrobenchmark

import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// androidx.benchmark's measureRepeated() returns Unit in this version (1.4.1) - there is no
// in-process result to assert a threshold against here. Metrics only exist in the JSON file this
// writes to build/outputs/connected_android_test_additional_output/.../*-benchmarkData.json
// after the run. scripts/check-benchmark-budget.sh (run via `make benchmark-check`) parses that
// file and fails if a metric's median exceeds its configured budget
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startup() = benchmarkRule.measureRepeated(
        packageName = "com.simtop.billionbeers",
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD
    ) {
        pressHome()
        startActivityAndWait()
    }
}
