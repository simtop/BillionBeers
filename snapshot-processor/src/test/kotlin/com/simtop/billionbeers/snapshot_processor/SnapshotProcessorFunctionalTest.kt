package com.simtop.billionbeers.snapshot_processor

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class SnapshotProcessorFunctionalTest {
  @TempDir lateinit var projectDir: Path

  @Test
  fun `generates nested previews matrix cases and parameter display names`() {
    writeFixture(
      """
      package com.simtop.fixture

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.tooling.preview.Preview
      import androidx.compose.ui.tooling.preview.PreviewParameter
      import androidx.compose.ui.tooling.preview.PreviewParameterProvider
      import com.simtop.billionbeers.core.designsystem.component.AccessibilityMatrixPreview

      @Preview(name = "direct")
      @LightDark
      @Composable
      internal fun directPreview() = Unit

      @AccessibilityMatrixPreview
      @Composable
      internal fun matrixPreview() = Unit

      @Preview(name = "parameter")
      @Composable
      internal fun parameterPreview(
        @PreviewParameter(StringProvider::class, limit = 2) value: String,
      ) = Unit

      @Preview(name = "light", uiMode = 0)
      @Preview(name = "dark", uiMode = 32)
      @Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
      annotation class LightDark

      class StringProvider : PreviewParameterProvider<String> {
        override val values = sequenceOf("one", "two", "three")
        override fun getDisplayName(index: Int) = listOf("FIRST", "SECOND", "THIRD")[index]
      }
      """
        .trimIndent()
    )

    val generated = runKsp().generatedInventory().readText()

    assertTrue(generated.contains("directPreview"), generated)
    assertTrue(generated.contains("parameterPreview"))
    assertTrue(generated.contains("provider.values.take(2)"))
    assertTrue(generated.contains("provider.getDisplayName(index)"))
    assertTrue(generated.contains("AccessibilityMatrix.configurations"))
    assertFalse(generated.contains("provider.values.forEachIndexed"))
  }

  @Test
  fun `aggregating generation removes stale preview entries`() {
    writeFixture(
      """
      package com.simtop.fixture

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.tooling.preview.Preview

      @Preview(name = "stale")
      @Composable
      internal fun stalePreview() = Unit
      """
        .trimIndent()
    )

    val first = runKsp().generatedInventory().readText()
    assertTrue(first.contains("stalePreview"), first)

    writeFixture(
      """
      package com.simtop.fixture

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.tooling.preview.Preview

      @Preview(name = "fresh")
      @Composable
      internal fun freshPreview() = Unit
      """
        .trimIndent()
    )

    val second = runKsp("--rerun-tasks").generatedInventory().readText()
    assertTrue(second.contains("freshPreview"))
    assertFalse(second.contains("stalePreview"))
  }

  @Test
  fun `empty source still generates an explicit empty inventory`() {
    writeFixture(
      """
      package com.simtop.fixture

      fun notAPreview() = Unit
      """
        .trimIndent()
    )

    val generated = runKsp().generatedInventory().readText()

    assertTrue(generated.contains("public val snapshots: List<Snapshot> = buildList"))
    assertFalse(generated.contains("addSnapshot(this"))
  }

  private fun runKsp(vararg extraArguments: String): BuildResult {
    setupProject()
    return GradleRunner.create()
      .withProjectDir(projectDir.toFile())
      .withArguments(listOf("kspKotlin", "--stacktrace") + extraArguments)
      .forwardOutput()
      .build()
  }

  private fun writeFixture(source: String) {
    writeFile("src/main/kotlin/Fixture.kt", source)
    writeFile("src/main/kotlin/Stubs.kt", stubs)
  }

  private fun writeFile(relativePath: String, contents: String) {
    val file = projectDir.resolve(relativePath)
    file.parent.createDirectories()
    file.writeText(contents)
  }

  private fun BuildResult.generatedInventory(): Path {
    val paths = Files.walk(projectDir.resolve("build/generated/ksp"))
    return try {
      paths
        .filter { it.fileName.toString() == "GeneratedPreviewInventory.kt" }
        .findFirst()
        .orElseThrow()
    } finally {
      paths.close()
    }
  }

  private fun runnerBuildFile(): String {
    val processorJar =
      Path.of(System.getProperty("billionbeers.repo.root"))
        .resolve("snapshot-processor/build/libs/snapshot-processor.jar")
        .toString()
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
    return """
      plugins {
        kotlin("jvm") version "2.4.10"
        id("com.google.devtools.ksp") version "2.3.10"
      }

      repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
      }

      dependencies {
        implementation(project(":annotations"))
        ksp(files("$processorJar"))
        ksp("com.squareup:kotlinpoet:2.3.0")
        ksp("com.squareup:kotlinpoet-ksp:2.3.0")
      }
    """
      .trimIndent()
  }

  private fun setupProject() {
    writeFile(
      "settings.gradle.kts",
      """
      pluginManagement {
        repositories {
          google()
          mavenCentral()
          gradlePluginPortal()
        }
      }
      dependencyResolutionManagement {
        repositories {
          google()
          mavenCentral()
        }
      }
      rootProject.name = "snapshot-processor-fixture"
      include(":annotations")
      """
        .trimIndent(),
    )
    writeFile("build.gradle.kts", runnerBuildFile())
    writeFile(
      "annotations/build.gradle.kts",
      "plugins { kotlin(\"jvm\") version \"2.4.10\" }",
    )
    writeFile("annotations/src/main/kotlin/Composable.kt", composableStub)
    writeFile("annotations/src/main/kotlin/Preview.kt", previewStubs)
    writeFile("annotations/src/main/kotlin/AccessibilityMatrixPreview.kt", matrixStub)
  }

  private val stubs =
    """
    package com.simtop.billionbeers.snapshot_testing

    import androidx.compose.runtime.Composable

    data class PreviewConfiguration(
      val name: String,
      val theme: String,
      val fontScale: Float,
      val locale: String,
      val layoutDirection: String,
      val width: String,
      val previewName: String = "",
      val previewGroup: String = "",
      val widthDp: Int = -1,
      val heightDp: Int = -1,
      val uiMode: Int = 0,
      val device: String = "",
    )

    data class Snapshot(
      val name: String,
      val content: @Composable () -> Unit,
      val configuration: PreviewConfiguration,
    )

    object AccessibilityMatrix {
      val configurations = emptyList<PreviewConfiguration>()
    }
    """
      .trimIndent()

  private val composableStub =
    """
    package androidx.compose.runtime

    @Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE)
    annotation class Composable
    """
      .trimIndent()

  private val previewStubs =
    """
    package androidx.compose.ui.tooling.preview

    import kotlin.reflect.KClass

    @Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
    @Repeatable
    annotation class Preview(
      val name: String = "",
      val group: String = "",
      val fontScale: Float = 1f,
      val locale: String = "",
      val uiMode: Int = 0,
      val device: String = "",
      val widthDp: Int = -1,
      val heightDp: Int = -1,
    )

    @Target(AnnotationTarget.VALUE_PARAMETER)
    annotation class PreviewParameter(
      val provider: KClass<*>,
      val limit: Int = Int.MAX_VALUE,
    )

    interface PreviewParameterProvider<T> {
      val values: Sequence<T>
      fun getDisplayName(index: Int): String = index.toString()
    }
    """
      .trimIndent()

  private val matrixStub =
    """
    package com.simtop.billionbeers.core.designsystem.component

    import androidx.compose.ui.tooling.preview.Preview

    @Preview(name = "matrix-marker")
    @Target(AnnotationTarget.FUNCTION)
    annotation class AccessibilityMatrixPreview
    """
      .trimIndent()
}
