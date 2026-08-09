package com.simtop.konsist

import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Instrumented tests are opt-in per module: without `billionbeers.android.managed.device` a module
 * has no `atdApi35DebugAndroidTest` task and is not part of `ciGroupDebugAndroidTest`, so its
 * `androidTest` sources compile and are never executed. That is the `:konsist:test` failure mode
 * (AGENTS.md §5) - tests that exist, look like coverage in review, and silently assert nothing for
 * months.
 *
 * The invariant lives in build.gradle.kts, not in Kotlin source, and Konsist's project scope does
 * not surface build scripts, so this rule reads them directly - the same approach as
 * [DevAppDependencyBoundaryTest].
 *
 * `:benchmark:*` is exempt, and deliberately so: benchmark modules declare their own
 * `AndroidBenchmarkRunner`, build against `testBuildType = "release"`, and suppress the EMULATOR
 * error class because a measurement taken on a managed virtual device is meaningless. They run
 * through `make benchmark-check` on real hardware, not the ATD lane.
 */
class InstrumentedTestOptInBoundaryTest {

  /**
   * Plugin ids that put a module on the managed device: the device plugin itself, and the wrappers
   * that apply it. Listed rather than resolved, because this rule reads build scripts as text and
   * cannot follow a plugin into build-logic. A new wrapper must be added here - the cost of
   * forgetting is a loud failure on the next module that uses it, not a silent gap.
   */
  private val optInPluginIds =
    listOf("billionbeers.android.managed.device", "billionbeers.android.feature.uitest")

  // A positive control on the list above: if one of those ids drifts (typo, or the plugin gets
  // renamed/moved), this fails loudly instead of the rule below silently matching nothing.
  @Test
  fun `opt-in plugin id list names real convention plugins`() {
    assertConventionPluginsExist(optInPluginIds)
  }

  @Test
  fun `modules with instrumented tests opt into the managed device`() {
    val root = repoRoot()

    val modulesWithInstrumentedTests =
      buildScripts(root)
        .filter { File(it.parentFile, "src/androidTest").isDirectory }
        .filterNot {
          it.parentFile.relativeTo(root).invariantSeparatorsPath.startsWith("benchmark/")
        }

    assertTrue(modulesWithInstrumentedTests.isNotEmpty()) {
      "No module with a src/androidTest directory found under $root - the layout changed and this " +
        "rule would pass vacuously"
    }

    val violations =
      modulesWithInstrumentedTests
        .filterNot { script -> optInPluginIds.any { script.uncommentedText().contains(it) } }
        .map { it.parentFile.relativeTo(root).invariantSeparatorsPath }

    assertTrue(violations.isEmpty()) {
      "Module(s) $violations have src/androidTest but apply none of $optInPluginIds - their " +
        "instrumented tests would never run, locally or in CI. Apply " +
        "'billionbeers.android.feature.uitest' (feature modules) or " +
        "'billionbeers.android.managed.device' directly."
    }
  }
}
