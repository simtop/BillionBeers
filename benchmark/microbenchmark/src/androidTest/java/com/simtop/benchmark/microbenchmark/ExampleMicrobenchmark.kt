package com.simtop.benchmark.microbenchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExampleMicrobenchmark {
    @get:Rule
    val benchmarkRule = BenchmarkRule()

    @Test
    fun benchmarkExample() {
        benchmarkRule.measureRepeated {
            // Add the logic you want to measure here
            val list = List(1000) { it }
            list.sorted()
        }
    }
}
