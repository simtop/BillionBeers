package com.simtop.billionbeers.buildlogic

import org.gradle.api.Project
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrLink

abstract class WasmLinkerBudget : BuildService<BuildServiceParameters.None>

fun Project.configureWasmLinkerBudget() {
  // Cold Compose links share the Kotlin daemon heap. Four concurrent links exhausted 2 GB;
  // each succeeds alone. Bound executable links while leaving other compilation parallel.
  val budget = gradle.sharedServices.registerIfAbsent("wasmLinkerBudget", WasmLinkerBudget::class.java) {
    maxParallelUsages.set(1)
  }
  subprojects {
    tasks.withType<KotlinJsIrLink>().configureEach {
      if (name.endsWith("KotlinWasmJs")) {
        usesService(budget)
      }
    }
  }
}
