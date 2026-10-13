package com.simtop.konsist

import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KmpTestRoutingTest {

  @Test
  fun `Makefile delegates module test routing to Gradle`() {
    val makefile = File(repoRoot(), "Makefile").readText()
    val gradleConvention =
      File(
          repoRoot(),
          "build-logic/convention/src/main/kotlin/billionbeers.kmp.verification.gradle.kts",
        )
        .readText()

    assertTrue(
      listOf(
          "KMP_JVM_TEST_MODULES",
          "KMP_METADATA_MODULES",
          "KMP_ANDROID_HOST_TEST_MODULES",
          "KMP_BROWSER_TEST_MODULES",
        )
        .none(makefile::contains),
      "Makefile must not maintain KMP module inventories",
    )
    assertTrue(
      makefile.contains("-PtestModule=$(MODULE_TRIMMED) testModule"),
      "Selected-module test routing must be delegated to Gradle",
    )
    assertTrue(
      gradleConvention.contains("providers.gradleProperty(\"testModule\")"),
      "Gradle must read the selected module for scoped test routing",
    )
    assertTrue(
      makefile.contains("kmpTest"),
      "The full test target must use the Gradle KMP aggregate",
    )
  }

  @Test
  fun `static analysis and formatting name KMP source roots`() {
    val root = repoRoot()
    val detekt =
      File(root, "build-logic/convention/src/main/kotlin/billionbeers.detekt.gradle.kts").readText()
    val spotless =
      File(root, "build-logic/convention/src/main/kotlin/billionbeers.spotless.gradle.kts")
        .readText()

    listOf(
        "src/commonMain/**/*.kt",
        "src/commonTest/**/*.kt",
        "src/jvmMain/**/*.kt",
        "src/jvmTest/**/*.kt",
        "src/androidHostTest/**/*.kt",
        "src/wasmJsTest/**/*.kt",
        "src/ios*Test/**/*.kt",
      )
      .forEach { rootPattern ->
        assertTrue(detekt.contains(rootPattern), "Detekt lost KMP source root: $rootPattern")
      }
    assertTrue(
      spotless.contains("target(\"**/*.kt\")"),
      "Spotless must continue formatting all Kotlin roots",
    )
  }
}
