package com.simtop.billionbeers.buildlogic

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class KmpVerificationFunctionalTest {

  @TempDir lateinit var testProjectDir: Path

  @Test
  fun `KMP aggregate tasks include only the matching available tiers`() {
    writeFile(
      "settings.gradle.kts",
      """
      rootProject.name = "kmp-verification-test"
      include(":shared:sample", ":shared:metadata-opt-out", ":plain-jvm")
      """.trimIndent(),
    )
    writeFile(
      "build.gradle.kts",
      """
      plugins { id("billionbeers.kmp.verification") }
      """.trimIndent(),
    )
    writeFile(
      "shared/sample/build.gradle.kts",
      listOf("jvmTest", "allMetadataJar", "testAndroidHostTest", "wasmJsBrowserTest")
        .joinToString("\n") { taskName -> "tasks.register(\"$taskName\") { doLast {} }" },
    )
    writeFile(
      "shared/metadata-opt-out/build.gradle.kts",
      """
      extra["kmpVerificationExcludedTasks"] = setOf("allMetadataJar")
      tasks.register("allMetadataJar") { doLast {} }
      """.trimIndent(),
    )
    writeFile(
      "plain-jvm/build.gradle.kts",
      """
      tasks.register("jvmTest") { doLast {} }
      tasks.register("unrelatedVerification")
      """.trimIndent(),
    )

    val result = runner().withArguments("kmpTest", "--stacktrace").build()

    listOf(
        ":shared:sample:jvmTest",
        ":shared:sample:allMetadataJar",
        ":shared:sample:testAndroidHostTest",
        ":shared:sample:wasmJsBrowserTest",
        ":plain-jvm:jvmTest",
      )
      .forEach { taskPath ->
        assertEquals(TaskOutcome.SUCCESS, result.task(taskPath)?.outcome, result.output)
      }
    assertNull(result.task(":plain-jvm:unrelatedVerification"), result.output)
    assertNull(result.task(":shared:metadata-opt-out:allMetadataJar"), result.output)
  }

  @Test
  fun `KMP aggregate tasks can run one verification tier`() {
    writeFile(
      "settings.gradle.kts",
      """
      rootProject.name = "kmp-verification-test"
      include(":sample")
      """.trimIndent(),
    )
    writeFile(
      "build.gradle.kts",
      """
      plugins { id("billionbeers.kmp.verification") }
      """.trimIndent(),
    )
    writeFile(
      "sample/build.gradle.kts",
      """
      tasks.register("jvmTest") { doLast {} }
      tasks.register("wasmJsBrowserTest") { doLast {} }
      """.trimIndent(),
    )

    val result = runner().withArguments("kmpJvmTest", "--stacktrace").build()

    assertTrue(result.task(":sample:jvmTest")?.outcome == TaskOutcome.SUCCESS, result.output)
    assertNull(result.task(":sample:wasmJsBrowserTest"), result.output)
  }

  @Test
  fun `Apple simulator aggregate discovers every configured simulator test`() {
    writeFile(
      "settings.gradle.kts",
      """
      rootProject.name = "kmp-ios-verification-test"
      include(":first", ":second", ":plain-jvm")
      """.trimIndent(),
    )
    writeFile(
      "build.gradle.kts",
      """
      plugins { id("billionbeers.kmp.verification") }
      """.trimIndent(),
    )
    listOf("first", "second").forEach { module ->
      writeFile(
        "$module/build.gradle.kts",
        "tasks.register(\"iosSimulatorArm64Test\") { doLast {} }",
      )
    }
    writeFile(
      "plain-jvm/build.gradle.kts",
      "tasks.register(\"test\") { doLast {} }",
    )

    val result = runner().withArguments("kmpIosSimulatorTest", "--stacktrace").build()

    listOf(":first:iosSimulatorArm64Test", ":second:iosSimulatorArm64Test").forEach { taskPath ->
      assertEquals(TaskOutcome.SUCCESS, result.task(taskPath)?.outcome, result.output)
    }
    assertNull(result.task(":plain-jvm:test"), result.output)
  }

  @Test
  fun `selected module test routing follows available task tiers`() {
    writeFile(
      "settings.gradle.kts",
      """
      rootProject.name = "kmp-verification-test"
      include(
        ":kmp-all",
        ":browser-with-metadata",
        ":host-with-metadata",
        ":android",
        ":metadata-only",
        ":plain-jvm",
      )
      """.trimIndent(),
    )
    writeFile(
      "build.gradle.kts",
      """
      plugins { id("billionbeers.kmp.verification") }
      """.trimIndent(),
    )
    writeFile(
      "kmp-all/build.gradle.kts",
      listOf("jvmTest", "allMetadataJar", "testAndroidHostTest", "wasmJsBrowserTest")
        .joinToString("\n") { taskName -> "tasks.register(\"$taskName\") { doLast {} }" },
    )
    writeFile(
      "browser-with-metadata/build.gradle.kts",
      """
      tasks.register("allMetadataJar") { doLast { println("METADATA_TIER_RAN") } }
      tasks.register("wasmJsBrowserTest") { doLast { println("BROWSER_TIER_RAN") } }
      """.trimIndent(),
    )
    writeFile(
      "host-with-metadata/build.gradle.kts",
      """
      tasks.register("allMetadataJar") { doLast { println("HOST_METADATA_TIER_RAN") } }
      tasks.register("testAndroidHostTest") { doLast { println("ANDROID_HOST_TIER_RAN") } }
      """.trimIndent(),
    )
    writeFile(
      "android/build.gradle.kts",
      """
      tasks.register("allMetadataJar") { doLast { println("ANDROID_METADATA_TIER_RAN") } }
      tasks.register("testDebugUnitTest") { doLast { println("ANDROID_UNIT_TIER_RAN") } }
      """.trimIndent(),
    )
    writeFile(
      "metadata-only/build.gradle.kts",
      "tasks.register(\"allMetadataJar\") { doLast { println(\"METADATA_ONLY_TIER_RAN\") } }",
    )
    writeFile(
      "plain-jvm/build.gradle.kts",
      """
      tasks.register("allMetadataJar") { doLast { println("JVM_METADATA_TIER_RAN") } }
      tasks.register("test") { doLast { println("JVM_TEST_TIER_RAN") } }
      """.trimIndent(),
    )

    val kmpResult =
      runner().withArguments("-PtestModule=:kmp-all", "testModule", "--stacktrace").build()
    assertEquals(TaskOutcome.SUCCESS, kmpResult.task(":kmp-all:jvmTest")?.outcome, kmpResult.output)
    assertNull(kmpResult.task(":kmp-all:allMetadataJar"), kmpResult.output)

    val browserResult =
      runner()
        .withArguments("-PtestModule=:browser-with-metadata", "testModule", "--stacktrace")
        .build()
    assertEquals(
      TaskOutcome.SUCCESS,
      browserResult.task(":browser-with-metadata:wasmJsBrowserTest")?.outcome,
      browserResult.output,
    )
    assertNull(browserResult.task(":browser-with-metadata:allMetadataJar"), browserResult.output)
    assertTrue(browserResult.output.contains("BROWSER_TIER_RAN"), browserResult.output)

    val hostResult =
      runner()
        .withArguments("-PtestModule=:host-with-metadata", "testModule", "--stacktrace")
        .build()
    assertEquals(
      TaskOutcome.SUCCESS,
      hostResult.task(":host-with-metadata:testAndroidHostTest")?.outcome,
      hostResult.output,
    )
    assertNull(hostResult.task(":host-with-metadata:allMetadataJar"), hostResult.output)
    assertTrue(hostResult.output.contains("ANDROID_HOST_TIER_RAN"), hostResult.output)

    val androidResult =
      runner().withArguments("-PtestModule=:android", "testModule", "--stacktrace").build()
    assertEquals(
      TaskOutcome.SUCCESS,
      androidResult.task(":android:testDebugUnitTest")?.outcome,
      androidResult.output,
    )
    assertNull(androidResult.task(":android:allMetadataJar"), androidResult.output)

    val jvmResult =
      runner().withArguments("-PtestModule=:plain-jvm", "testModule", "--stacktrace").build()
    assertEquals(TaskOutcome.SUCCESS, jvmResult.task(":plain-jvm:test")?.outcome, jvmResult.output)
    assertNull(jvmResult.task(":plain-jvm:allMetadataJar"), jvmResult.output)

    val metadataResult =
      runner()
        .withArguments("-PtestModule=:metadata-only", "testModule", "--stacktrace")
        .build()
    assertEquals(
      TaskOutcome.SUCCESS,
      metadataResult.task(":metadata-only:allMetadataJar")?.outcome,
      metadataResult.output,
    )
  }

  private fun runner(): GradleRunner =
    GradleRunner.create().withProjectDir(testProjectDir.toFile()).withPluginClasspath().forwardOutput()

  private fun writeFile(relativePath: String, contents: String) {
    val file = testProjectDir.resolve(relativePath)
    file.parent.createDirectories()
    file.writeText(contents)
  }
}
