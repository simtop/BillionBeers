package com.simtop.konsist

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Dynamic-feature modules ship as on-demand split APKs. A user-facing string declared in the
 * split's own package compiles, but can crash under an instrumented test because a split's resource
 * table is not merged into the resolving context like an installed base module's is. The invariant:
 * every string a dynamic feature renders lives in :presentation_utils (a base module) and is
 * referenced through com.simtop.presentation_utils.R, never the feature's own R.
 *
 * Dynamic features are discovered from convention-plugin application in their build scripts. A new
 * on-demand feature therefore cannot silently opt out by being omitted from a hand-maintained list.
 * The check catches the failure path that matters: Kotlin code importing a split-owned R. A dead
 * strings.xml that nothing references is harmless and cannot be identified by this source check.
 */
class DynamicFeatureResourceBoundaryTest {

  @Test
  fun `every dynamic feature is discovered`() {
    assertTrue(dynamicFeatureModules().isNotEmpty()) {
      "No dynamic-feature convention applications were discovered under ${repoRoot()}/feature - " +
        "the resource boundary would pass vacuously"
    }
  }

  @Test
  fun `dynamic-feature code references resources through presentation_utils, not its own R`() {
    dynamicFeatureModules().forEach { feature ->
      val files =
        Konsist.scopeFromProject().files.filter {
          it.packagee?.name?.startsWith(feature.namespace) == true
        }

      assertTrue(files.isNotEmpty()) {
        "No files scoped for ${feature.namespace} - the namespace no longer matches this module"
      }

      files.assertFalse { file ->
        file.hasImport { import ->
          import.name == "${feature.namespace}.R" ||
            import.name.startsWith("${feature.namespace}.R.")
        }
      }
    }
  }

  private data class DynamicFeatureModule(val script: File, val namespace: String)

  private fun dynamicFeatureModules(): List<DynamicFeatureModule> {
    val root = repoRoot()
    val scripts =
      File(root, "feature")
        .listFiles { file -> file.isDirectory }
        .orEmpty()
        .map { File(it, "build.gradle.kts") }
        .filter { it.isFile }
        .filter { it.uncommentedText().contains("billionbeers.android.dynamic.feature") }
        .sortedBy { it.path }

    assertTrue(scripts.isNotEmpty()) {
      "No dynamic-feature build scripts found under $root/feature - the layout changed and " +
        "this rule would pass vacuously"
    }

    return scripts.map { script ->
      val namespace =
        Regex("namespace\\s*=\\s*[\"']([^\"']+)[\"']")
          .find(script.uncommentedText())
          ?.groupValues
          ?.get(1)
          .orEmpty()
      assertTrue(namespace.isNotBlank()) {
        "${script.relativeTo(root)} applies the dynamic-feature convention but does not declare " +
          "an Android namespace; the resource boundary cannot identify its own R"
      }
      DynamicFeatureModule(script, namespace)
    }
  }
}
