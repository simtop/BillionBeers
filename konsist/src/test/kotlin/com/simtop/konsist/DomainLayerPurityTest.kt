package com.simtop.konsist

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * :beerdomain:api is the pure-Kotlin domain layer - models, repository interfaces, error types. An
 * android.* import here means an Android framework type leaked into a layer that every other module
 * (including a future KMP non-Android target) depends on.
 */
class DomainLayerPurityTest {

  @Test
  fun `beerdomain domain layer has no android imports`() {
    val domainFiles =
      Konsist.scopeFromProject().files.filter { it.packagee?.name?.let(::isDomainPackage) == true }
    assertTrue(domainFiles.isNotEmpty()) {
      "No files were discovered in com.simtop.beerdomain.domain; this boundary would pass vacuously"
    }

    domainFiles.assertFalse { file ->
      file.hasImport { import -> isAndroidFrameworkImport(import.name) }
    }
  }

  @Test
  fun `Android framework import matcher leaves AndroidX imports alone`() {
    assertTrue(isAndroidFrameworkImport("android.content.Context"))
    assertTrue(isAndroidFrameworkImport("android"))
    assertTrue(!isAndroidFrameworkImport("androidx.annotation.NonNull"))
    assertTrue(isDomainPackage("com.simtop.beerdomain.domain.model"))
    assertTrue(!isDomainPackage("com.simtop.beerdomain.domainish.model"))
  }

  private fun isAndroidFrameworkImport(importName: String): Boolean =
    importName == "android" || importName.startsWith("android.")

  private fun isDomainPackage(packageName: String): Boolean =
    packageName == DOMAIN_PACKAGE || packageName.startsWith("$DOMAIN_PACKAGE.")

  private companion object {
    const val DOMAIN_PACKAGE = "com.simtop.beerdomain.domain"
  }
}
