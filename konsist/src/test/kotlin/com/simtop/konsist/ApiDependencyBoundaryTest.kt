package com.simtop.konsist

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Project exports are checked by architecture-policy; library exports need review here. */
class ApiDependencyBoundaryTest {

  // Exact declarations, not whole-module exemptions. Adding a dependency must not silently expand
  // a consumer's compile classpath.
  private val reviewedExports =
    mapOf(
      "navigation/build.gradle.kts" to
        mapOf(
          "api(libs.androidx.navigation3.runtime)" to
            "EntryProviderScope and NavKey in the public contribution API"
        ),
      "navigation-contract/build.gradle.kts" to
        mapOf(
          "commonMainApi(libs.androidx.navigation3.runtime)" to "PortableRoute implements NavKey"
        ),
      "presentation_utils/build.gradle.kts" to
        mapOf(
          "api(libs.androidPlayCore)" to
            "SplitInstallManager and SplitInstallSessionState in public APIs"
        ),
      "snapshot-processor/build.gradle.kts" to processorExports,
      "catalog-processor/build.gradle.kts" to processorExports,
      "testing-utils/build.gradle.kts" to
        mapOf(
          "api(libs.junit.jupiter.api)" to "MainDispatcherExtension implements Jupiter callbacks",
          "api(libs.junit)" to "MainDispatcherExtension implements TestRule",
          "api(libs.coroutinesTest)" to "MainDispatcherExtension exposes TestDispatcher",
        ),
      "testing-utils-android/build.gradle.kts" to
        mapOf(
          "api(libs.androidx.ui.test.junit4)" to
            "Robot constructors and rules expose ComposeTestRule",
          "api(libs.androidx.annotation)" to "BaseTestRobot.string exposes @StringRes",
          "api(libs.junit)" to "FailureScreenshotRule exposes TestRule and TestWatcher",
        ),
    )

  @Test
  fun `library api dependencies require an exact reviewed export`() {
    val root = repoRoot()
    val actual =
      (buildScripts(root) +
          File(root, "build-logic/convention/src/main/kotlin")
            .listFiles { file -> file.name.endsWith(".gradle.kts") }
            .orEmpty())
        .associate { script ->
          script.relativeTo(root).invariantSeparatorsPath to
            libraryApiDeclarations(script.uncommentedText())
        }
        .filterValues { it.isNotEmpty() }
    val expected = reviewedExports.mapValues { (_, exports) -> exports.keys }

    assertEquals(
      expected,
      actual,
      "Use implementation (a catalog bundle can group dependencies). Add an exact export with a " +
        "public-contract reason only when consumers need the dependency through this module. " +
        "Remove stale exemptions when an export is narrowed. Project api edges also require architecture-policy.",
    )
    assertTrue(reviewedExports.values.all { exports -> exports.values.all(String::isNotBlank) })
  }

  @Test
  fun `scanner detects direct KMP quoted and add declarations including multiline arguments`() {
    assertEquals(
      setOf(
        "api(libs.first)",
        "commonMainApi(libs.second)",
        "api(platform(libs.bom))",
        "androidMainApi(libs.third)",
      ),
      libraryApiDeclarations(
        """
        dependencies {
          api(libs.first)
          commonMainApi(
            libs.second
          )
          "api"(platform(libs.bom))
          add("androidMainApi", libs.third)
          implementation(libs.private)
          api(project(":domain"))
          api(this.project(":other"))
        }
        """
          .trimIndent()
      ),
    )
  }

  private companion object {
    val processorExports =
      mapOf("api(libs.ksp.api)" to "Processor providers implement KSP interfaces")
  }
}

private fun libraryApiDeclarations(text: String): Set<String> {
  val direct = Regex("""\b(api|\w+Api)\s*\(|"(api|\w+Api)"\s*\(""")
  val added = Regex("""\badd\s*\(\s*"(api|\w+Api)"\s*,""")
  return (direct.findAll(text).map { match ->
      val configuration = match.groupValues[1].ifEmpty { match.groupValues[2] }
      configuration to callArguments(text, match.range.last + 1)
    } +
      added.findAll(text).map { match ->
        match.groupValues[1] to callArguments(text, match.range.last + 1)
      })
    .filterNot { (_, argument) -> Regex("""^(this\.)?project\(""").containsMatchIn(argument) }
    .map { (configuration, argument) -> "$configuration($argument)" }
    .toSet()
}

/** Reads nested platform/project calls; whitespace is irrelevant for the reviewed declarations. */
private fun callArguments(text: String, start: Int): String {
  var depth = 1
  var quoted = false
  var escaped = false
  for (index in start until text.length) {
    val char = text[index]
    if (escaped) {
      escaped = false
    } else if (char == '\\' && quoted) {
      escaped = true
    } else if (char == '"') {
      quoted = !quoted
    } else if (!quoted) {
      if (char == '(') depth++
      if (char == ')' && --depth == 0)
        return text.substring(start, index).replace(Regex("\\s+"), "")
    }
  }
  error("Unclosed api dependency declaration")
}
