import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ScopedArtifacts
import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Exports absolute paths to local compiled release inputs")
abstract class ExportDependencyConsumers : DefaultTask() {
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.ABSOLUTE)
  abstract val jars: ListProperty<RegularFile>

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.ABSOLUTE)
  abstract val directories: ListProperty<Directory>

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.ABSOLUTE)
  abstract val projectJars: ListProperty<RegularFile>

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.ABSOLUTE)
  abstract val projectDirectories: ListProperty<Directory>

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.ABSOLUTE)
  abstract val platformJars: ListProperty<RegularFile>

  @get:Input abstract val modulePath: Property<String>
  @get:OutputFile abstract val manifest: RegularFileProperty

  @TaskAction
  fun export() {
    val rows = jars.get().map { it.asFile } + directories.get().map { it.asFile }.filter { it.exists() }
    val projectRows = projectJars.get().map { it.asFile } + projectDirectories.get().map { it.asFile }.filter { it.exists() }
    require(rows.isNotEmpty()) { "No compiled release classes for ${modulePath.get()}" }
    require(projectRows.isNotEmpty()) { "No compiled project classes for ${modulePath.get()}" }
    val output = manifest.get().asFile
    output.parentFile.mkdirs()
    output.writeText(
      rows.sortedBy { it.absolutePath }.joinToString("") {
        "consumer\t${modulePath.get()}\t${it.absolutePath}\n"
      } + projectRows.joinToString("") {
        "project\t${modulePath.get()}\t${it.absolutePath}\n"
      } + platformJars.get().joinToString("") { "platform\tandroid\t${it.asFile.absolutePath}\n" }
    )
  }
}

fun org.gradle.api.Project.registerDependencyLinkageInputs() {
  val components = extensions.getByType(AndroidComponentsExtension::class.java)
  components.onVariants(components.selector().withBuildType("release")) { variant ->
    val export = tasks.register("exportReleaseDependencyConsumers", ExportDependencyConsumers::class.java) {
      modulePath.set(project.path)
      platformJars.set(components.sdkComponents.bootClasspath)
      manifest.set(layout.buildDirectory.file("dependency-linkage/consumers.tsv"))
    }
    variant.artifacts.forScope(ScopedArtifacts.Scope.ALL).use(export).toGet(
      ScopedArtifact.CLASSES, ExportDependencyConsumers::jars, ExportDependencyConsumers::directories
    )
    variant.artifacts.forScope(ScopedArtifacts.Scope.PROJECT).use(export).toGet(
      ScopedArtifact.CLASSES, ExportDependencyConsumers::projectJars, ExportDependencyConsumers::projectDirectories
    )
  }
}
