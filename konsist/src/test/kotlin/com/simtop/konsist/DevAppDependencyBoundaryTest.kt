package com.simtop.konsist

import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A dev-app (app-dev-<feature>) exists to build one feature in seconds instead of minutes: it
 * binds the feature under development to :beerdomain:fakes instead of the real data layer, so the
 * heavy :beer_data / :beer_database / :beer_network graph (Room, Retrofit, OkHttp) never enters
 * its compilation. The moment a dev-app declares a dependency on a data-layer module, that
 * guarantee is gone and the dev-app is just a slower copy of :app.
 *
 * The invariant lives in build.gradle.kts, not in Kotlin source, and Konsist's project scope does
 * not surface build scripts (they are excluded, and .kts is not scanned at all), so this rule
 * reads the dev-app build files directly. It matches the full project("...") call form on purpose:
 * a bare ":beer_data" would also match the explanatory comment in the build file and fail correct
 * code.
 */
class DevAppDependencyBoundaryTest {

  private val forbiddenDataLayerModules = listOf(":beer_data", ":beer_database", ":beer_network")

  @Test
  fun `dev-apps do not depend on data-layer modules`() {
    val root = repoRoot()
    val devAppBuildScripts =
      root
        .listFiles { file -> file.isDirectory && file.name.startsWith("app-dev-") }
        .orEmpty()
        .map { File(it, "build.gradle.kts") }
        .filter { it.exists() }

    assertTrue(devAppBuildScripts.isNotEmpty()) {
      "No app-dev-*/build.gradle.kts found under $root - the dev-app layout changed and this rule " +
        "would pass vacuously"
    }

    devAppBuildScripts.forEach { script ->
      val text = script.readText()
      val violations =
        forbiddenDataLayerModules.filter { module -> text.contains("project(\"$module\")") }
      assertTrue(violations.isEmpty()) {
        "${script.parentFile.name} depends on data-layer module(s) $violations - dev-apps must use " +
          ":beerdomain:fakes, not the real data layer, to keep their build fast"
      }
    }
  }
}
