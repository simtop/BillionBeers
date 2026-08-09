package com.simtop.konsist

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Dynamic-feature modules (feature/beerdetail, feature/beerbrowse) ship as on-demand split APKs. A
 * user-facing string declared in the split's *own* package compiles fine but crashes at runtime
 * under an instrumented test, because a split's resource table is not merged into the resolving
 * context the way an installed base module's is - this has already bitten twice. The invariant:
 * every string a dynamic feature renders lives in :presentation_utils (a base module) and is
 * referenced through com.simtop.presentation_utils.R, never the feature's own R.
 *
 * The crash path is a *reference* to a split-owned resource, so that is what this rule catches: a
 * dynamic-feature file importing its own module R. It does not (and cannot, being Kotlin-only)
 * police a stray strings.xml that nothing references - a dead resource is harmless; a reference to
 * one is the failure.
 */
class DynamicFeatureResourceBoundaryTest {

  private val dynamicFeaturePackages =
    listOf("com.simtop.feature.beerdetail", "com.simtop.feature.beerbrowse")

  @Test
  fun `dynamic-feature code references resources through presentation_utils, not its own R`() {
    dynamicFeaturePackages.forEach { featurePackage ->
      val files =
        Konsist.scopeFromProject().files.filter {
          it.packagee?.name?.startsWith(featurePackage) == true
        }

      assertTrue(files.isNotEmpty()) {
        "No files scoped for $featurePackage - the package filter no longer matches this module"
      }

      files.assertFalse { file ->
        file.hasImport { import ->
          import.name == "$featurePackage.R" || import.name.startsWith("$featurePackage.R.")
        }
      }
    }
  }
}
