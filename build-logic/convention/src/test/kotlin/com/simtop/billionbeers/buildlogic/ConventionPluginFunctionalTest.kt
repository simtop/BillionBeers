package com.simtop.billionbeers.buildlogic

import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertFalse
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

    val stored = runner().withArguments("tasks", "--configuration-cache").build()
    assertTrue(stored.output.contains("Configuration cache entry stored"), stored.output)
    val reused = runner().withArguments("tasks", "--configuration-cache").build()

    assertTrue(reused.output.contains("Configuration cache entry reused"), reused.output)
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

    val instrumented =
      runner().withArguments("compileDebugAndroidTestKotlin", "--dry-run").build()

    assertFalse(instrumented.output.contains("generatePaparazziTest"), instrumented.output)

    val unit = runner().withArguments("compileDebugUnitTestKotlin", "--dry-run").build()

    assertTrue(
      unit.output.contains("generatePaparazziTest"),
      unit.output,
    )
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
    val expectedTestedAbi =
      when (System.getProperty("os.arch")) {
        "aarch64", "arm64" -> "arm64-v8a"
        else -> "x86_64"
      }

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

      tasks.register("assertManagedDeviceContract") {
        doLast {
          val devices = android.testOptions.managedDevices.localDevices
          check(devices.getByName("atdApi35").testedAbi == "$expectedTestedAbi")
          println("MANAGED_DEVICE_CONTRACT_OK")
        }
      }
      """.trimIndent(),
    )

    val result =
      runner().withArguments("assertManagedDeviceContract", "tasks", "--all", "--stacktrace").build()

    assertTrue(result.output.contains("MANAGED_DEVICE_CONTRACT_OK"), result.output)
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

      tasks.register("assertApplicationContract") {
        doLast {
          check(tasks.findByName("assembleDebug") != null)
          check(tasks.findByName("lintDebug") != null)
          check(tasks.findByName("jacocoDebugReport") != null)
          println("APPLICATION_CONTRACT_OK")
        }
      }
      """.trimIndent(),
    )

    val result = runner().withArguments("assertApplicationContract").build()

    assertTrue(result.output.contains("APPLICATION_CONTRACT_OK"), result.output)
  }

  @Test
  fun `library convention exposes debug coverage and verification tasks`() {
    writeAndroidFixture("include(\":testing-utils\")")
    writeFile("testing-utils/build.gradle.kts", "")
    writeBuildFile(
      """
      import org.gradle.testing.jacoco.tasks.JacocoReport

      plugins { id("billionbeers.android.library") }

      android {
        namespace = "com.simtop.conventiontest"
        compileSdk = 37
      }

      tasks.register("assertCoverageContract") {
        doLast {
          check(tasks.findByName("assembleDebug") != null)
          check(tasks.findByName("detekt") != null)
          check(tasks.findByName("jacocoDebugReport") != null)
          val report = tasks.named<JacocoReport>("jacocoDebugReport").get()
          check(
            report.taskDependencies.getDependencies(report).any { it.name == "testDebugUnitTest" },
          ) { "jacocoDebugReport must run testDebugUnitTest first" }
          check(report.executionData.files.any { it.name == "testDebugUnitTest.exec" }) {
            "jacocoDebugReport must consume testDebugUnitTest.exec"
          }
          println("COVERAGE_CONTRACT_OK")
        }
      }
      """.trimIndent(),
    )

    val result = runner().withArguments("assertCoverageContract", "tasks", "--all").build()

    assertTrue(result.output.contains("COVERAGE_CONTRACT_OK"), result.output)
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

      tasks.register("assertBoundaryContract") {
        doLast {
          val verification = tasks.named("check").get()
          check(tasks.findByName("checkDataLayerClasspathBoundary") != null)
          check(tasks.findByName("jacocoDebugReport") != null)
          check(
            verification.taskDependencies.getDependencies(verification)
              .any { it.name == "checkDataLayerClasspathBoundary" },
          ) { "check must depend on checkDataLayerClasspathBoundary" }
          println("BOUNDARY_CONTRACT_OK")
        }
      }
      """.trimIndent(),
    )

    val result = runner().withArguments("assertBoundaryContract").build()

    assertTrue(result.output.contains("BOUNDARY_CONTRACT_OK"), result.output)
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

      tasks.register("assertBoundaryContract") {
        doLast {
          val verification = tasks.named("check").get()
          check(tasks.findByName("checkDataLayerClasspathBoundary") != null)
          check(tasks.findByName("jacocoDebugReport") != null)
          check(
            verification.taskDependencies.getDependencies(verification)
              .any { it.name == "checkDataLayerClasspathBoundary" },
          ) { "check must depend on checkDataLayerClasspathBoundary" }
          println("BOUNDARY_CONTRACT_OK")
        }
      }
      """.trimIndent(),
    )

    val result = runner().withArguments("assertBoundaryContract").build()

    assertTrue(result.output.contains("BOUNDARY_CONTRACT_OK"), result.output)
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
      result.output.contains("SigningConfig \"release\" is missing required property \"storeFile\""),
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
