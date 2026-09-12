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

  @Test
  fun `app instrumentation defaults to isolated debug sources`() {
    val root = repoRoot()
    val script = File(root, "app/build.gradle.kts").uncommentedText()
    val debugManifest = File(root, "app/src/debugAndroidTest/AndroidManifest.xml").readText()

    assertTrue(debugManifest.contains("com.simtop.billionbeers.di.MockTestRunner")) {
      "The Debug instrumentation manifest must keep the mock runner"
    }
    assertTrue(
      script.contains("providers.gradleProperty(\"appTestBuildType\").orElse(\"debug\")")
    ) {
      "The app instrumentation selector must default to the mock/debug suite"
    }
    assertTrue(script.contains("testBuildType = appTestBuildType")) {
      "The selected app instrumentation suite must control the Android test build type"
    }
    assertTrue(script.contains("selectedBuildType in setOf(\"debug\", \"releaseSmoke\")")) {
      "Unsupported appTestBuildType values must fail during configuration"
    }
    assertTrue(script.contains("listOf(\"src/androidTest/java\", \"src/debugAndroidTest/java\")")) {
      "Debug instrumentation must include the common and debug-only source roots"
    }
    assertTrue(script.contains("listOf(\"src/releaseSmokeAndroidTest/java\")")) {
      "Smoke instrumentation must use only its dedicated source root"
    }
    assertTrue(
      script.contains("appTestSourceSet.manifest.srcFile") &&
        script.contains("app/src/debugAndroidTest/AndroidManifest.xml")
    ) {
      "Debug instrumentation must select the mock runner manifest explicitly"
    }

    listOf(
        "app/src/androidTest/java",
        "app/src/debugAndroidTest/java",
        "app/src/releaseSmokeAndroidTest/java",
      )
      .forEach { relativePath ->
        assertTrue(File(root, relativePath).isDirectory) {
          "App instrumentation source root $relativePath is missing"
        }
      }
  }

  @Test
  fun `make routes smoke tests in a separate selected-build-type invocation`() {
    val root = repoRoot()
    val makefile = File(root, "Makefile").readText()

    assertTrue(
      makefile.contains(":app:atdApi35ReleaseSmokeAndroidTest -PappTestBuildType=releaseSmoke")
    ) {
      "The app-owned release confidence smoke must select releaseSmoke explicitly"
    }
    assertTrue(
      makefile.contains(
        "VERIFICATION_METADATA_REFERENCE_DEBUG_DEVICE_TASKS := ciGroupDebugAndroidTest"
      )
    ) {
      "Verification metadata must keep the Debug aggregate in its own invocation"
    }
    assertTrue(makefile.contains("VERIFICATION_METADATA_REFERENCE_SMOKE_DEVICE_TASKS")) {
      "Verification metadata must keep smoke device tasks in their own invocation"
    }
    assertTrue(
      makefile.contains("verification-metadata-reference: ##") &&
        makefile.contains("-PappTestBuildType=releaseSmoke")
    ) {
      "Smoke metadata invocations must select releaseSmoke explicitly"
    }
    assertTrue(makefile.contains(":app:assembleReleaseSmokeAndroidTest")) {
      "The candidate metadata graph must assemble the app-owned smoke test APK"
    }
  }
}
