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
  fun `configuration cache is reusable for managed device task discovery`() {
    writeAndroidFixture()
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

    runner().withArguments("tasks", "--configuration-cache").build()
    val reused = runner().withArguments("tasks", "--configuration-cache").build()

    assertTrue(reused.output.contains("Reusing configuration cache"))
  }

  @Test
  fun `screenshot generation is not attached to instrumented compilation`() {
    writeScreenshotFixture()
    writeBuildFile(
      """
      plugins {
        id("com.android.library") version "9.2.1"
        id("billionbeers.android.screenshot")
      }

      android {
        namespace = "com.simtop.conventiontest"
        compileSdk = 37
      }
      """.trimIndent(),
    )

    val result = runner().withArguments("compileDebugAndroidTestKotlin").build()

    assertTrue(
      result.task(":compileDebugAndroidTestKotlin")?.outcome in
        setOf(TaskOutcome.SUCCESS, TaskOutcome.NO_SOURCE, TaskOutcome.UP_TO_DATE),
    )
    assertTrue(result.output.contains("compileDebugAndroidTestKotlin"))
  }

  @Test
  fun `Metro graph reports stay opt-in`() {
    writeSettings()
    writeBuildFile(
      """
      import dev.zacsweers.metro.gradle.MetroPluginExtension

      plugins {
        id("com.android.library") version "9.2.1"
        id("dev.zacsweers.metro")
      }

      android {
        namespace = "com.simtop.metroreporttest"
        compileSdk = 37
      }

      gradle.projectsEvaluated {
        val destination =
          project.extensions.getByType<MetroPluginExtension>().reportsDestination.orNull
        println(
          "METRO_REPORT_DESTINATION=" +
            (destination?.asFile?.invariantSeparatorsPath ?: "absent")
        )
      }
      """.trimIndent(),
    )

    val ordinary = runner().withArguments("help", "--stacktrace").build()
    assertTrue(ordinary.output.contains("METRO_REPORT_DESTINATION=absent"))

    val reporting =
      runner()
        .withArguments("help", "-Pmetro.reportsDestination=reports/metro", "--stacktrace")
        .build()
    assertTrue(reporting.output.contains("/build/reports/metro"))
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

  @Test
  fun `application convention exposes debug quality and coverage tasks`() {
    writeAndroidFixture()
    writeBuildFile(
      """
      plugins { id("billionbeers.android.application") }

      android {
        namespace = "com.simtop.conventiontest"
        compileSdk = 37
      }
      """.trimIndent(),
    )

    val result = runner().withArguments("tasks", "--all").build()

    assertTrue(result.output.contains("assembleDebug"))
    assertTrue(result.output.contains("lintDebug"))
    assertTrue(result.output.contains("jacocoDebugReport"))
  }

  @Test
  fun `library convention exposes debug coverage and verification tasks`() {
    writeAndroidFixture("include(\":testing-utils\")")
    writeFile("testing-utils/build.gradle.kts", "")
    writeBuildFile(
      """
      plugins { id("billionbeers.android.library") }

      android {
        namespace = "com.simtop.conventiontest"
        compileSdk = 37
      }
      """.trimIndent(),
    )

    val result = runner().withArguments("tasks", "--all").build()

    assertTrue(result.output.contains("assembleDebug"))
    assertTrue(result.output.contains("jacocoDebugReport"))
    assertTrue(result.output.contains("detekt"))
  }

  @Test
  fun `regular feature convention registers the data layer boundary check`() {
    writeFeatureFixture()
    writeBuildFile(
      """
      plugins { id("billionbeers.android.feature") }

      android {
        namespace = "com.simtop.conventiontest.feature"
        compileSdk = 37
      }
      """.trimIndent(),
    )

    val result = runner().withArguments("tasks", "--all").build()

    assertTrue(result.output.contains("checkDataLayerClasspathBoundary"))
    assertTrue(result.output.contains("jacocoDebugReport"))
  }

  @Test
  fun `dynamic feature convention registers the data layer boundary check`() {
    writeDynamicFeatureFixture()
    writeBuildFile(
      """
      plugins { id("billionbeers.android.dynamic.feature") }

      android {
        namespace = "com.simtop.conventiontest.dynamicfeature"
        compileSdk = 37
      }
      """.trimIndent(),
    )

    val result = runner().withArguments("tasks", "--all").build()

    assertTrue(result.output.contains("checkDataLayerClasspathBoundary"))
    assertTrue(result.output.contains("jacocoDebugReport"))
  }

  @Test
  fun `feature boundary rejects a data layer arriving through an api dependency`() {
    writeFeatureFixture(includeDataLayerBridge = true)
    writeBuildFile(
      """
      plugins { id("billionbeers.android.feature") }

      android {
        namespace = "com.simtop.conventiontest.feature"
        compileSdk = 37
      }

      dependencies {
        implementation(project(":bridge"))
      }
      """.trimIndent(),
    )

    val result = runner().withArguments("checkDataLayerClasspathBoundary").buildAndFail()

    assertTrue(result.output.contains("resolves data-layer module(s)"))
    assertTrue(result.output.contains(":beer_data"))
  }

  @Test
  fun `release validation reports missing signing credentials`() {
    writeAndroidFixture()
    writeFile(
      "src/main/AndroidManifest.xml",
      """
      <manifest xmlns:android="http://schemas.android.com/apk/res/android">
        <application android:theme="@android:style/Theme.Material.Light" />
      </manifest>
      """.trimIndent(),
    )
    writeFile("proguard-rules.pro", "")
    writeBuildFile(
      """
      plugins { id("billionbeers.android.application") }

      android {
        namespace = "com.simtop.conventiontest"
        compileSdk = 37
      }
      """.trimIndent(),
    )

    val result = runner().withArguments("assembleRelease").buildAndFail()

    assertTrue(
      result.output.contains("keystore") ||
        result.output.contains("signing") ||
        result.output.contains("SigningConfig"),
      result.output,
    )
  }

  private fun runner(): GradleRunner =
    GradleRunner.create()
      .withProjectDir(testProjectDir.toFile())
      .withPluginClasspath()
      .forwardOutput()

  private fun writeScreenshotFixture() {
    writeSettings("include(\":snapshot-testing\", \":snapshot-processor\")")
    writeFile(
      "snapshot-testing/build.gradle.kts",
      """
      plugins { id("com.android.library") }
      android {
        namespace = "com.simtop.snapshot.testing"
        compileSdk = 37
      }
      """.trimIndent(),
    )
    writeFile(
      "snapshot-processor/build.gradle.kts",
      """
      plugins { id("java-library") }
      """.trimIndent(),
    )
  }

  private fun writeAndroidFixture(includes: String = "") {
    writeSettings(includes)
    val catalog =
      generateSequence(Path.of(System.getProperty("user.dir"))) { it.parent }
        .map { it.resolve("gradle/libs.versions.toml") }
        .firstOrNull { it.exists() }
    check(catalog != null) { "Could not locate the repository version catalog" }
    testProjectDir.resolve("gradle").createDirectories()
    catalog.copyTo(testProjectDir.resolve("gradle/libs.versions.toml"), overwrite = true)
    testProjectDir.resolve("gradle.properties").writeText(
      "billionbeers.versionCode=1\nbillionbeers.versionName=1.0\n",
    )
  }

  private fun writeFeatureFixture(includeDataLayerBridge: Boolean = false) {
    val includes =
      listOf(
          ":core",
          ":core-common",
          ":presentation_utils",
          ":beerdomain:api",
          ":testing-utils",
          if (includeDataLayerBridge) ":bridge" else null,
          if (includeDataLayerBridge) ":beer_data" else null,
        )
        .filterNotNull()
        .joinToString(", ") { "\"$it\"" }
    writeAndroidFixture("include($includes)")
    listOf("core", "core-common", "presentation_utils", "testing-utils").forEach {
      writeFile("$it/build.gradle.kts", "")
    }
    writeFile("beerdomain/api/build.gradle.kts", "")
    if (includeDataLayerBridge) {
      writeFile("beer_data/build.gradle.kts", "plugins { id(\"java-library\") }")
      writeFile(
        "bridge/build.gradle.kts",
        """
        plugins { id("java-library") }
        dependencies { api(project(":beer_data")) }
        """.trimIndent(),
      )
    }
  }

  private fun writeDynamicFeatureFixture() {
    writeAndroidFixture("include(\":app\", \":testing-utils\")")
    writeFile("testing-utils/build.gradle.kts", "")
    writeFile(
      "app/build.gradle.kts",
      """
      plugins { id("com.android.application") }
      android {
        namespace = "com.simtop.conventiontest.app"
        compileSdk = 37
        defaultConfig { applicationId = "com.simtop.conventiontest.app" }
      }
      """.trimIndent(),
    )
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
