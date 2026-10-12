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

  private fun runner(): GradleRunner =
    GradleRunner.create().withProjectDir(testProjectDir.toFile()).withPluginClasspath().forwardOutput()

  private fun writeFile(relativePath: String, contents: String) {
    val file = testProjectDir.resolve(relativePath)
    file.parent.createDirectories()
    file.writeText(contents)
  }
}
