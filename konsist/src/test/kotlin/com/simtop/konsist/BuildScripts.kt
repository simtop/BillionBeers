package com.simtop.konsist

import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue

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
 * excluded because stale IDE output holds *deleted* sources and would make a rule fail on code that
 * no longer exists (AGENTS.md §2).
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

/**
 * A deny-list rule is only as good as the strings it searches for. If one of them is misspelled or
 * the thing it names gets renamed, the rule keeps passing - not because nothing violates it, but
 * because the check now matches nothing at all. That failure is silent: the test stays green, so
 * nothing points at it.
 *
 * [assertModulePathsExist] and [assertConventionPluginsExist] are the positive controls for the two
 * cases here where a hardcoded string can be checked against something real: a module path can be
 * checked against the filesystem, a convention-plugin id against its precompiled script file (which
 * is always named `<id>.gradle.kts`). Not every hardcoded needle has an equivalent - a banned API
 * name like `MutableSharedFlow` names nothing that should exist in the repo, so there is nothing to
 * check it against; that gap is accepted, not fixed here.
 */
internal fun assertModulePathsExist(modules: Collection<String>, root: File = repoRoot()) {
  // An empty list would make every check below vacuously pass - the exact failure mode this
  // function exists to close, one level up.
  assertTrue(modules.isNotEmpty()) { "No module paths given to check - nothing would be verified" }

  modules.forEach { module ->
    val dir = File(root, module.removePrefix(":").replace(':', '/'))
    assertTrue(dir.isDirectory) {
      "Module path \"$module\" does not resolve to a real directory under $root - this rule's " +
        "hardcoded module list has drifted (typo, or the module was renamed/moved), and the " +
        "boundary check built on top of it is now silently matching nothing"
    }
  }
}

/**
 * Where precompiled script plugins live - a plugin id `foo.bar` is the file `foo.bar.gradle.kts`.
 */
private fun conventionPluginDir(root: File): File =
  File(root, "build-logic/convention/src/main/kotlin")

internal fun assertConventionPluginsExist(pluginIds: Collection<String>, root: File = repoRoot()) {
  // Same vacuous-pass guard as assertModulePathsExist: an empty list would check nothing.
  assertTrue(pluginIds.isNotEmpty()) { "No plugin ids given to check - nothing would be verified" }

  val dir = conventionPluginDir(root)
  pluginIds.forEach { id ->
    val script = File(dir, "$id.gradle.kts")
    assertTrue(script.isFile) {
      "Plugin id \"$id\" has no precompiled script plugin at ${script.relativeTo(root)} - this " +
        "rule's hardcoded plugin-id list has drifted (typo, or the plugin was renamed/moved), and " +
        "the opt-in check built on top of it is now silently matching nothing"
    }
  }
}
