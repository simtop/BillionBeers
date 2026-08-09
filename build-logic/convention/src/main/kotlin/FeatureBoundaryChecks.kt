import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register

/**
 * A feature module (regular or on-demand dynamic) reaches persistence and the network only through
 * the repository interfaces in `:beerdomain:api` - invariant 13, AGENTS.md. `FeatureDataLayerBoundaryTest`
 * (`:konsist`) enforces the direct half of that: it reads each feature's build script text for a
 * `project(":beer_data")`-shaped declaration. What it structurally cannot see is one of those
 * modules arriving *indirectly* - declared by something the feature depends on, rather than by the
 * feature's own build script. Source-level text scanning has no view of the resolved graph.
 *
 * This closes that gap by resolving the module's own `debugCompileClasspath` at task-execution
 * time and failing if a forbidden module shows up anywhere in it, direct or transitive.
 *
 * Verified empirically, not assumed: the case this actually catches is a data-layer module arriving
 * through an intermediate dependency's `api(...)` declaration - the type is then genuinely visible
 * to the feature's compiler, so it is a real violation, and one `FeatureDataLayerBoundaryTest` would
 * miss if the feature module's own build script never names the data-layer module directly. An
 * `implementation(...)` declaration on the intermediate module does *not* trip this check, and
 * correctly so: Gradle's own configuration elision already keeps an `implementation` dependency off
 * a consumer's compile classpath, so the type was never resolvable in the feature's code to begin
 * with - there is nothing for this task, or anything else, to catch there.
 *
 * `debugCompileClasspath`, not `debugRuntimeClasspath`, is the deliberate choice: dynamic-feature
 * modules declare `implementation(project(":app"))` to install correctly, and `:app` itself depends
 * on all three data-layer modules (assembling the graph is its exempted job). `:app` declares them
 * on `implementation`, so ordinary Gradle configuration elision already keeps them off a consumer's
 * *compile* classpath - only `:app`'s `api` dependencies would leak through. Checking the runtime
 * classpath instead would flag that structurally-required edge as a violation, not a real one.
 *
 * Reads the resolved graph through `resolutionResult.rootComponent`, not `resolutionResult.allComponents`
 * directly - the latter needs a live `Configuration`/`Project` at task-execution time, which the
 * configuration cache forbids. `rootComponent` is the `Provider<ResolvedComponentResult>` Gradle
 * built for exactly this: safe to capture at configuration time, resolved lazily, serializable
 * across a configuration-cache boundary.
 *
 * Registration happens inside `afterEvaluate`: AGP creates `debugCompileClasspath` while it builds
 * its variants, which is *after* the convention plugin script - applying `com.android.library` /
 * `com.android.dynamic-feature` does not make the configuration exist yet - so `configurations.named`
 * called any earlier throws `Configuration with name 'debugCompileClasspath' not found`. Same
 * pattern the existing `UnusedDependenciesPlugin` uses for the same reason.
 */
fun Project.registerDataLayerClasspathBoundaryCheck() {
  afterEvaluate {
    val debugCompileClasspath = configurations.named("debugCompileClasspath")
    val rootComponent = debugCompileClasspath.flatMap { it.incoming.resolutionResult.rootComponent }

    val checkTask =
      tasks.register<DataLayerClasspathBoundaryTask>("checkDataLayerClasspathBoundary") {
        group = "verification"
        description =
          "Fails if this module's resolved debugCompileClasspath includes a data-layer module, " +
            "even transitively (invariant 13, AGENTS.md)."
        this.projectPath.set(path)
        this.forbiddenModules.set(setOf(":beer_data", ":beer_database", ":beer_network"))
        this.rootComponent.set(rootComponent)
      }

    tasks.named("check") { dependsOn(checkTask) }
  }
}

abstract class DataLayerClasspathBoundaryTask : DefaultTask() {

  @get:Input abstract val projectPath: Property<String>

  @get:Input abstract val forbiddenModules: SetProperty<String>

  // Not @Input: a dependency graph isn't a hashable value, it's a lazily-resolved object graph.
  // The task has no declared outputs, so Gradle never considers it up-to-date and it runs every
  // time regardless - the usual shape for a verification task with nothing to cache.
  @get:Internal abstract val rootComponent: Property<ResolvedComponentResult>

  @TaskAction
  fun verify() {
    val resolvedProjectPaths = mutableSetOf<String>()
    val visited = mutableSetOf<ResolvedComponentResult>()

    fun visit(component: ResolvedComponentResult) {
      if (!visited.add(component)) return
      (component.id as? ProjectComponentIdentifier)?.let { resolvedProjectPaths += it.projectPath }
      component.dependencies.filterIsInstance<ResolvedDependencyResult>().forEach {
        visit(it.selected)
      }
    }
    visit(rootComponent.get())

    val violations = forbiddenModules.get().intersect(resolvedProjectPaths)
    check(violations.isEmpty()) {
      "${projectPath.get()} resolves data-layer module(s) $violations on debugCompileClasspath - " +
        "a feature reaches persistence and the network only through the :beerdomain:api " +
        "repository interfaces, whose implementations :app binds (invariant 13, AGENTS.md)."
    }
  }
}
