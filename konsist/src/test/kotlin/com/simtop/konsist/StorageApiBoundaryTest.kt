package com.simtop.konsist

import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StorageApiBoundaryTest {

  @Test
  fun `storage api exposes no platform or persistence types`() {
    val root = repoRoot()
    val sourceRoot = File(root, "beer_storage/api/src")
    val forbiddenTokens =
      listOf(
        "android.",
        "androidx.room",
        "androidx.sqlite",
        "android.database",
        "org.w3c.dom",
        "kotlinx.browser",
        "BeerDbModel",
        "PagingStateDbModel",
      )

    val sources = sourceRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    assertTrue(sources.isNotEmpty()) { "No storage API Kotlin sources found under $sourceRoot" }

    val violations = sources.flatMap { source ->
      val text = source.readText()
      forbiddenTokens
        .filter { token -> text.contains(token) }
        .map { token ->
          "${source.relativeTo(root)} contains $token"
        }
    }
    assertTrue(violations.isEmpty()) {
      "Storage API must remain portable and Room-free:\n${violations.joinToString("\n")}"
    }
  }
}
