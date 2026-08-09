package com.simtop.konsist

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.jupiter.api.Test

/**
 * Repository interfaces are the boundary between the domain layer and the data layer - their public
 * signatures must only expose domain types (Beer, PagingState<E>, Either<E, T>), never DB entities
 * or network DTOs. If a repository interface needs to import beer_database or beer_network, a
 * data-layer type is leaking through its signature.
 */
class RepositoryBoundaryTest {

  private val forbiddenPackagePrefixes =
    listOf("com.simtop.beer_database", "com.simtop.beer_network")

  @Test
  fun `repository interfaces do not import data-layer types`() {
    Konsist.scopeFromProject().interfaces().withNameEndingWith("Repository").assertFalse {
      repository ->
      forbiddenPackagePrefixes.any { prefix ->
        repository.containingFile.hasImport { import -> import.name.startsWith(prefix) }
      }
    }
  }
}
