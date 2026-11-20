package com.simtop.billionbeers.buildlogic

import java.io.File
import java.io.IOException
import java.io.Serializable
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.UnresolvedDependencyResult
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

private val importPattern = Regex("""^\s*import\s+(?:static\s+)?([\w.]+)""")

internal fun packageMatchesImport(packageName: String, importPath: String): Boolean =
  importPath == packageName || importPath.startsWith("$packageName.")

internal fun dependencyPackages(file: File): Set<String> = buildSet {
  ZipFile(file).use { archive ->
    val entries = archive.entries().asSequence()
    when (file.extension) {
      "jar" -> entries.mapNotNull { classPackage(it.name) }.forEach { add(it) }
      "aar" ->
        entries
          .filter { it.name == "classes.jar" || (it.name.startsWith("libs/") && it.name.endsWith(".jar")) }
          .forEach { embeddedJar ->
            ZipInputStream(archive.getInputStream(embeddedJar)).use { jar ->
              generateSequence { jar.nextEntry }.mapNotNull { classPackage(it.name) }.forEach { add(it) }
            }
          }
    }
  }
}

private fun classPackage(entry: String): String? =
  entry.takeIf { it.endsWith(".class") && !it.startsWith("META-INF/") }
    ?.substringBeforeLast('/', missingDelimiterValue = "")
    ?.takeIf(String::isNotEmpty)
    ?.replace('/', '.')

data class DependencyArtifact(
  @get:Input val coordinate: String,
  @get:InputFile @get:Optional @get:PathSensitive(PathSensitivity.NONE) val file: File?,
) : Serializable

@DisableCachingByDefault(because = "Reports dependency evidence to the console")
abstract class DetectUnusedDependenciesTask : DefaultTask() {
  @get:Input abstract val moduleName: Property<String>

  @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val sourceFiles: ConfigurableFileCollection

  @get:Nested abstract val artifacts: ListProperty<DependencyArtifact>

  @TaskAction
  fun detectUnusedDependencies() {
    val imports = sourceFiles.files.flatMap { file ->
      file.useLines { lines ->
        lines.mapNotNull { importPattern.find(it)?.groupValues?.get(1)?.trimEnd('.') }.toList()
      }
    }.toSet()

    val unassessed = mutableListOf<String>()
    val candidates = mutableListOf<String>()
    var assessed = 0
    artifacts.get().groupBy { it.coordinate }.toSortedMap().forEach { (coordinate, evidence) ->
      val packages =
        try {
          if (evidence.any { it.file == null }) emptySet()
          else evidence.flatMapTo(mutableSetOf()) { dependencyPackages(checkNotNull(it.file)) }
        } catch (error: IOException) {
          logger.info("Could not read artifact for $coordinate", error)
          emptySet()
        }

      if (packages.isEmpty()) {
        unassessed.add(coordinate)
      } else {
        assessed++
        if (imports.none { imported -> packages.any { packageMatchesImport(it, imported) } }) {
          candidates.add(coordinate)
        }
      }
    }

    logger.lifecycle("Dependency import check for ${moduleName.get()} (${imports.size} source imports)")
    logger.lifecycle("Assessed $assessed direct external dependencies using resolved JARs and AARs.")
    logger.lifecycle("No matching source import (${candidates.size}):")
    candidates.forEach { logger.lifecycle("  $it") }
    logger.lifecycle("Unassessed because class evidence was missing (${unassessed.size}):")
    unassessed.forEach { logger.lifecycle("  $it") }
    logger.lifecycle(
      "Review candidates manually. Imports miss resources, generated code, reflection, and runtime use."
    )
  }
}

class UnusedDependenciesPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    listOf("com.android.application", "com.android.library", "com.android.dynamic-feature")
      .forEach { pluginId -> project.plugins.withId(pluginId) { registerTask(project) } }
  }

  private fun registerTask(project: Project) {
    project.tasks.register("detectUnusedDependencies", DetectUnusedDependenciesTask::class.java) {
      group = "verification"
      description = "Finds direct external compile dependencies without matching source imports"
      moduleName.set(project.path)
      sourceFiles.from(project.fileTree("src") { include("**/*.kt", "**/*.java") })
      artifacts.set(
        project.configurations
          .filter {
            it.isCanBeResolved &&
              (it.name == "compileClasspath" || it.name.endsWith("CompileClasspath"))
          }
          .flatMap(::directArtifacts)
          .distinct()
          .sortedWith(compareBy({ it.coordinate }, { it.file?.path })),
      )
    }
  }
}

private fun directArtifacts(configuration: Configuration): List<DependencyArtifact> {
  val incoming = configuration.incoming
  val dependencies = incoming.resolutionResult.root.dependencies.filterNot { it.isConstraint }
  val modules = dependencies.filterIsInstance<ResolvedDependencyResult>()
    .filter { it.selected.id is ModuleComponentIdentifier }
    .map { dependency ->
      // Gradle metadata can point a common coordinate at a separate Android or JVM artifact.
      val variant = generateSequence(dependency.resolvedVariant) { it.externalVariant.orElse(null) }.last()
      dependency.selected.id.displayName to variant.owner
    }

  val owners = modules.map { (_, owner) -> owner }.toSet()
  val files = incoming.artifactView {
    componentFilter { it in owners }
    isLenient = true
  }.artifacts.artifacts.groupBy({ it.id.componentIdentifier }, { it.file })

  val unresolved = dependencies.filterIsInstance<UnresolvedDependencyResult>()
    .map { DependencyArtifact(it.requested.displayName, null) }
  return unresolved + modules.flatMap { (coordinate, owner) ->
    val archives = files[owner].orEmpty().filter { it.extension in setOf("jar", "aar") }
    if (archives.isEmpty()) listOf(DependencyArtifact(coordinate, null))
    else archives.map { DependencyArtifact(coordinate, it) }
  }
}
