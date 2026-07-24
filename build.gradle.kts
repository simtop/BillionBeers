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
        classpath(libs.navigationSafeArgsPlugin)
        classpath(libs.metro.gradle.plugin)
    }

    configurations.all {
        resolutionStrategy {
            force("com.squareup:javapoet:1.13.0")
        }
    }
}

plugins {
    id("org.sonarqube").version("3.2.0")
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

    val subprojectsWithJacoco = subprojects.filter {
        it.plugins.hasPlugin("jacoco") &&
                !it.name.contains("presentation_utils") &&
                !it.name.contains("beer_database")
    }

    val excludes = listOf(
        "**/R.class",
        "**/R$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "android/**/*.*",
        "**/databinding/*",
        "**/generated/*",
        "**/model/*",
        "**/di/*",
        "**/*Activity*.*",
        "**/*Fragment*.*",
        "**/*_MetroGraph*.*",
        "**/Metro_*.*",
        "**/*_Factory*.*",
        "**/*_MembersInjector*.*",
        "**/*MapperImpl*.*",
        "**/*Module*.*",
        "**/*Component*.*",
        "**/*Screen*.*",
        "**/*Application*.*",
        "**/*CommonUiState*.*",
        "**/*Compose*.*",
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