import org.gradle.testing.jacoco.tasks.JacocoReport
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath(libs.androidToolsBuildGradle)
        classpath(libs.kotlinGradlePlugin)
    }

    // Live, despite appearances: javapoet reaches this classpath transitively through AGP and the
    // Room Gradle plugin (confirmed with `./gradlew buildEnvironment`). Note the scope - this is the
    // *buildscript* classpath only, so it does not pin the javapoet that KSP processors resolve.
    configurations.all {
        resolutionStrategy {
            force("com.squareup:javapoet:1.13.0")
        }
    }
}

plugins {
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.com.google.devtools.ksp) apply false


    alias(libs.plugins.metro) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.dependency.guard) apply false
    id("jacoco")
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}

// Resolves the artifacts Android Studio's Gradle sync needs but no build ever asks for, so that
// `make verification-metadata` records them (ADR 0007). Without this the ledger cannot cover them:
// CI has no IDE, so the CI task graph never resolves them, and sync fails one artifact at a time on
// a machine no lane can see.
//
// The gap is localGroovy(), pulled in by `kotlin-dsl` in build-logic. Its jars come from the Gradle
// distribution, so nothing resolves them remotely — but asking for their *sources* forces a real
// Maven Central resolution, which needs metadata the ledger lacks. Version and module list are read
// off the running distribution rather than hardcoded, because the bundled Groovy moves with every
// Gradle upgrade (4.0.29 → 4.0.32 already happened) and a stale pin here reintroduces the bug.
//
// Run it two ways:
//   ./gradlew ideSyncArtifacts                              → fails if the ledger is behind
//   ./gradlew ideSyncArtifacts --write-verification-metadata sha256 → records what's missing
val ideSyncGroovyVersion: String = groovy.lang.GroovySystem.getVersion()
val ideSyncGroovyModules: List<String> =
    (gradle.gradleHomeDir?.resolve("lib")?.listFiles()?.map { it.name } ?: emptyList())
        .filter { it.startsWith("groovy") && it.endsWith("-$ideSyncGroovyVersion.jar") }
        .map { it.removeSuffix("-$ideSyncGroovyVersion.jar") }
        .sorted()

val ideSyncMetadata: Configuration by configurations.creating
val ideSyncSources: Configuration by configurations.creating { isTransitive = false }

ideSyncGroovyModules.forEach { module ->
    val coordinate = "org.apache.groovy:$module:$ideSyncGroovyVersion"
    dependencies.add(ideSyncMetadata.name, coordinate)
    dependencies.add(ideSyncSources.name, "$coordinate:sources@jar")
}

tasks.register("ideSyncArtifacts") {
    description = "Resolves IDE-sync-only artifacts so dependency verification covers them."
    group = "verification"
    // Captured as configuration-cache-friendly values: a Provider for the resolution result and a
    // FileCollection for the artifacts. Referencing the Configuration objects from doLast instead
    // would make the task incompatible with the configuration cache, which is on by default here.
    val version = ideSyncGroovyVersion
    val moduleCount = ideSyncGroovyModules.size
    val rootComponent = ideSyncMetadata.incoming.resolutionResult.rootComponent
    // Lenient: a module without a published sources jar must not fail the check. Metadata is the
    // part the ledger actually needs; the sources jars themselves are covered by the
    // `-sources.jar` trust rule.
    val sourceFiles = ideSyncSources.incoming.artifactView { isLenient = true }.files
    doLast {
        if (moduleCount == 0) {
            error("Found no bundled Groovy jars in the Gradle distribution — the layout changed.")
        }
        val seen = mutableSetOf<String>()
        val queue = ArrayDeque(listOf(rootComponent.get()))
        while (queue.isNotEmpty()) {
            val component = queue.removeFirst()
            if (!seen.add(component.id.displayName)) continue
            component.dependencies.filterIsInstance<ResolvedDependencyResult>()
                .forEach { queue.addLast(it.selected) }
        }
        logger.lifecycle(
            "ideSyncArtifacts: groovy $version, $moduleCount modules, " +
                "${seen.size - 1} components, ${sourceFiles.files.size} sources jars",
        )
    }
}

