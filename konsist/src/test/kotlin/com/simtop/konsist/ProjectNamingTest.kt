package com.simtop.konsist

import com.lemonappdev.konsist.api.Konsist
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProjectNamingTest {
  @Test
  fun `Gradle project paths use lowercase letters digits and hyphens`() {
    val root = repoRoot()
    val projectPaths =
      buildScripts(root)
        .map { it.parentFile.relativeTo(root).invariantSeparatorsPath }
        .filter { it.isNotEmpty() }
    assertTrue(projectPaths.isNotEmpty(), "Expected Gradle project directories under $root")

    val invalid = projectPaths.filter { path ->
      path.split('/').any { !PROJECT_SEGMENT.matches(it) }
    }
    assertTrue(invalid.isEmpty()) {
      "Use lowercase letters, digits, and hyphens in Gradle project paths.\n${invalid.sorted().joinToString("\n")}"
    }
  }

  @Test
  fun `Kotlin packages use lowercase letters and digits`() {
    val packages =
      Konsist.scopeFromProject().files.mapNotNull { file ->
        file.packagee?.name?.let { file.path to it }
      }
    assertTrue(packages.isNotEmpty(), "Expected Kotlin source files with package declarations")

    val invalid = packages.filterNot { (_, name) -> KOTLIN_PACKAGE.matches(name) }
    assertTrue(invalid.isEmpty()) {
      "Use lowercase letters and digits in Kotlin package names.\n" +
        invalid.sortedBy { it.first }.joinToString("\n") { (path, name) -> "$name ($path)" }
    }
  }

  private companion object {
    val PROJECT_SEGMENT = Regex("[a-z][a-z0-9]*(?:-[a-z0-9]+)*")
    val KOTLIN_PACKAGE = Regex("[a-z][a-z0-9]*(?:\\.[a-z][a-z0-9]*)*")
  }
}
