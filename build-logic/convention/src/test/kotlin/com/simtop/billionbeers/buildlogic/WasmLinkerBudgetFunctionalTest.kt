package com.simtop.billionbeers.buildlogic

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class WasmLinkerBudgetFunctionalTest {

  @TempDir lateinit var testProjectDir: Path

  @Test
  fun `Wasm links in different projects do not overlap with parallel execution enabled`() {
    writeFile(
      "settings.gradle.kts",
      """
      rootProject.name = "wasm-budget-test"
      include(":first", ":second")
      dependencyResolutionManagement { repositories { mavenCentral() } }
      """.trimIndent(),
    )
    writeFile(
      "build.gradle.kts",
      """
      import com.simtop.billionbeers.buildlogic.configureWasmLinkerBudget
      import java.util.concurrent.atomic.AtomicInteger
      import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrLink

      plugins { id("billionbeers.kmp.verification") }

      project.configureWasmLinkerBudget()

      val activeLinks = AtomicInteger()
      gradle.taskGraph.beforeTask {
        if (this is KotlinJsIrLink) {
          check(activeLinks.incrementAndGet() == 1) { "Wasm links overlapped" }
          Thread.sleep(200)
        }
      }
      gradle.taskGraph.afterTask {
        if (this is KotlinJsIrLink) activeLinks.decrementAndGet()
      }
      """.trimIndent(),
    )
    for (module in listOf("first", "second")) {
      writeFile(
        "$module/build.gradle.kts",
        """
        plugins { id("org.jetbrains.kotlin.multiplatform") }
        kotlin {
          wasmJs { browser() }
          sourceSets.commonTest.dependencies { implementation(kotlin("test")) }
        }
        """.trimIndent(),
      )
      writeFile(
        "$module/src/commonTest/kotlin/ExampleTest.kt",
        """
        import kotlin.test.Test
        import kotlin.test.assertEquals
        class ExampleTest {
          @Test fun example() { assertEquals(2, 1 + 1) }
        }
        """.trimIndent(),
      )
    }

    val result = GradleRunner.create()
      .withProjectDir(testProjectDir.toFile())
      .withPluginClasspath()
      .withArguments(
        ":first:compileTestDevelopmentExecutableKotlinWasmJs",
        ":second:compileTestDevelopmentExecutableKotlinWasmJs",
        "--parallel", "--max-workers=2", "--no-configuration-cache", "--stacktrace",
      )
      .forwardOutput()
      .build()

    for (module in listOf("first", "second")) {
      assertEquals(TaskOutcome.SUCCESS, result.task(":$module:compileTestDevelopmentExecutableKotlinWasmJs")?.outcome)
    }
  }

  private fun writeFile(path: String, text: String) {
    val file = testProjectDir.resolve(path)
    file.parent.createDirectories()
    file.writeText(text)
  }
}
