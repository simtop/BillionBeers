import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

data class ArchitectureDeclaration(
  val source: String,
  val target: String,
  val configuration: String,
)

abstract class VerifyArchitecturePolicyTask : DefaultTask() {

  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val policyFile: RegularFileProperty

  @get:Input abstract val projectPath: Property<String>
  @get:Input abstract val projectRole: Property<String>
  @get:Input abstract val declarations: ListProperty<String>
  @get:Input abstract val moduleRoleRecords: ListProperty<String>

  @get:Internal abstract val rootComponent: Property<ResolvedComponentResult>

  @TaskAction
  fun verify() {
    val policy = ArchitecturePolicy.load(policyFile.get().asFile)
    val source = projectPath.get()
    val sourceRole = projectRole.get()
    val moduleRoles = moduleRoleRecords.get().associate { record ->
      val parts = record.split(RECORD_SEPARATOR)
      require(parts.size == 2) { "Invalid architecture module role record: $record" }
      parts[0] to parts[1]
    }
    check(sourceRole != "unknown") {
      "$source has no architecture role. Add a generic path rule to " +
        "${policyFile.get().asFile.invariantSeparatorsPath}; do not add a feature/package list."
    }

    val violations = mutableListOf<String>()
    declarations.get().map(::parseDeclaration).forEach { declaration ->
      val targetRole = moduleRoles[declaration.target] ?: policy.roleForPath(declaration.target)
      if (targetRole == "unknown") {
        violations += "${declaration.source} -> ${declaration.target}: target has no architecture role"
      } else if (!policy.allows(sourceRole, targetRole, "main")) {
        violations +=
          "${declaration.source} -> ${declaration.target} ($targetRole) via ${declaration.configuration}: " +
            "edge is not allowed for $sourceRole"
      }
      if (targetRole != "unknown" && declaration.configuration.equals("api", ignoreCase = true) &&
        !policy.allowsApi(sourceRole, targetRole)
      ) {
        violations +=
          "${declaration.source} -> ${declaration.target} ($targetRole) via api: " +
            "project API exposure is not listed in allowedApiEdges"
      }
    }

    val resolvedProjectPaths = mutableSetOf<String>()
    val visited = mutableSetOf<ResolvedComponentResult>()
    fun visit(component: ResolvedComponentResult) {
      if (!visited.add(component)) return
      (component.id as? ProjectComponentIdentifier)?.let { resolvedProjectPaths += it.projectPath }
      component.dependencies.filterIsInstance<ResolvedDependencyResult>().forEach { visit(it.selected) }
    }
    visit(rootComponent.get())
    val resolvedRoles = resolvedProjectPaths
      .filter { it != source }
      .associateWith { path -> moduleRoles[path] ?: policy.roleForPath(path) }
    val forbidden = policy.forbiddenRoles(sourceRole)
    resolvedRoles.filterValues { it in forbidden }.forEach { (path, role) ->
      violations +=
        "$source resolves forbidden $role module $path on its compile classpath; " +
          "the dependency is visible transitively and must be removed or moved behind an allowed API"
    }

    check(violations.isEmpty()) {
      "Architecture policy violations for $source ($sourceRole):\n- " + violations.sorted().joinToString("\n- ")
    }
  }

  private fun parseDeclaration(record: String): ArchitectureDeclaration {
    val parts = record.split(RECORD_SEPARATOR)
    require(parts.size == 3) { "Invalid architecture declaration record: $record" }
    return ArchitectureDeclaration(parts[0], parts[1], parts[2])
  }

  private companion object {
    const val RECORD_SEPARATOR = "\u001f"
  }
}
