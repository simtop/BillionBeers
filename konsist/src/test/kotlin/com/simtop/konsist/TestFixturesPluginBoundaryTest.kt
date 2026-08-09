package com.simtop.konsist

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Backs ADR 0001 (docs/adr/0001-test-fixtures-via-sibling-modules.md): test fixtures live in
 * sibling `:module:fakes` / `:module:fixtures` modules, never Gradle's `java-test-fixtures` plugin.
 * That was decided on measured build-time cost, not taste - the plugin adds a variant to every
 * consumer and the ADR records what that did to build times here.
 *
 * The decision was drifting back in on its own: `beerdomain/api/build.gradle.kts` carried a "TODO:
 * try testFixtures instead" with commented-out `testFixturesImplementation` lines, which is how a
 * settled ADR quietly becomes a suggestion. Deleting the TODO fixes today; this rule fixes the next
 * time.
 *
 * Matching is on the plugin id and deliberately ignores comment lines - see [uncommentedText].
 */
class TestFixturesPluginBoundaryTest {

  private val forbiddenPluginId = "java-test-fixtures"

  @Test
  fun `no module applies the java-test-fixtures plugin`() {
    val scripts = buildScripts()

    assertTrue(scripts.isNotEmpty()) {
      "No build.gradle.kts found - the project layout changed and this rule would pass vacuously"
    }

    val violations = scripts.filter { it.uncommentedText().contains(forbiddenPluginId) }

    assertTrue(violations.isEmpty()) {
      "These build scripts apply '$forbiddenPluginId': " +
        "${violations.map { it.relativeTo(repoRoot()).path }} - ADR 0001 requires a sibling " +
        "fakes/fixtures module instead. Reopen the ADR if you want to change that; don't " +
        "reintroduce the plugin alongside it."
    }
  }
}
