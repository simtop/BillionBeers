package com.simtop.konsist

import java.io.File
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BuildScriptsTest {

  @Test
  fun `build script scan ignores nested worktrees`() {
    val root = Files.createTempDirectory("build-scripts-fixture").toFile()
    try {
      File(root, "feature/build.gradle.kts").apply {
        parentFile.mkdirs()
        writeText("plugins { }\n")
      }
      File(root, ".claude/worktrees/stale/src/main/kotlin/Ghost.kt").apply {
        parentFile.mkdirs()
        writeText("class Ghost\n")
      }
      File(root, ".claude/worktrees/stale/build.gradle.kts").writeText("plugins { }\n")

      assertEquals(
        listOf("feature/build.gradle.kts"),
        buildScripts(root).map { it.relativeTo(root).invariantSeparatorsPath },
      )
    } finally {
      root.deleteRecursively()
    }
  }
}
