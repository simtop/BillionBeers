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
        "$(MODULE_TRIMMED):jvmTest",
        "$(MODULE_TRIMMED):allMetadataJar",
        "$(MODULE_TRIMMED):testAndroidHostTest",
        "$(MODULE_TRIMMED):wasmJsBrowserTest",
        "kmpJvmTest",
        "kmpMetadata",
        "kmpAndroidHostTest",
        "kmpBrowserTest",
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
    assertTrue(
      makefile.contains("kmpTest"),
      "The full test target must use the Gradle KMP aggregate",
    )
  }

  @Test
  fun `every test source is routed to its Makefile test tier`() {
    val root = repoRoot()
    val makefile = File(root, "Makefile").readText()
    val moduleBuildFiles =
      root
        .walkTopDown()
        .onEnter { directory ->
          !directory.name.startsWith(".") &&
            directory.name !in setOf("gradle", "build", "bin", "out")
        }
        .filter { it.name == "build.gradle.kts" }
        .associateBy { it.parentFile }

    listOf(
        "commonTest" to "KMP_JVM_TEST_MODULES",
        "jvmTest" to "KMP_JVM_TEST_MODULES",
        "androidHostTest" to "KMP_ANDROID_HOST_TEST_MODULES",
        "wasmJsTest" to "KMP_BROWSER_TEST_MODULES",
      )
      .forEach { (sourceSet, variable) ->
        val configuredModules = makeVariableModules(makefile, variable)
        val missingModules =
          moduleBuildFiles.keys
            .filter { module -> hasTestSource(module, sourceSet) }
            .map { module -> moduleName(module, root) }
            .toSet() - configuredModules
        assertTrue(
          missingModules.isEmpty(),
          "$sourceSet test sources are not routed by $variable: ${missingModules.sorted()}",
        )
      }

    val configuredJvmModules = makeVariableModules(makefile, "JVM_TEST_MODULES")
    val missingJvmModules =
      moduleBuildFiles
        .filter { (module, buildFile) ->
          val buildText = buildFile.readText()
          moduleName(module, root) != ":konsist" &&
            ("billionbeers.jvm.library" in buildText || "kotlin(\"jvm\")" in buildText) &&
            hasTestSource(module, "test")
        }
        .keys
        .map { module -> moduleName(module, root) }
        .toSet() - configuredJvmModules
    assertTrue(
      missingJvmModules.isEmpty(),
      "Pure-JVM test sources are not routed by JVM_TEST_MODULES: ${missingJvmModules.sorted()}",
    )
  }

  private fun hasTestSource(module: File, sourceSet: String): Boolean =
    File(module, "src/$sourceSet").walkTopDown().any {
      it.isFile && it.extension in setOf("kt", "java")
    }

  private fun moduleName(module: File, root: File): String =
    ":" + module.relativeTo(root).invariantSeparatorsPath.replace("/", ":")

  private fun makeVariableModules(makefile: String, variable: String): Set<String> {
    val lines = makefile.lines()
    val declarationIndex = lines.indexOfFirst { it.startsWith("$variable :=") }
    if (declarationIndex < 0) return emptySet()

    val values = mutableListOf<String>()
    var index = declarationIndex
    var continues: Boolean
    do {
      val line = lines[index].trim()
      val value = if (index == declarationIndex) line.substringAfter(":=") else line
      continues = value.trimEnd().endsWith("\\")
      values += value.removeSuffix("\\")
      index++
    } while (continues && index < lines.size)
    return values.joinToString(" ").split(Regex("\\s+")).filter { it.startsWith(":") }.toSet()
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
