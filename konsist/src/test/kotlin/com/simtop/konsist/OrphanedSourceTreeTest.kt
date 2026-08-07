package com.simtop.konsist

import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A `src/` directory with no build script beside it is a ghost: nothing compiles it, nothing tests
 * it, and nothing ships it - but `grep` still finds it, and a reader has no way to tell it apart
 * from live code. AGENTS.md §2 already records the same failure mode for the stale `bin/` output
 * trees, where a search hit pointed at a file that had been deleted from the project.
 *
 * `settings.gradle.kts` discovers modules by looking for a build script, so a directory that has
 * sources but no script is simply absent from the build. Gradle still *shows* a project for it when
 * it is a parent of an included module - `:beerdomain`, `:feature` and `:benchmark` all appear in
 * `./gradlew projects` as synthesized containers - which makes the ghost look load-bearing in a
 * project listing while being invisible to every task.
 *
 * Caught on adoption: `beerdomain/src/main/AndroidManifest.xml`, an empty manifest orphaned when
 * `:beerdomain` was split into `:beerdomain:api` and `:beerdomain:fakes` and the parent became a
 * container. Deleted in the same change.
 *
 * Like [DevAppDependencyBoundaryTest] and [InstrumentedTestOptInBoundaryTest], this reads the
 * filesystem rather than Kotlin source: the invariant is about a directory layout, which Konsist's
 * project scope cannot see.
 */
class OrphanedSourceTreeTest {

  /**
   * Directories that are output, tooling, or version-control state rather than project source.
   * Mirrors [buildScripts]'s exclusions, plus `gradle-user-home`, which gradle-profiler populates
   * with a full Gradle distribution during `make build-budget` (ADR 0011).
   */
  private val excludedDirNames =
    setOf("build", "bin", ".git", ".gradle", ".kotlin", ".idea", "gradle-user-home")

  @Test
  fun `every source tree belongs to a module with a build script`() {
    val root = repoRoot()

    val sourceTrees =
      root
        .walkTopDown()
        .onEnter { it.name !in excludedDirNames }
        .filter { it.isDirectory && it.name == "src" }
        .map { it.parentFile }
        .toList()

    assertTrue(sourceTrees.isNotEmpty()) {
      "No src/ directory found under $root - the layout changed and this rule would pass vacuously"
    }

    val orphans =
      sourceTrees
        .filterNot { File(it, "build.gradle.kts").exists() || File(it, "build.gradle").exists() }
        .map { it.relativeTo(root).invariantSeparatorsPath }
        .sorted()

    assertTrue(orphans.isEmpty()) {
      "Source tree(s) $orphans have no build script beside them, so no Gradle task compiles, " +
        "tests or ships them - but a grep still finds them. Delete the directory, or add a " +
        "build.gradle.kts so it becomes a real module (settings.gradle.kts discovers it " +
        "automatically)."
    }
  }
}
