import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependencyBundle
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

/**
 * Reads the project catalog by its public Gradle API rather than its generated accessor class.
 *
 * Generated `LibrariesForLibs` accessors are compiled into the build-logic classloader, while a
 * TestKit build generates another class with the same name in the fixture classloader. Looking up
 * the catalog through `VersionCatalogsExtension` keeps convention plugins usable in both builds.
 */
fun Project.billionBeersCatalog(): VersionCatalog =
  extensions.getByType<VersionCatalogsExtension>().named("libs")

fun VersionCatalog.billionBeersLibrary(alias: String): Provider<MinimalExternalModuleDependency> =
  findLibrary(alias).orElseThrow()

fun VersionCatalog.billionBeersBundle(alias: String): Provider<ExternalModuleDependencyBundle> =
  findBundle(alias).orElseThrow()

fun VersionCatalog.billionBeersVersion(alias: String): String =
  findVersion(alias).get().requiredVersion
