import com.simtop.billionbeers.buildlogic.GenerateModuleGraphTask
import org.gradle.api.artifacts.ProjectDependency

check(project == rootProject) { "billionbeers.module.graph must be applied to the root project" }

fun moduleKind(project: Project): String =
  when {
    project.plugins.hasPlugin("com.android.application") -> "android-application"
    project.plugins.hasPlugin("com.android.dynamic-feature") -> "android-dynamic-feature"
    project.plugins.hasPlugin("com.android.test") -> "android-test"
    project.plugins.hasPlugin("com.android.library") -> "android-library"
    project.plugins.hasPlugin("org.jetbrains.kotlin.multiplatform") -> "kmp-library"
    project.plugins.hasPlugin("org.jetbrains.kotlin.jvm") -> "jvm-library"
    else -> "other"
  }

fun isKmpProject(project: Project): Boolean =
  project.plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")

fun architectureCompileConfigurations(project: Project): List<org.gradle.api.artifacts.Configuration> {
  val targetConfigurations = project.configurations.filter { configuration ->
    val name = configuration.name.lowercase()
    configuration.name.endsWith("MainCompileClasspath", ignoreCase = true) &&
      "test" !in name && "androidtest" !in name && "ksp" !in name
  }
  if (isKmpProject(project)) return targetConfigurations.sortedBy { it.name }
  return listOf("debugCompileClasspath", "compileClasspath")
    .mapNotNull(project.configurations::findByName)
    .take(1)
}

fun dependencyScope(configurationName: String): String {
  val name = configurationName.lowercase()
  return when {
    "benchmark" in name || "baselineprofile" in name || name == "testedapks" -> "benchmark"
    "androidtest" in name -> "androidTest"
    "test" in name -> "test"
    "reversemetadatavalues" in name -> "main"
    listOf(
        "api",
        "implementation",
        "compileonly",
        "runtimeonly",
        "ksp",
        "lintchecks",
        "dynamicfeatures",
      )
      .any { marker -> marker in name } -> "main"
    else -> "tooling/other"
  }
}

fun isArchitectureProductionConfiguration(configurationName: String): Boolean {
  val name = configurationName.lowercase()
  if ("test" in name || "androidtest" in name || name == "ksp" || name.startsWith("ksp")) return false
  return name == "api" || name.endsWith("api") ||
    name == "implementation" || name.endsWith("implementation") ||
    name == "compileonly" || name.endsWith("compileonly") ||
    name == "runtimeonly" || name.endsWith("runtimeonly")
}

val architecturePolicyFile = rootProject.file("config/architecture/project-dependency-policy.json")
check(architecturePolicyFile.isFile) {
  "Missing checked-in architecture policy: ${architecturePolicyFile.invariantSeparatorsPath}"
}

val architecturePolicy = ArchitecturePolicy.load(architecturePolicyFile)
val verifyArchitectureGraph =
  tasks.register("verifyArchitectureGraph") {
    group = "verification"
    description = "Verifies the resolved project dependency graph against the checked-in architecture policy."
  }

val generateModuleGraph =
  tasks.register<GenerateModuleGraphTask>("generateModuleGraph") {
    group = "reporting"
    description = "Generates deterministic JSON and interactive HTML for direct Gradle project dependencies."
    rootProjectName.set(rootProject.name)
    jsonOutput.set(layout.buildDirectory.file("reports/module-graph/modules.json"))
    htmlOutput.set(layout.buildDirectory.file("reports/module-graph/index.html"))
  }

gradle.projectsEvaluated {
  val separator = GenerateModuleGraphTask.RECORD_SEPARATOR
  val modules = rootProject.subprojects.filter { it.buildFile.isFile }.sortedBy { it.path }
  val modulePaths = modules.map { it.path }.toSet()
  generateModuleGraph.configure {
    nodeRecords.set(
      modules.map { project ->
        listOf(
            project.path,
            project.name,
            rootProject.relativePath(project.projectDir),
            moduleKind(project),
          )
          .joinToString(separator)
      }
    )
    edgeRecords.set(
      modules
        .flatMap { project ->
          project.configurations.flatMap { configuration ->
            configuration.dependencies.withType(ProjectDependency::class.java).mapNotNull { dependency ->
              if (dependency.path == project.path || dependency.path !in modulePaths) return@mapNotNull null
              listOf(
                  project.path,
                  dependency.path,
                  configuration.name,
                  dependencyScope(configuration.name),
                )
                .joinToString(separator)
            }
          }
        }
        .sorted()
    )
    includedBuildNames.set(gradle.includedBuilds.map { it.name }.sorted())
  }

    val moduleRoleRecords = modules.map { project ->
      listOf(project.path, architecturePolicy.role(project)).joinToString(separator)
    }
    modules.forEach { project ->
      val compileConfigurations = architectureCompileConfigurations(project)
      check(compileConfigurations.isNotEmpty() || !isKmpProject(project)) {
        "${project.path} applies Kotlin Multiplatform but has no production *MainCompileClasspath " +
          "configuration for architecture verification"
      }
      if (compileConfigurations.isEmpty()) return@forEach

      val checks = compileConfigurations.map { configuration ->
        val configurationName = configuration.name
        val rootComponent = configuration.incoming.resolutionResult.rootComponent
        val declarations = project.configurations.flatMap { declarationConfiguration ->
          if (!isArchitectureProductionConfiguration(declarationConfiguration.name)) return@flatMap emptyList()
          declarationConfiguration.dependencies
            .withType(ProjectDependency::class.java)
            .mapNotNull { dependency ->
              if (dependency.path == project.path || dependency.path !in modulePaths) return@mapNotNull null
              listOf(project.path, dependency.path, declarationConfiguration.name).joinToString(separator)
            }
        }.sorted()
        val taskName = "verifyArchitecturePolicy${configurationName.replaceFirstChar { it.uppercase() }}"
        project.tasks.register<VerifyArchitecturePolicyTask>(taskName) {
          group = "verification"
          description =
            "Verifies this module's resolved $configurationName against the architecture policy."
          projectPath.set(project.path)
          projectRole.set(architecturePolicy.role(project))
          policyFile.set(architecturePolicyFile)
          this.configurationName.set(configurationName)
          this.declarations.set(declarations)
          this.moduleRoleRecords.set(moduleRoleRecords)
          this.rootComponent.set(rootComponent)
        }
      }
      val aggregate = project.tasks.register("verifyArchitecturePolicy") {
        group = "verification"
        description = "Verifies this module's resolved compile graphs against the architecture policy."
        dependsOn(checks)
      }
      verifyArchitectureGraph.configure { dependsOn(aggregate) }
    }
    if (tasks.findByName("check") == null) {
      tasks.register("check") {
        group = "verification"
        dependsOn(verifyArchitectureGraph)
      }
    } else {
      tasks.named("check") { dependsOn(verifyArchitectureGraph) }
    }
}
