import com.simtop.billionbeers.buildlogic.GenerateModuleGraphTask
import org.gradle.api.artifacts.ProjectDependency

check(project == rootProject) { "billionbeers.module.graph must be applied to the root project" }

fun moduleKind(project: Project): String =
  when {
    project.plugins.hasPlugin("com.android.application") -> "android-application"
    project.plugins.hasPlugin("com.android.dynamic-feature") -> "android-dynamic-feature"
    project.plugins.hasPlugin("com.android.test") -> "android-test"
    project.plugins.hasPlugin("com.android.library") -> "android-library"
    project.plugins.hasPlugin("org.jetbrains.kotlin.jvm") -> "jvm-library"
    else -> "other"
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
      val compileClasspath = listOf("debugCompileClasspath", "compileClasspath")
        .firstOrNull { project.configurations.findByName(it) != null }
      if (compileClasspath == null) return@forEach

      val rootComponent = project.configurations
        .getByName(compileClasspath)
        .incoming.resolutionResult.rootComponent
      val declarations = project.configurations.flatMap { configuration ->
        if (!isArchitectureProductionConfiguration(configuration.name)) return@flatMap emptyList()
        configuration.dependencies.withType(ProjectDependency::class.java).mapNotNull { dependency ->
          if (dependency.path == project.path || dependency.path !in modulePaths) return@mapNotNull null
          listOf(project.path, dependency.path, configuration.name).joinToString(separator)
        }
      }.sorted()
      val task = project.tasks.register<VerifyArchitecturePolicyTask>("verifyArchitecturePolicy") {
        group = "verification"
        description = "Verifies this module's resolved compile classpath against the architecture policy."
        projectPath.set(project.path)
        projectRole.set(architecturePolicy.role(project))
        policyFile.set(architecturePolicyFile)
        this.declarations.set(declarations)
        this.moduleRoleRecords.set(moduleRoleRecords)
        this.rootComponent.set(rootComponent)
      }
      verifyArchitectureGraph.configure { dependsOn(task) }
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
