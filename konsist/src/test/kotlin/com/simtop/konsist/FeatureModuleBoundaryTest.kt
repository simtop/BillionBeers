package com.simtop.konsist

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Feature modules are siblings, never dependents of each other - each communicates with the rest of
 * the app only through the domain layer (repositories, use cases, models) and navigation. A feature
 * importing another feature directly is exactly the kind of coupling that makes dynamic delivery
 * and independent feature ownership impossible to scale.
 *
 * The list is discovered from build scripts directly below the `feature` directory, rather than
 * maintained by hand. This is important: a boundary rule that forgets a newly added feature is
 * worse than no rule because it reports a green build while enforcing a smaller architecture than
 * the documentation promises.
 */
class FeatureModuleBoundaryTest {

  @Test
  fun `feature modules are isolated from every other feature`() {
    val featureModules = featureModules()
    assertTrue(featureModules.size >= 2) {
      "Fewer than two feature modules were discovered below ${repoRoot()}/feature - this rule " +
        "would have no sibling boundary to verify"
    }

    val featurePackages = featureModules.map { it.namespace }.toSet()
    val files = Konsist.scopeFromProject().files

    featureModules.forEach { feature ->
      val otherPackages = featurePackages - feature.namespace
      files
        .filter { it.packagee?.name?.startsWith(feature.namespace) == true }
        .assertFalse { file ->
          file.hasImport { import ->
            otherPackages.any { otherPackage -> import.name.startsWith(otherPackage) }
          }
        }

      val directDependencies =
        feature.script
          .uncommentedText()
          .lineSequence()
          .filter { it.contains("project(\":feature:") }
          .toList()
      assertTrue(directDependencies.isEmpty()) {
        "${feature.script.relativeTo(repoRoot())} declares a direct feature dependency: " +
          directDependencies.joinToString(" | ") +
          ". Features communicate through :navigation and :beerdomain:api instead."
      }
    }
  }

  @Test
  fun `every discovered feature has a namespace`() {
    featureModules().forEach { feature ->
      assertTrue(feature.namespace.isNotBlank()) {
        "${feature.script.relativeTo(repoRoot())} does not declare an Android namespace; feature " +
          "discovery cannot determine which source package belongs to it"
      }
    }
  }

  private data class FeatureModule(val script: File, val namespace: String)

  private fun featureModules(): List<FeatureModule> {
    val root = repoRoot()
    val featureRoot = File(root, "feature")
    val scripts =
      featureRoot
        .listFiles { file -> file.isDirectory }
        .orEmpty()
        .map { File(it, "build.gradle.kts") }
        .filter { it.isFile }
        .sortedBy { it.path }

    assertTrue(scripts.isNotEmpty()) {
      "No feature module build scripts found under $root/feature - the feature layout changed and this " +
        "rule would pass vacuously"
    }

    return scripts.map { script ->
      val namespace =
        Regex("namespace\\s*=\\s*[\"']([^\"']+)[\"']")
          .find(script.uncommentedText())
          ?.groupValues
          ?.get(1)
          .orEmpty()
      FeatureModule(script, namespace)
    }
  }

  /*
   * Kept as focused tests in addition to the discovered all-pairs rule. They produce a shorter,
   * feature-specific failure message when the two historically important modules are changed.
   */
  @Test
  fun `feature-beerslist does not depend on feature-beerdetail`() {
    Konsist.scopeFromProject()
      .files
      .filter { it.packagee?.name?.startsWith("com.simtop.feature.beerslist") == true }
      .assertFalse { file ->
        file.hasImport { import -> import.name.startsWith("com.simtop.feature.beerdetail") }
      }
  }

  @Test
  fun `feature-beerdetail does not depend on feature-beerslist`() {
    Konsist.scopeFromProject()
      .files
      .filter { it.packagee?.name?.startsWith("com.simtop.feature.beerdetail") == true }
      .assertFalse { file ->
        file.hasImport { import -> import.name.startsWith("com.simtop.feature.beerslist") }
      }
  }
}
