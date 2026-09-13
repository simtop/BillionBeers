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

class KmpLibraryFunctionalTest {

  @TempDir lateinit var testProjectDir: Path

  @Test
  fun `KMP fixture compiles common and JVM tests`() {
    writeKmpFixture()

    val result =
      runner()
        .withArguments("jvmTest", "--stacktrace")
        .build()

    assertTrue(result.task(":compileKotlinJvm")?.outcome == TaskOutcome.SUCCESS, result.output)
    assertTrue(result.task(":jvmTest")?.outcome == TaskOutcome.SUCCESS, result.output)
    assertTrue(result.output.contains("FixtureCommonTest[jvm] > commonApiWorks[jvm] PASSED"), result.output)
    assertTrue(result.output.contains("FixtureJvmTest[jvm] > jvmApiWorks[jvm] PASSED"), result.output)
  }

  @Test
  fun `KMP fixture compiles common metadata explicitly`() {
    writeKmpFixture()
    val result = runner().withArguments("allMetadataJar", "--stacktrace").build()

    assertTrue(result.task(":allMetadataJar")?.outcome == TaskOutcome.SUCCESS, result.output)
    assertTrue(result.output.contains("compileCommonMainKotlinMetadata"), result.output)
  }

  @Test
  fun `KMP common code java API limitation is visible on JVM`() {
    writeKmpFixture()
    writeFile(
      "src/commonMain/kotlin/JavaLeak.kt",
      """
      package fixture

      fun javaLeak(): String = java.util.Locale.getDefault().language
      """.trimIndent(),
    )

    val result = runner().withArguments("compileKotlinJvm", "--stacktrace").build()

    assertTrue(result.task(":compileKotlinJvm")?.outcome == TaskOutcome.SUCCESS, result.output)
    assertTrue(result.output.contains("BUILD SUCCESSFUL"), result.output)
  }

  @Test
  fun `KMP fixture runs the opted in Android host test`() {
    writeKmpFixture()
    val tasks = runner().withArguments("tasks", "--all").build()
    val hostTestTask =
      listOf("testAndroidHostTest", "androidHostTest")
        .firstOrNull { tasks.output.contains(it) }
    assertTrue(hostTestTask != null, tasks.output)

    val result = runner().withArguments(hostTestTask!!, "--stacktrace").build()

    assertTrue(result.output.contains("compileAndroidHostTest"), result.output)
    assertTrue(result.output.contains("FixtureAndroidHostTest"), result.output)
    assertTrue(result.output.contains("PASSED"), result.output)
  }

  @Test
  fun `KMP fixture Android consumer selects the Android variant`() {
    writeKmpFixture(includeConsumer = true)

    val result =
      runner()
        .withArguments(":consumer:compileDebugKotlin", "--stacktrace")
        .build()

    assertTrue(result.task(":compileAndroidMain")?.outcome == TaskOutcome.SUCCESS, result.output)
    assertTrue(result.task(":consumer:compileDebugKotlin")?.outcome == TaskOutcome.SUCCESS, result.output)
  }

  @Test
  fun `KMP fixture configuration cache is reusable`() {
    writeKmpFixture()

    val stored = runner().withArguments("compileKotlinJvm", "--configuration-cache").build()
    assertTrue(stored.output.contains("Configuration cache entry stored"), stored.output)

    val reused = runner().withArguments("compileKotlinJvm", "--configuration-cache").build()
    assertTrue(reused.output.contains("Configuration cache entry reused"), reused.output)
  }

  private fun runner(): GradleRunner =
    GradleRunner.create()
      .withProjectDir(testProjectDir.toFile())
      .withPluginClasspath()
      .forwardOutput()

