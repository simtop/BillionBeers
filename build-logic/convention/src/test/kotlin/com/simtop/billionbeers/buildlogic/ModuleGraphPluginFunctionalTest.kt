package com.simtop.billionbeers.buildlogic

import groovy.json.JsonSlurper
import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ModuleGraphPluginFunctionalTest {

  @TempDir lateinit var testProjectDir: Path

  @Test
  @Suppress("UNCHECKED_CAST")
  fun `module graph is deterministic merged cycle-aware and self-contained`() {
    writeFixture()

    val first =
      runner()
        .withArguments("generateModuleGraph", "--configuration-cache", "--stacktrace")
        .build()
    assertEquals(TaskOutcome.SUCCESS, first.task(":generateModuleGraph")?.outcome)

    val jsonPath = testProjectDir.resolve("build/reports/module-graph/modules.json")
    val htmlPath = testProjectDir.resolve("build/reports/module-graph/index.html")
    val firstJson = jsonPath.readText()
    val model = JsonSlurper().parseText(firstJson) as Map<*, *>
    val nodes = model["nodes"] as List<Map<String, Any>>
    val edges = model["edges"] as List<Map<String, Any>>
    val cycles = model["cycles"] as List<Map<String, Any>>
    val summary = model["summary"] as Map<*, *>

    assertEquals(1, model["schemaVersion"])
    assertEquals(3, summary["nodeCount"])
    assertEquals(3, summary["edgeCount"])
    assertEquals(1, summary["cycleCount"])
    assertEquals(1, summary["maxFanIn"])
    assertEquals(1, summary["maxFanOut"])
    assertEquals(1, summary["apiProjectEdgeCount"])
    assertEquals(listOf(":a", ":b", ":c"), nodes.map { it["path"] })
    assertEquals(
      listOf(
        mapOf(
          "source" to ":a",
          "target" to ":b",
          "configurations" to listOf("implementation", "testImplementation"),
          "scopes" to listOf("main", "test"),
        ),
        mapOf(
          "source" to ":b",
          "target" to ":c",
          "configurations" to listOf("api"),
          "scopes" to listOf("main"),
        ),
        mapOf(
          "source" to ":c",
          "target" to ":a",
          "configurations" to listOf("implementation"),
          "scopes" to listOf("main"),
        ),
      ),
      edges,
    )
    assertEquals(listOf(mapOf("modules" to listOf(":a", ":b", ":c"))), cycles)
    assertFalse(firstJson.contains(testProjectDir.toAbsolutePath().toString()))
    assertFalse(firstJson.contains("org.example"))

    val html = htmlPath.readText()
    assertTrue(html.contains("<script id=\"graph-data\" type=\"application/json\">$firstJson</script>"))
    assertTrue(html.contains("id=\"module-search\""))
    assertTrue(html.contains("id=\"transitive\""))
    assertTrue(html.contains("id=\"focus\""))
    assertTrue(html.contains("id=\"scope-filters\""))
    assertTrue(html.contains("id=\"cycle-warning\""))
    assertTrue(html.contains("selected.removeClass('hidden-by-filter').addClass('selected-node');"))
    assertTrue(html.contains("Cytoscape.js 3.34.1"))
    assertFalse(Regex("<script[^>]+src=").containsMatchIn(html))

    val second =
      runner()
        .withArguments("generateModuleGraph", "--configuration-cache", "--stacktrace")
        .build()
    assertTrue(second.output.contains("Configuration cache entry reused."))
    assertEquals(firstJson, jsonPath.readText())
  }

  private fun writeFixture() {
    val policy = generateSequence(Path.of(System.getProperty("user.dir"))) { it.parent }
      .map { it.resolve("config/architecture/project-dependency-policy.json") }
      .firstOrNull { it.exists() }
      ?: error("Could not find the checked-in architecture policy")
    testProjectDir.resolve("config/architecture").createDirectories()
    policy.copyTo(
      testProjectDir.resolve("config/architecture/project-dependency-policy.json"),
      overwrite = true,
    )
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
      rootProject.name = "module-graph-fixture"
      include(":a", ":b", ":c")
      """.trimIndent(),
    )
    writeFile(
      "build.gradle.kts",
      """
      plugins {
        id("billionbeers.module.graph")
      }

      subprojects {
        apply(plugin = "java-library")
      }

      project(":a") {
        dependencies {
          add("implementation", project(":b"))
          add("testImplementation", project(":b"))
          add("implementation", "org.example:external:1.0")
        }
      }
      project(":b") {
        dependencies {
          add("api", project(":c"))
        }
      }
      project(":c") {
        dependencies {
          add("implementation", project(":a"))
        }
      }
      """.trimIndent(),
    )
    listOf("a", "b", "c").forEach { module -> writeFile("$module/build.gradle.kts", "") }
  }

  private fun runner(): GradleRunner =
    GradleRunner.create()
      .withProjectDir(testProjectDir.toFile())
      .withPluginClasspath()
      .forwardOutput()

  private fun writeFile(relativePath: String, contents: String) {
    val file = testProjectDir.resolve(relativePath)
    file.parent.createDirectories()
    file.writeText(contents)
  }
}
