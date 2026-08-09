package com.simtop.konsist

import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A feature module reaches persistence and the network only through the repository *interfaces* in
 * `:beerdomain:api`, whose implementations are bound in `:app`'s Metro graph. Declaring
 * `:beer_data`, `:beer_database` or `:beer_network` in a feature's build script skips that seam:
 * the feature gains Room entities, DTOs and Retrofit services as compile-time types, and the domain
 * boundary becomes advisory.
 *
 * This is the *unnamed* edge the existing rules leave open, and it is worth stating explicitly:
 * - `FeatureModuleBoundaryTest` reads imports, and only for the beerslist/beerdetail pair.
 * - `ViewModelBoundaryTest` catches a data-layer type only when a **ViewModel** imports it. A
 *   mapper, an extension or a composable in the same module would pass.
 * - `DevAppDependencyBoundaryTest` forbids the same three modules, but only for `app-dev-*`.
 *
 * So the dependency can be declared and used today without any rule firing. This closes that.
 *
 * Reads build scripts directly for the reason given in [buildScripts]: Konsist does not scan
 * `.kts`. It matches the full `project("...")` call form, since a bare `":beer_data"` would also
 * match prose, and it strips comment lines so an explanatory comment cannot fail a correct build
 * file.
 *
 * Scope is `feature/` — both the regular modules and the on-demand dynamic ones, which have the
 * same boundary. `:app` is deliberately exempt: assembling the graph is precisely its job.
 */
class FeatureDataLayerBoundaryTest {

  private val forbiddenDataLayerModules = listOf(":beer_data", ":beer_database", ":beer_network")

  @Test
  fun `feature modules do not depend on data-layer modules`() {
    val root = repoRoot()
    val featureBuildScripts =
      File(root, "feature")
        .listFiles { file -> file.isDirectory }
        .orEmpty()
        .map { File(it, "build.gradle.kts") }
        .filter { it.exists() }

    // Without this the rule passes silently if the directory is ever renamed - the failure mode
    // that let :konsist:test itself sit un-run for weeks (AGENTS.md §5).
    assertTrue(featureBuildScripts.isNotEmpty()) {
      "No feature/*/build.gradle.kts found under $root - the feature module layout changed and " +
        "this rule would pass vacuously"
    }

    featureBuildScripts.forEach { script ->
      val text = script.uncommentedText()
      val violations = forbiddenDataLayerModules.filter { module ->
        text.contains("project(\"$module\")")
      }
      assertTrue(violations.isEmpty()) {
        ":feature:${script.parentFile.name} declares data-layer module(s) $violations - a feature " +
          "depends on the repository interfaces in :beerdomain:api, and :app binds the " +
          "implementations"
      }
    }
  }
}
