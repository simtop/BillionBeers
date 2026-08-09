package com.simtop.konsist

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.jupiter.api.Test

/**
 * Feature modules are siblings, never dependents of each other - each communicates with the rest of
 * the app only through the domain layer (repositories, use cases, models) and navigation. A feature
 * importing another feature directly is exactly the kind of coupling that makes dynamic delivery
 * (feature/beerdetail) and independent feature ownership impossible to scale.
 */
class FeatureModuleBoundaryTest {

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
