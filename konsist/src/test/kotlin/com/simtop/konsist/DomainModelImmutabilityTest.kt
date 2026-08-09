package com.simtop.konsist

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Domain models are values: two `Beer`s with the same fields are the same beer, and nothing that
 * receives one can change what the sender sees. The app leans on this. `PagedListReducer` folds
 * pages into a list and hands it to Compose, which decides what to recompose by comparing old state
 * to new - a model mutated in place is the same reference, so the comparison says "unchanged" and
 * the UI silently keeps rendering stale data. The mappers and the pager pass the same instances
 * around on the same assumption.
 *
 * Two ways to break it, so two rules:
 *
 * 1. A `var` property - the obvious one.
 * 2. A `val` holding a **mutable collection**. `val items: MutableList<Beer>` reads as immutable
 *    and is not: the reference is fixed, the contents are not, and `items.add(...)` mutates
 *    something another layer is already holding. This is the half that slips through review, which
 *    is why it is enforced rather than trusted.
 *
 * `Array` is in the same list for a second reason: it uses identity equality, so a data class with
 * an `Array` property gets an `equals` that reports two identical models as different - breaking
 * the same recomposition comparison from the opposite direction.
 *
 * Scoped to `com.simtop.beerdomain.domain`, matching [DomainLayerPurityTest]. Konsist's
 * `properties()` includes primary-constructor properties, which is where every model here declares
 * its state - verified by mutating a real model and watching this fail, not assumed.
 */
class DomainModelImmutabilityTest {

  /** Bare type names, since a property's type is written unqualified in practice. */
  private val mutableTypeNames =
    setOf(
      "MutableList",
      "MutableSet",
      "MutableMap",
      "MutableCollection",
      "MutableIterable",
      "ArrayList",
      "HashMap",
      "HashSet",
      "LinkedHashMap",
      "LinkedHashSet",
      "Array",
    )

  private fun domainClasses(): List<KoClassDeclaration> =
    Konsist.scopeFromProject()
      .files
      .filter { it.packagee?.name?.startsWith("com.simtop.beerdomain.domain") == true }
      .flatMap { it.classes(includeNested = true) }

  @Test
  fun `domain models expose no var properties`() {
    val classes = domainClasses()

    assertTrue(classes.isNotEmpty()) {
      "No classes found under com.simtop.beerdomain.domain - the domain layout changed and this " +
        "rule would pass vacuously"
    }

    val violations = classes.flatMap { koClass ->
      koClass.properties().filter { it.hasVarModifier }.map { "${koClass.name}.${it.name}" }
    }

    assertTrue(violations.isEmpty()) {
      "Domain models must be immutable, but these are `var`: $violations. Model a change as a new " +
        "value (`copy(...)`) instead - Compose compares old state to new to decide what to redraw, " +
        "and an in-place mutation keeps the same reference, so the UI never updates."
    }
  }

  @Test
  fun `domain models hold no mutable collection types`() {
    val violations =
      domainClasses().flatMap { koClass ->
        koClass.properties().mapNotNull { property ->
          val typeName = property.type?.name?.substringBefore('<')?.substringAfterLast('.')
          if (typeName in mutableTypeNames) "${koClass.name}.${property.name}: $typeName" else null
        }
      }

    assertTrue(violations.isEmpty()) {
      "These properties are `val` but hold mutable contents: $violations. Use the read-only " +
        "interface (List/Set/Map) - a val MutableList is not an immutable model, and whoever " +
        "received it can change what the sender is still holding."
    }
  }
}
