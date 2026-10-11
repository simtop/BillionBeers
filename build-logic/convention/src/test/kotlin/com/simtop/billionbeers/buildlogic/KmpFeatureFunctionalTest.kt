package com.simtop.billionbeers.buildlogic

import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class KmpFeatureFunctionalTest {

  @TempDir lateinit var testProjectDir: Path

  @Test
  fun `shared feature convention supplies its production and JVM test baseline`() {
    writeFeatureFixture()

    val result =
      runner()
        .withArguments(":shared:sample:jvmTest", ":shared:sample:tasks", "--all", "--stacktrace")
        .build()

    assertTrue(
      result.task(":shared:sample:jvmTest")?.outcome == TaskOutcome.SUCCESS,
      result.output,
    )
    assertTrue(
      result.output.contains("SampleFeatureTest[jvm] > baselineDependenciesAreAvailable()[jvm] PASSED"),
      result.output,
    )
    assertTrue(result.output.contains("wasmJsBrowserDistribution"), result.output)
    assertTrue(
      testProjectDir
        .resolve("shared/sample/build/test-results/jvmTest")
        .toFile()
        .walkTopDown()
        .any { it.name.startsWith("TEST-") && it.extension == "xml" },
      "jvmTest must emit JUnit XML",
    )
  }

  @Test
  fun `shared feature convention reuses the configuration cache`() {
    writeFeatureFixture()

    val stored =
      runner()
        .withArguments(":shared:sample:compileKotlinJvm", "--configuration-cache")
        .build()
    assertTrue(stored.output.contains("Configuration cache entry stored"), stored.output)

    val reused =
      runner()
        .withArguments(":shared:sample:compileKotlinJvm", "--configuration-cache")
        .build()
    assertTrue(reused.output.contains("Configuration cache entry reused"), reused.output)
  }

  private fun runner(): GradleRunner =
    GradleRunner.create()
      .withProjectDir(testProjectDir.toFile())
      .withPluginClasspath()
      .forwardOutput()

  private fun writeFeatureFixture() {
    copyRepositoryCatalog()
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
      rootProject.name = "kmp-feature-convention-test"
      include(
        ":beerdomain:api",
        ":beerdomain:fakes",
        ":core-common",
        ":shared:designsystem",
        ":shared:sample",
      )
      """.trimIndent(),
    )
    writeFile("build.gradle.kts", "")

    writeStubProject(
      path = "beerdomain/api",
      packageName = "fixture.domain",
      declaration = "const val domainValue: String = \"domain\"",
    )
    writeStubProject(
      path = "beerdomain/fakes",
      packageName = "fixture.fakes",
      declaration = "const val fakeValue: String = \"fake\"",
    )
    writeStubProject(
      path = "core-common",
      packageName = "fixture.core",
      declaration = "const val coreValue: String = \"core\"",
    )
    writeStubProject(
      path = "shared/designsystem",
      packageName = "fixture.design",
      declaration = "const val designValue: String = \"design\"",
    )

    writeFile(
      "shared/sample/build.gradle.kts",
      """
      plugins {
        id("billionbeers.kmp.feature")
      }

      kotlin {
        android {
          check(namespace == "com.simtop.billionbeers.shared.sample") {
            "Unexpected shared feature namespace: ${'$'}namespace"
          }
        }
      }

      tasks.withType<Test>().configureEach {
        testLogging { events("passed", "failed") }
      }
      """.trimIndent(),
    )
    writeFile(
      "shared/sample/src/commonMain/kotlin/SampleFeature.kt",
      """
      package fixture.sample

      import androidx.compose.material3.Text
      import androidx.compose.runtime.Composable
      import androidx.lifecycle.ViewModel
      import fixture.core.coreValue
      import fixture.design.designValue
      import fixture.domain.domainValue

      class SampleViewModel : ViewModel()

      @Composable
      fun SampleFeature() {
        Text("${'$'}domainValue ${'$'}coreValue ${'$'}designValue")
      }
      """.trimIndent(),
    )
    writeFile(
      "shared/sample/src/jvmTest/kotlin/SampleFeatureTest.kt",
      """
      package fixture.sample

      import app.cash.turbine.test
      import fixture.fakes.fakeValue
      import kotlin.test.assertEquals
      import kotlinx.coroutines.flow.MutableStateFlow
      import kotlinx.coroutines.test.runTest
      import org.junit.jupiter.api.Test

      class SampleFeatureTest {
        @Test
        fun baselineDependenciesAreAvailable() = runTest {
          assertEquals("fake", fakeValue)
          MutableStateFlow("ready").test {
            assertEquals("ready", awaitItem())
            cancelAndIgnoreRemainingEvents()
          }
        }
      }
      """.trimIndent(),
    )
  }

  private fun writeStubProject(path: String, packageName: String, declaration: String) {
    writeFile(
      "$path/build.gradle.kts",
      """
      plugins {
        id("billionbeers.kmp.library")
      }
      """.trimIndent(),
    )
    writeFile(
      "$path/src/commonMain/kotlin/Fixture.kt",
      """
      package $packageName

      $declaration
      """.trimIndent(),
    )
  }

  private fun copyRepositoryCatalog() {
    val catalog =
      generateSequence(Path.of(System.getProperty("user.dir"))) { it.parent }
        .map { it.resolve("gradle/libs.versions.toml") }
        .firstOrNull { it.exists() }
    check(catalog != null) { "Could not locate the repository version catalog" }
    testProjectDir.resolve("gradle").createDirectories()
    catalog.copyTo(testProjectDir.resolve("gradle/libs.versions.toml"), overwrite = true)
  }

  private fun writeFile(relativePath: String, contents: String) {
    val file = testProjectDir.resolve(relativePath)
    file.parent.createDirectories()
    file.writeText(contents)
  }
}
