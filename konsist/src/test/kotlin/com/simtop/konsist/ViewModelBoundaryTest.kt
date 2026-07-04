package com.simtop.konsist

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.jupiter.api.Test

/**
 * Backs ADR 0003 (docs/adr/0003-use-case-policy.md): deleting the pass-through use cases and
 * letting ViewModels inject BeersRepository directly is only safe because the layer boundary
 * those classes used to guard socially is enforced here instead. A ViewModel importing anything
 * from the data layer fails the build, not just a review.
 */
class ViewModelBoundaryTest {

  private val forbiddenPackagePrefixes =
    listOf(
      "com.simtop.beer_data",
      "com.simtop.beer_database",
      "com.simtop.beer_network",
      "retrofit2",
      "androidx.room",
      "okhttp3",
    )

  @Test
  fun `ViewModels depend only on domain-layer types`() {
    Konsist.scopeFromProject()
      .classes()
      .withNameEndingWith("ViewModel")
      .assertFalse { viewModel ->
        forbiddenPackagePrefixes.any { prefix ->
          viewModel.containingFile.hasImport { import -> import.name.startsWith(prefix) }
        }
      }
  }
}
