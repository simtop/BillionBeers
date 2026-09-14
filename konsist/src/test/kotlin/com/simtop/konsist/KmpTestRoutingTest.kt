package com.simtop.konsist

import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KmpTestRoutingTest {

  @Test
  fun `Makefile keeps KMP test tiers explicit and target specific`() {
    val makefile = File(repoRoot(), "Makefile").readText()

    listOf(
        "KMP_JVM_TEST_MODULES :=",
        "KMP_METADATA_MODULES :=",
        "KMP_ANDROID_HOST_TEST_MODULES :=",
        "KMP_BROWSER_TEST_MODULES :=",
        "$(module):jvmTest",
        "$(module):allMetadataJar",
        "$(module):testAndroidHostTest",
        "$(module):wasmJsBrowserTest",
      )
      .forEach { declaration ->
        assertTrue(
          makefile.contains(declaration),
          "Makefile lost KMP routing declaration: $declaration",
        )
      }

    assertTrue(
      makefile.contains("else ifneq ($(filter $(MODULE_TRIMMED),$(KMP_JVM_TEST_MODULES)),)"),
      "KMP modules must be matched before generic test routing",
    )
    assertTrue(
      makefile.contains("else ifneq ($(filter $(MODULE_TRIMMED),$(JVM_TEST_MODULES) :konsist),)"),
      "Existing JVM routing must remain a separate fallback",
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
