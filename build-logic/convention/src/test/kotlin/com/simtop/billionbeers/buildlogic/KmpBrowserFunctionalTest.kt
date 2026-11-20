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

class KmpBrowserFunctionalTest {

  @TempDir lateinit var testProjectDir: Path

  @Test
  fun `Wasm fixture discovers browser tasks`() {
    writeWasmFixture()

    val result = runner().withArguments("tasks", "--all", "--stacktrace").build()

    assertTrue(result.output.contains("wasmJs"), result.output)
    assertTrue(result.output.contains("browser"), result.output)
  }

  @Test
  fun `Wasm fixture compiles and runs browser tests`() {
    writeWasmFixture()

    val result = runner().withArguments("wasmJsBrowserTest", "--stacktrace").build()

    assertTrue(result.task(":wasmJsBrowserTest")?.outcome == TaskOutcome.SUCCESS, result.output)
  }

  private fun runner(): GradleRunner =
    GradleRunner.create()
      .withProjectDir(testProjectDir.toFile())
      .withPluginClasspath()
      .withEnvironment(
        mapOf(
          "CHROME_BIN" to
            (System.getenv("CHROME_BIN")
              ?: "/Users/simontopchyan/.cache/puppeteer/chrome-headless-shell/mac_arm-151.0.7922.47/chrome-headless-shell-mac-arm64/chrome-headless-shell")
        )
      )
      .forwardOutput()

  private fun writeWasmFixture() {
    writeSettings()
    copyRepositoryCatalog()
    writeBuildFile(
      """
      plugins {
        id("org.jetbrains.kotlin.multiplatform") version "2.4.10"
        id("dev.zacsweers.metro")
      }

      kotlin {
        wasmJs {
          browser()
          binaries.executable()
        }
        sourceSets {
          commonMain.dependencies {
            implementation("dev.zacsweers.metro:runtime:1.4.2")
            implementation("org.jetbrains.kotlinx:kotlinx-browser:0.3")
          }
          commonTest.dependencies {
            implementation(kotlin("test"))
          }
        }
      }
      """.trimIndent(),
    )
    writeFile(
      "src/commonMain/kotlin/WasmFixture.kt",
      """
      package wasmfixture

      import dev.zacsweers.metro.Inject
      import kotlinx.browser.window
      @Inject
      class WasmService {
        fun value(): String = "metro-wasm-fixture"
      }

      fun wasmFixtureValue(): String = "wasm-fixture"

      fun metroFixtureValue(): String = WasmService().value()

      fun browserOrigin(): String = window.location.origin.toString()
      """.trimIndent(),
    )
    writeFile(
      "src/commonTest/kotlin/WasmFixtureTest.kt",
      """
      package wasmfixture

      import kotlin.test.Test
      import kotlin.test.assertEquals
      import kotlin.test.assertTrue

      class WasmFixtureTest {
        @Test
        fun commonApiRunsInBrowser() {
          assertEquals("wasm-fixture", wasmFixtureValue())
          assertEquals("metro-wasm-fixture", metroFixtureValue())
        }

        @Test
        fun browserEnvironmentIsAvailable() {
          assertTrue(browserOrigin().startsWith("http://"))
        }
      }
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
      rootProject.name = "kmp-browser-test"
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
