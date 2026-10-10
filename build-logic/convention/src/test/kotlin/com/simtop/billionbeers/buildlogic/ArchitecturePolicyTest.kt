package com.simtop.billionbeers.buildlogic

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArchitecturePolicyTest {

  private val policy = ArchitecturePolicy.load(policyPath().toFile())

  @Test
  fun `new feature paths use the regular feature role without a package list`() {
    assertEquals("regular-feature", policy.roleForPath(":feature:alpha"))
    assertEquals(
      "dynamic-feature",
      policy.role(":feature:detail", setOf("com.android.dynamic-feature")),
    )
  }

  @Test
  fun `feature to feature and feature to data are forbidden`() {
    assertFalse(policy.allows("regular-feature", "regular-feature", "main"))
    assertFalse(policy.allows("regular-feature", "data", "main"))
    assertTrue("data" in policy.forbiddenRoles("regular-feature"))
  }

  @Test
  fun `dynamic feature to app is an explicit allowed edge`() {
    assertTrue(policy.allows("dynamic-feature", "application", "main"))
    assertFalse(policy.allows("regular-feature", "application", "main"))
  }

  @Test
  fun `domain implementation is shared behavior with a narrow dependency boundary`() {
    assertEquals("domain-impl", policy.roleForPath(":beerdomain:impl"))
    assertTrue(policy.allows("domain-impl", "domain-api", "main"))
    assertTrue(policy.allows("domain-impl", "core-common", "main"))
    assertTrue(policy.allows("application", "domain-impl", "main"))
    assertTrue(policy.allows("regular-feature", "domain-impl", "main"))
    assertTrue(policy.allows("shared-app", "domain-impl", "main"))
    assertFalse(policy.allows("domain-impl", "data", "main"))
    assertTrue("data" in policy.forbiddenRoles("domain-impl"))
  }

  @Test
  fun `api exposure requires an explicit policy entry`() {
    assertTrue(policy.allowsApi("core", "core-common"))
    assertFalse(policy.allowsApi("core", "data"))
  }

  private fun policyPath(): Path {
    val current = Path.of(System.getProperty("user.dir"))
    return generateSequence(current) { it.parent }
      .map { it.resolve("config/architecture/project-dependency-policy.json") }
      .firstOrNull { it.exists() && !it.isDirectory() }
      ?: error("Could not find the checked-in architecture policy")
  }
}