// Modules deliberately kept out of the aggregate, by exact project path. A module belongs here
// only when the current test tier cannot produce compatible JaCoCo execution data; do not use this
// as a convenient way to improve the headline percentage.
//
// This used to be `it.name.contains("presentation_utils")`, a substring match on the *name*, which
// is fragile twice over: a rename silently drops the exclusion - the module then re-enters the
// aggregate and nobody notices - and `contains` would also swallow any future module whose name
// happens to embed one of these strings. Exact paths plus the check below fail loudly instead.
val coverageExcludedProjects = setOf(":beer_database")

tasks.register<JacocoReport>("jacocoRootReport") {
    // Android debug unit tests + JVM-module `test`. The Android `test` *lifecycle* task aggregates
    // every variant (incl. the non-compiling benchmark one), so it is excluded by only taking `test`
    // from non-Android modules; Android modules contribute via testDebugUnitTest instead.
    dependsOn(
        subprojects.flatMap { sp ->
            sp.tasks.matching { task ->
                val isAndroid =
                    sp.extensions.findByType(
                        com.android.build.api.variant.AndroidComponentsExtension::class.java
                    ) != null
                task.name == "testDebugUnitTest" || (task.name == "test" && !isAndroid)
            }
        }
    )
    dependsOn(subprojects.map { it.tasks.withType<JacocoReport>() })

    val unknownExclusions = coverageExcludedProjects - subprojects.map { it.path }.toSet()
    require(unknownExclusions.isEmpty()) {
        "coverageExcludedProjects names projects that do not exist: $unknownExclusions. " +
            "A module was renamed or removed - update the set rather than leaving a dead entry, " +
            "which would silently pull the module back into the coverage aggregate."
    }

    val subprojectsWithJacoco = subprojects.filter {
        it.plugins.hasPlugin("jacoco") && it.path !in coverageExcludedProjects
    }

    // Exclude generated/framework plumbing only. Screens, activities, DI, models and Compose code
    // are production behavior and must remain visible in the report. UI confidence comes from
    // screenshot/device tests, but removing those classes from line coverage made the aggregate
    // percentage imply more coverage than the repository actually had.
    val excludes = listOf(
        "**/R.class",
        "**/R$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "android/**/*.*",
        "**/databinding/*",
        "**/generated/*",
        "**/*_MetroGraph*.*",
        "**/Metro_*.*",
        "**/*_Factory*.*",
        "**/*_MembersInjector*.*",
        "**/*MapperImpl*.*",
        "**/*.Companion*.*",
        "**/navigation/*"
    )

    additionalSourceDirs.setFrom(subprojectsWithJacoco.map { it.extensions.getByType<JacocoPluginExtension>().reportsDirectory })
    sourceDirectories.setFrom(subprojectsWithJacoco.flatMap {
        listOf(
            file("${it.projectDir}/src/main/java"),
            file("${it.projectDir}/src/main/kotlin")
        )
    })
    // The report holding a module's data: jacocoDebugReport for Android modules, jacocoTestReport
    // for JVM modules. (Android modules also have an unconfigured jacocoTestReport aggregator, which
    // contributes empty class/exec data - harmless.)
    fun org.gradle.api.Project.dataReports() =
        tasks.withType<JacocoReport>().matching { it.name.contains("Debug") || it.name == "jacocoTestReport" }

    classDirectories.setFrom(subprojectsWithJacoco.flatMap {
        it.dataReports().map { reportTask ->
            reportTask.classDirectories.asFileTree.matching {
                exclude(excludes)
            }
        }
    })
    executionData.setFrom(subprojectsWithJacoco.flatMap {
        it.dataReports().map { reportTask -> reportTask.executionData }
    })

    reports {
        html.required.set(true)
        // XML on: the health report (scripts/health-report.sh) and any coverage tooling parse it.
        xml.required.set(true)
        csv.required.set(false)
    }

    doLast {
        val reportPath = reports.html.outputLocation.get().asFile.resolve("index.html")
        println("\n" + "=".repeat(80))
        println("JaCoCo Coverage Report Generated Successfully!")
        println("=".repeat(80))
        println("📊 Report Location: file://${reportPath.absolutePath}")
        println("=".repeat(80) + "\n")
    }
}
