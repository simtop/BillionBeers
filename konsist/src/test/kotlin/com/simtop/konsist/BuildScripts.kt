package com.simtop.konsist

import java.io.File

/**
 * Helpers for the rules that have to read `build.gradle.kts` directly.
 *
 * Konsist's project scope does not surface build scripts - they are excluded, and `.kts` is not
 * scanned at all - so any invariant that lives in a build file (module dependencies, applied
 * plugins) has to be checked by reading the files.
 */

/** Walks up from the test working directory (the :konsist module) to the Gradle root. */
internal fun repoRoot(): File {
  var dir: File? = File(System.getProperty("user.dir"))
  while (dir != null && !File(dir, "settings.gradle.kts").exists()) dir = dir.parentFile
  return dir ?: error("Could not locate Gradle root (no settings.gradle.kts above user.dir)")
}

/**
 * Every `build.gradle.kts` in the project, skipping build output and IDE output trees. `bin/` is
 * excluded because stale IDE output holds *deleted* sources and would make a rule fail on code
 * that no longer exists (AGENTS.md §2).
 */
internal fun buildScripts(root: File = repoRoot()): List<File> =
  root
    .walkTopDown()
    .onEnter { it.name !in setOf("build", "bin", ".git", ".gradle", "gradle-user-home") }
    .filter { it.isFile && it.name == "build.gradle.kts" }
    .toList()

/**
 * The script's contents with comment lines removed, so a rule matching a string cannot be tripped
 * by an explanatory comment that merely mentions it. Block comments are not stripped - no build
 * script here uses them, and a rule that silently ignored one would be worse than a false alarm.
 */
internal fun File.uncommentedText(): String =
  readLines().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")