  private fun writeKmpFixture(includeConsumer: Boolean = false) {
    writeSettings(if (includeConsumer) "include(\":consumer\")" else "")
    copyRepositoryCatalog()
    writeBuildFile(
      """
      plugins {
        id("billionbeers.kmp.library")
        id("dev.zacsweers.metro")
      }

      kotlin {
        sourceSets {
          commonMain.dependencies {
            implementation("dev.zacsweers.metro:runtime:1.4.2")
          }
          commonTest.dependencies {
            implementation(kotlin("test"))
          }
        }
      }

      tasks.withType<Test>().configureEach {
        testLogging { events("passed", "failed") }
      }
      """.trimIndent(),
    )
    writeFile(
      "src/commonMain/kotlin/FixtureApi.kt",
      """
      package fixture

      import dev.zacsweers.metro.AppScope
      import dev.zacsweers.metro.ContributesTo
      import dev.zacsweers.metro.Inject
      import dev.zacsweers.metro.Provides

      interface FixtureService {
        fun value(): String
      }

      @Inject
      class FixtureServiceImpl : FixtureService {
        override fun value(): String = "metro-fixture"
      }

      @ContributesTo(AppScope::class)
      interface FixtureBindings {
        @Provides
        fun bindService(implementation: FixtureServiceImpl): FixtureService = implementation
      }

      fun fixtureValue(): String = "kmp-fixture"
      """.trimIndent(),
    )
    writeFile(
      "src/commonTest/kotlin/FixtureCommonTest.kt",
      """
      package fixture

      import kotlin.test.Test
      import kotlin.test.assertEquals

      class FixtureCommonTest {
        @Test
        fun commonApiWorks() {
          println("KMP_COMMON_TEST_RAN")
          assertEquals("kmp-fixture", fixtureValue())
        }
      }
      """.trimIndent(),
    )
    writeFile(
      "src/jvmMain/kotlin/FixtureJvmApi.kt",
      """
      package fixture

      fun jvmOnlyValue(): String = System.getProperty("fixture.value", "jvm-fixture")
      """.trimIndent(),
    )
    writeFile(
      "src/androidHostTest/kotlin/FixtureAndroidHostTest.kt",
      """
      package fixture

      import kotlin.test.Test

      class FixtureAndroidHostTest {
        @Test
        fun androidHostTestRuns() {
          println("KMP_ANDROID_HOST_TEST_RAN")
        }
      }
      """.trimIndent(),
    )
    writeFile(
      "src/jvmTest/kotlin/FixtureJvmTest.kt",
      """
      package fixture

      import kotlin.test.Test
      import kotlin.test.assertEquals

      class FixtureJvmTest {
        @Test
        fun jvmApiWorks() {
          println("KMP_JVM_TEST_RAN")
          assertEquals("jvm-fixture", jvmOnlyValue())
        }
      }
      """.trimIndent(),
    )
    if (includeConsumer) {
      writeFile(
        "consumer/build.gradle.kts",
        """
        plugins {
          id("com.android.library")
        }

        android {
          namespace = "com.simtop.billionbeers.kmp.consumer"
          compileSdk = 37
        }

        dependencies {
          implementation(project(":"))
        }
        """.trimIndent(),
      )
      writeFile(
        "consumer/src/main/kotlin/Consumer.kt",
        """
        package consumer

        import fixture.fixtureValue

        fun consumedValue(): String = fixtureValue()
        """.trimIndent(),
      )
    }
  }

  private fun discoverTask(output: String, vararg candidates: String): String? =
    candidates.firstOrNull { candidate ->
      output.lines().any { line -> line.trim() == candidate || line.trim().startsWith("$candidate ") }
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

  private fun writeSettings(includes: String = "") {
    testProjectDir.resolve("settings.gradle.kts").writeText(
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
      $includes
      rootProject.name = "kmp-convention-test"
      """.trimIndent(),
    )
  }

  private fun writeBuildFile(contents: String) {
    testProjectDir.resolve("build.gradle.kts").writeText(contents)
  }

  private fun writeFile(relativePath: String, contents: String) {
    val file = testProjectDir.resolve(relativePath)
    file.parent.createDirectories()
    file.writeText(contents)
  }
}
