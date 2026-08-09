package com.simtop.konsist

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.jupiter.api.Test

/**
 * :beerdomain:api is the pure-Kotlin domain layer - models, repository interfaces, error types. An
 * android.* import here means an Android framework type leaked into a layer that every other module
 * (including a future KMP non-Android target) depends on.
 */
class DomainLayerPurityTest {

  @Test
  fun `beerdomain domain layer has no android imports`() {
    Konsist.scopeFromProject()
      .files
      .filter { it.packagee?.name?.startsWith("com.simtop.beerdomain.domain") == true }
      .assertFalse { file -> file.hasImport { import -> import.name.startsWith("android") } }
  }
}
