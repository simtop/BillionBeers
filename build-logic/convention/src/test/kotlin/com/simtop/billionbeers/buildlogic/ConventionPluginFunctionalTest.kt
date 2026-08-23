package com.simtop.billionbeers.buildlogic

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ConventionPluginFunctionalTest {

  @TempDir lateinit var testProjectDir: Path

  @Test
  fun `Kotlin options convention configures the project JVM target`() {
    writeSettings()
    writeBuildFile(
      """
      import org.gradle.api.JavaVersion
      import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

      plugins {
        id("org.jetbrains.kotlin.jvm") version "2.4.10"
        id("billionbeers.kotlin.options")
      }

      java {
        sourceCompatibility = JavaVersion.VERSION_23
        targetCompatibility = JavaVersion.VERSION_23
      }

      tasks.withType<KotlinJvmCompile>().configureEach {
        doLast {
          println("CONVENTION_JVM_TARGET=" + compilerOptions.jvmTarget.get())
        }
      }
      """.trimIndent(),
    )
    writeFile("src/main/kotlin/ConventionSource.kt", "fun conventionSource() = 42")

    val result =
      runner()
        .withArguments("compileKotlin", "--stacktrace")
        .build()

    assertTrue(result.output.contains("CONVENTION_JVM_TARGET=JVM_23"))
    assertTrue(result.task(":compileKotlin")?.outcome == TaskOutcome.SUCCESS)
  }

  @Test
  fun `spotless convention exposes a check task and rejects unformatted Kotlin`() {
    writeSettings()
    writeBuildFile(
      """
      plugins {
        id("billionbeers.spotless")
      }
      """.trimIndent(),
    )
    writeFile("src/main/kotlin/Unformatted.kt", "fun  unformatted( ) = 42")

    val result =
      runner()
        .withArguments("spotlessCheck", "--stacktrace")
        .buildAndFail()

    assertTrue(result.output.contains("spotlessKotlinCheck"))
  }

  @Test
  fun `managed device convention creates both device lanes and their groups`() {
    writeSettings()
    writeBuildFile(
      """
      plugins {
        id("com.android.library") version "9.2.1"
        id("billionbeers.android.managed.device")
      }

      android {
        namespace = "com.simtop.conventiontest"
        compileSdk = 37
      }
      """.trimIndent(),
    )

    val result = runner().withArguments("tasks", "--all", "--stacktrace").build()

    assertTrue(result.output.contains("atdApi35Setup"))
    assertTrue(result.output.contains("pixel9Api37Setup"))
    assertTrue(result.output.contains("ciGroupDebugAndroidTest"))
    assertTrue(result.output.contains("compatGroupDebugAndroidTest"))
  }

  private fun runner(): GradleRunner =
    GradleRunner.create()
      .withProjectDir(testProjectDir.toFile())
      .withPluginClasspath()
      .forwardOutput()

  private fun writeSettings() {
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
      rootProject.name = "convention-plugin-test"
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
