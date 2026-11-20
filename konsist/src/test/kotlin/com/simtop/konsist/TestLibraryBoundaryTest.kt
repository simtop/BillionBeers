package com.simtop.konsist

import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A test-only library declared on `implementation` or `api` ships in the release APK.
 *
 * Caught on adoption: `core-common/build.gradle.kts` declared `implementation(libs.coroutinesTest)`
 * next to `implementation(libs.kotlinx.coroutines.core)` - the comment on the line ("For
 * Dispatchers? No, coroutines-core") shows the first was a mistake the author noticed, corrected on
 * the following line, and never deleted. Because every module depends on `:core-common`,
 * `kotlinx-coroutines-test` reached `:app`'s `releaseRuntimeClasspath` and its
 * `TestMainDispatcherFactory` entry was present in the shipped APK's merged `META-INF/services`
 * registry.
 *
 * Nothing else catches this. `dependency-guard` locks the resolved release classpath, but it was
 * baselined *with* the offending dependency already on it, so the guard faithfully protected the
 * defect. `detectUnusedDependencies` does not run on the pure-JVM modules. The gap is the
 * *direction*: a lock file stops a graph from changing, and says nothing about whether the graph
 * was right when it was locked.
 *
 * Reads the version catalog and the build scripts as text, the same approach as
 * [DevAppDependencyBoundaryTest] - Konsist does not scan `.kts` or `.toml`.
 */
class TestLibraryBoundaryTest {

  /** Configurations whose contents reach a shipped artifact. */
  private val productionConfigurations =
    listOf("implementation", "api", "compileOnly", "runtimeOnly")

  /**
   * Modules allowed to put test libraries on a production configuration.
   *
   * `testing-utils` / `testing-utils-android` are the sibling fixture modules ADR 0001 chose
   * instead of Gradle's `java-test-fixtures`; exposing JUnit and friends via `api` is their entire
   * job, and consumers only ever take them on `testImplementation` / `androidTestImplementation`,
   * so nothing they declare can reach a release classpath.
   *
   * `build-logic` is a separate composite build of Gradle plugins - its `implementation`
   * dependencies are compile-time inputs to the build itself, not to the app.
   *
   * `:benchmark:*` modules apply `com.android.test`, which produces a standalone test APK and no
   * shipped artifact. Their `src/main` *is* the test code, so there is no `testImplementation` to
   * move these to - `implementation` is the only correct configuration. The release-smoke test
   * module has the same standalone-test-APK shape, so it is exempt for the same reason.
   */
  private val exemptPathPrefixes =
    listOf(
      "testing-utils/",
      "testing-utils-android/",
      "build-logic/",
      "benchmark/",
      "app-release-smoke/",
    )

  /**
   * Coordinates that are test-only but do not say so in their name. Everything else is caught by
   * the `"test"` substring check below. Listed rather than resolved, for the same reason
   * [InstrumentedTestOptInBoundaryTest] lists plugin ids: a new one must be added here, and the
   * cost of forgetting is a miss on that library alone, not a silently disabled rule.
   */
  private val testOnlyCoordinateMarkers =
    listOf(
      "junit",
      "io.mockk",
      "io.strikt",
      "org.amshove.kluent",
      "app.cash.turbine",
      "app.cash.paparazzi",
      "org.robolectric",
      "espresso",
      "uiautomator",
    )

  private fun isTestOnly(coordinate: String): Boolean {
    val c = coordinate.lowercase()
    return "test" in c || testOnlyCoordinateMarkers.any { it in c }
  }

  /**
   * Version-catalog aliases whose coordinate is test-only, returned in the dotted form a build
   * script uses (`coroutinesTest` -> `libs.coroutinesTest`, `androidx-ui-test-junit4` ->
   * `libs.androidx.ui.test.junit4`).
   *
   * Handles all three shapes the catalog uses: `alias = "group:name:version"`, `alias = { module =
   * "group:name", ... }` and `alias = { group = "...", name = "...", ... }`.
   */
  private fun testOnlyAliases(root: File): List<String> {
    val toml = File(root, "gradle/libs.versions.toml").readLines()
    val librariesSection =
      toml.dropWhile { it.trim() != "[libraries]" }.drop(1).takeWhile { !it.trim().startsWith("[") }

    return librariesSection.mapNotNull { rawLine ->
      val line = rawLine.substringBefore('#').trim()
      if (line.isEmpty() || '=' !in line) return@mapNotNull null
      val alias = line.substringBefore('=').trim()
      val value = line.substringAfter('=').trim()

      val coordinate =
        when {
          value.startsWith("\"") -> value.trim('"')
          "module" in value -> Regex("""module\s*=\s*"([^"]+)"""").find(value)?.groupValues?.get(1)
          else -> {
            val group = Regex("""group\s*=\s*"([^"]+)"""").find(value)?.groupValues?.get(1)
            val name = Regex("""name\s*=\s*"([^"]+)"""").find(value)?.groupValues?.get(1)
            if (group != null && name != null) "$group:$name" else null
          }
        } ?: return@mapNotNull null

      if (isTestOnly(coordinate)) alias.replace('-', '.') else null
    }
  }

  /**
   * Every `[libraries]` alias, in the dotted form a build script uses, paired with its coordinate.
   * Needed by [testOnlyBundleAliases], which has to resolve a bundle's members back to coordinates.
   */
  private fun allAliasCoordinates(root: File): Map<String, String> {
    val toml = File(root, "gradle/libs.versions.toml").readLines()
    val librariesSection =
      toml.dropWhile { it.trim() != "[libraries]" }.drop(1).takeWhile { !it.trim().startsWith("[") }

    return librariesSection
      .mapNotNull { rawLine ->
        val line = rawLine.substringBefore('#').trim()
        if (line.isEmpty() || '=' !in line) return@mapNotNull null
        val alias = line.substringBefore('=').trim()
        val value = line.substringAfter('=').trim()

        val coordinate =
          when {
            value.startsWith("\"") -> value.trim('"')
            "module" in value ->
              Regex("""module\s*=\s*"([^"]+)"""").find(value)?.groupValues?.get(1)
            else -> {
              val group = Regex("""group\s*=\s*"([^"]+)"""").find(value)?.groupValues?.get(1)
              val name = Regex("""name\s*=\s*"([^"]+)"""").find(value)?.groupValues?.get(1)
              if (group != null && name != null) "$group:$name" else null
            }
          } ?: return@mapNotNull null

        alias to coordinate
      }
      .toMap()
  }

  /**
   * Bundle aliases holding at least one test-only library, in the dotted form a build script uses
   * (`unitTest` -> `libs.bundles.unitTest`).
   *
   * A bundle is a single name for a list of libraries, so without this the catalog offers a way
   * straight through this rule: `implementation(libs.bundles.unitTest)` ships five test libraries
   * while matching none of the `libs.<alias>` patterns the rule looks for. **One** test-only member
   * condemns the bundle - that member ships just as surely as it would on its own line.
   *
   * The `[bundles]` section is TOML arrays, which may be written on one line or across several, so
   * the section text is joined before the entries are split out.
   */
  private fun testOnlyBundleAliases(root: File): List<String> {
    val toml = File(root, "gradle/libs.versions.toml").readLines()
    val bundlesSection =
      toml
        .dropWhile { it.trim() != "[bundles]" }
        .drop(1)
        .takeWhile { !it.trim().startsWith("[") }
        .joinToString("\n") { it.substringBefore('#') }

    if (bundlesSection.isBlank()) return emptyList()

    val aliasCoordinates = allAliasCoordinates(root)

    return Regex("""([\w.-]+)\s*=\s*\[([^]]*)]""", RegexOption.DOT_MATCHES_ALL)
      .findAll(bundlesSection)
      .mapNotNull { match ->
        val bundleAlias = match.groupValues[1].trim()
        val members =
          match.groupValues[2].split(',').map { it.trim().trim('"') }.filter { it.isNotEmpty() }

        val holdsTestLibrary = members.any { member ->
          val coordinate = aliasCoordinates[member]
          coordinate != null && isTestOnly(coordinate)
        }

        if (holdsTestLibrary) bundleAlias.replace('-', '.') else null
      }
      .toList()
  }

  @Test
  fun `test-only libraries never reach a production configuration`() {
    val root = repoRoot()
    val aliases = testOnlyAliases(root)

    assertTrue(aliases.isNotEmpty()) {
      "No test-only aliases found in gradle/libs.versions.toml - the catalog format changed and " +
        "this rule would pass vacuously"
    }

    // `libs.<alias>` for a bare library, `libs.bundles.<alias>` for a bundle. Both reach a release
    // classpath the same way, so both are checked against the same production configurations.
    val referencePaths =
      aliases.map { "libs.$it" } + testOnlyBundleAliases(root).map { "libs.bundles.$it" }

    val violations = mutableListOf<String>()

    buildScripts(root)
      .filterNot { script ->
        val path = script.parentFile.relativeTo(root).invariantSeparatorsPath + "/"
        exemptPathPrefixes.any { path.startsWith(it) }
      }
      .forEach { script ->
        val relativePath = script.relativeTo(root).invariantSeparatorsPath
        script.uncommentedText().lines().forEachIndexed { index, line ->
          val declaration = line.trim()
          productionConfigurations.forEach { configuration ->
            referencePaths.forEach { reference ->
              if (
                declaration.startsWith("$configuration($reference)") ||
                  declaration.startsWith("\"$configuration\"($reference)")
              ) {
                violations += "$relativePath:${index + 1}  $configuration($reference)"
              }
            }
          }
        }
      }

    assertTrue(violations.isEmpty()) {
      "Test-only libraries on a production configuration - these ship in the release APK:\n" +
        violations.joinToString("\n") { "  $it" } +
        "\nMove them to testImplementation / androidTestImplementation."
    }
  }
}
