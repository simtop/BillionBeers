import groovy.json.JsonSlurper
import java.io.File
import org.gradle.api.Project

data class ArchitectureRoleRule(
  val name: String,
  val match: String,
  val value: String,
  val plugin: String?,
)

data class ArchitectureAllowedEdge(
  val source: String,
  val targets: Set<String>,
  val scopes: Set<String>,
)

data class ArchitectureApiEdge(
  val source: String,
  val target: String,
  val reason: String,
)

data class ArchitecturePolicy(
  val schemaVersion: Int,
  val roles: List<ArchitectureRoleRule>,
  val allowedEdges: List<ArchitectureAllowedEdge>,
  val allowedApiEdges: List<ArchitectureApiEdge>,
  val forbiddenCompileClasspathRoles: Map<String, Set<String>>,
) {

  fun role(project: Project): String =
    role(
      project.path,
      buildSet {
        if (project.plugins.hasPlugin("com.android.dynamic-feature")) {
          add("com.android.dynamic-feature")
        }
      },
    )

  fun role(path: String, plugins: Set<String> = emptySet()): String =
    roles.firstOrNull { rule ->
      val matchesPath =
        when (rule.match) {
          "exact" -> path == rule.value
          "prefix" -> path.startsWith(rule.value)
          "suffix" -> path.endsWith(rule.value)
          else -> error("Unknown architecture role match '${rule.match}'")
        }
      matchesPath && (rule.plugin == null || rule.plugin in plugins)
    }?.name ?: "unknown"

  fun roleForPath(path: String): String =
    roles.firstOrNull { rule ->
      rule.plugin == null && when (rule.match) {
        "exact" -> path == rule.value
        "prefix" -> path.startsWith(rule.value)
        "suffix" -> path.endsWith(rule.value)
        else -> false
      }
    }?.name ?: "unknown"

  fun allows(source: String, target: String, scope: String): Boolean =
    allowedEdges.any {
      it.source == source && target in it.targets && scope in it.scopes
    }

  fun allowsApi(source: String, target: String): Boolean =
    allowedApiEdges.any { it.source == source && it.target == target }

  fun forbiddenRoles(source: String): Set<String> = forbiddenCompileClasspathRoles[source].orEmpty()

  companion object {
    fun load(file: File): ArchitecturePolicy {
      val root = JsonSlurper().parse(file) as? Map<*, *>
        ?: error("Architecture policy must be a JSON object: ${file.path}")
      val schemaVersion = (root["schemaVersion"] as Number).toInt()
      require(schemaVersion == 1) { "Unsupported architecture policy schemaVersion=$schemaVersion" }
      return ArchitecturePolicy(
        schemaVersion = schemaVersion,
        roles = (root["roles"] as List<*>).map { value ->
          val item = value as Map<*, *>
          ArchitectureRoleRule(
            name = item.string("name"),
            match = item.string("match"),
            value = item.string("value"),
            plugin = item["plugin"] as String?,
          )
        },
        allowedEdges = (root["allowedEdges"] as List<*>).map { value ->
          val item = value as Map<*, *>
          ArchitectureAllowedEdge(
            source = item.string("source"),
            targets = item.strings("targets").toSet(),
            scopes = item.strings("scopes").toSet(),
          )
        },
        allowedApiEdges = (root["allowedApiEdges"] as List<*>).map { value ->
          val item = value as Map<*, *>
          ArchitectureApiEdge(
            source = item.string("source"),
            target = item.string("target"),
            reason = item.string("reason"),
          )
        },
        forbiddenCompileClasspathRoles =
          (root["forbiddenCompileClasspathRoles"] as List<*>).associate { value ->
            val item = value as Map<*, *>
            item.string("source") to item.strings("targets").toSet()
          },
      )
    }

    private fun Map<*, *>.string(key: String): String = get(key) as? String
      ?: error("Architecture policy field '$key' must be a string")

    private fun Map<*, *>.strings(key: String): List<String> = (get(key) as? List<*>)?.map {
      it as? String ?: error("Architecture policy field '$key' must contain strings")
    } ?: error("Architecture policy field '$key' must be an array")
  }
}

fun Project.architectureRole(policy: ArchitecturePolicy): String = policy.role(this)
