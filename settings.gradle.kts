pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "BillionBeers"

// Auto-discovers every module instead of requiring a manual include(...) line per module:
// walks the repo tree and includes any directory containing a build.gradle(.kts) file, at any
// depth (e.g. "core/designsystem" -> ":core:designsystem", "benchmark/baselineprofile" ->
// ":benchmark:baselineprofile"). New modules "just work" once they have a build script - no
// settings.gradle.kts edit needed. build-logic is excluded: it's a separate composite build
// (included above via includeBuild), not a subproject of this build.
val excludedDirNames =
    setOf(
        ".git", ".gradle", ".idea", ".kotlin", ".vscode", ".circleci", ".github", ".claude",
        "build", "src", "build-logic", "gradle", "gradle-user-home", "config", "docs", "scripts",
        "profile-out", "brain", "imagesForReadme",
    )

fun File.hasBuildScript() = resolve("build.gradle.kts").exists() || resolve("build.gradle").exists()

fun discoverModules(dir: File) {
    val children =
        dir.listFiles { file -> file.isDirectory && file.name !in excludedDirNames && !file.name.startsWith(".") }
            ?: return
    for (child in children) {
        if (child.hasBuildScript()) {
            val path = child.relativeTo(rootDir).path.replace(File.separatorChar, ':')
            include(":$path")
        }
        discoverModules(child)
    }
}

discoverModules(rootDir)
