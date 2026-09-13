package com.simtop.konsist

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private val kmpSourceSets = setOf("commonMain", "androidMain", "jvmMain", "wasmJsMain")

internal fun kmpSourceSet(path: String): String? =
  path
    .split(File.separatorChar)
    .windowed(2)
    .firstOrNull { (parent, child) ->
      parent == "src" &&
        (child in kmpSourceSets || child.startsWith("ios") && child.endsWith("Main"))
    }
    ?.last()

internal fun isForbiddenCommonImport(importName: String): Boolean =
  importName == "android" ||
    importName.startsWith("android.") ||
    importName == "java" ||
    importName.startsWith("java.") ||
    importName == "javax" ||
    importName.startsWith("javax.") ||
    importName == "jdk" ||
    importName.startsWith("jdk.") ||
    importName == "sun" ||
    importName.startsWith("sun.")

class KmpSourceSetBoundaryTest {

  @Test
  fun `KMP source files are represented by the Konsist project scope`() {
    val root = repoRoot()
    val sourceFiles =
      root
        .walkTopDown()
        .onEnter { it.name !in setOf("build", "bin", ".git", ".gradle", "generated") }
        .filter {
          it.isFile && it.extension == "kt" && kmpSourceSet(it.relativeTo(root).path) != null
        }
        .toList()
    val scopedFiles = Konsist.scopeFromProject().files.map { it.path }.toSet()

    if (sourceFiles.isNotEmpty()) {
      assertTrue(
        scopedFiles.isNotEmpty(),
        "KMP source roots exist but Konsist discovered no Kotlin files",
      )
      sourceFiles.forEach { file ->
        assertTrue(
          file.absolutePath in scopedFiles,
          "KMP source file is outside Konsist scope: $file",
        )
      }
    }
  }

  @Test
  fun `common source sets contain no platform-only imports`() {
    Konsist.scopeFromProject()
      .files
      .filter { kmpSourceSet(it.path) == "commonMain" }
      .assertFalse { file -> file.hasImport { import -> isForbiddenCommonImport(import.name) } }
  }

  @Test
  fun `source set classification has positive population controls`() {
    val controls =
      mapOf(
        "src/commonMain/kotlin/Contract.kt" to "commonMain",
        "src/androidMain/kotlin/Platform.kt" to "androidMain",
        "src/jvmMain/kotlin/Platform.kt" to "jvmMain",
        "src/iosArm64Main/kotlin/Platform.kt" to "iosArm64Main",
        "src/wasmJsMain/kotlin/Platform.kt" to "wasmJsMain",
      )
    assertEquals(5, controls.size)
    controls.forEach { (path, expected) -> assertEquals(expected, kmpSourceSet(path)) }
  }

  @Test
  fun `common import controls distinguish allowed and forbidden platforms`() {
    val allowed =
      listOf("androidx.annotation.NonNull", "kotlin.collections.List", "kotlinx.datetime.Instant")
    val forbidden =
      listOf(
        "android.content.Context",
        "java.util.Locale",
        "javax.inject.Inject",
        "jdk.internal.misc.Unsafe",
      )

    assertTrue(allowed.none(::isForbiddenCommonImport))
    assertTrue(forbidden.all(::isForbiddenCommonImport))
  }
}
