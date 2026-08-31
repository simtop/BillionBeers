package com.simtop.billionbeers.buildlogic

import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ArchitecturePolicyFunctionalTest {

  @TempDir lateinit var testProjectDir: Path

  @Test
  fun `resolved graph rejects feature to feature`() {
    writeFixture(
      includes = listOf(":feature:alpha", ":feature:beta"),
      dependencies = "project(\":feature:alpha\") { dependencies { add(\"implementation\", project(\":feature:beta\")) } }",
    )

    val result = runner().withArguments(":feature:alpha:verifyArchitecturePolicy").buildAndFail()

    assertTrue(result.output.contains("edge is not allowed"), result.output)
  }

  @Test
  fun `resolved graph rejects feature to data`() {
    writeFixture(
      includes = listOf(":feature:alpha", ":beer_data"),
      dependencies = "project(\":feature:alpha\") { dependencies { add(\"implementation\", project(\":beer_data\")) } }",
    )

    val result = runner().withArguments(":feature:alpha:verifyArchitecturePolicy").buildAndFail()

    assertTrue(result.output.contains("edge is not allowed"), result.output)
  }

  @Test
  fun `resolved graph rejects data exposed transitively through api`() {
    writeFixture(
      includes = listOf(":feature:alpha", ":presentation_utils", ":beer_data"),
      dependencies = """
        project(":feature:alpha") { dependencies { add("implementation", project(":presentation_utils")) } }
        project(":presentation_utils") { dependencies { add("api", project(":beer_data")) } }
      """.trimIndent(),
    )

    val result = runner().withArguments(":feature:alpha:verifyArchitecturePolicy").buildAndFail()

    assertTrue(result.output.contains("resolves forbidden data module :beer_data"), result.output)
  }

  @Test
  fun `resolved graph rejects domain api depending on an Android layer`() {
    writeFixture(
      includes = listOf(":beerdomain:api", ":core"),
      dependencies = "project(\":beerdomain:api\") { dependencies { add(\"implementation\", project(\":core\")) } }",
    )

    val result = runner().withArguments(":beerdomain:api:verifyArchitecturePolicy").buildAndFail()

    assertTrue(result.output.contains("edge is not allowed"), result.output)
  }

  @Test
  fun `resolved graph rejects an unapproved project api exposure`() {
    writeFixture(
      includes = listOf(":core", ":beer_data"),
      dependencies = "project(\":core\") { dependencies { add(\"api\", project(\":beer_data\")) } }",
    )

    val result = runner().withArguments(":core:verifyArchitecturePolicy").buildAndFail()

    assertTrue(result.output.contains("project API exposure is not listed"), result.output)
  }

  private fun writeFixture(includes: List<String>, dependencies: String) {
    val repoPolicy = generateSequence(Path.of(System.getProperty("user.dir"))) { it.parent }
      .map { it.resolve("config/architecture/project-dependency-policy.json") }
      .firstOrNull { it.exists() }
      ?: error("Could not find the checked-in architecture policy")
    testProjectDir.resolve("config/architecture").createDirectories()
    repoPolicy.copyTo(
      testProjectDir.resolve("config/architecture/project-dependency-policy.json"),
      overwrite = true,
    )
    testProjectDir.resolve("settings.gradle.kts").writeText(
      """
        pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
        rootProject.name = "architecture-policy-fixture"
        include(${includes.joinToString(", ") { "\"$it\"" }})
      """.trimIndent(),
    )
    testProjectDir.resolve("build.gradle.kts").writeText(
      """
        plugins { id("billionbeers.module.graph") }
        subprojects { apply(plugin = "java-library") }
        $dependencies
      """.trimIndent(),
    )
    includes.forEach { include ->
      val directory = include.removePrefix(":").replace(":", "/")
      testProjectDir.resolve("$directory/build.gradle.kts").apply {
        parent.createDirectories()
        writeText("")
      }
    }
  }

  private fun runner(): GradleRunner =
    GradleRunner.create()
      .withProjectDir(testProjectDir.toFile())
      .withPluginClasspath()
      .forwardOutput()
}
