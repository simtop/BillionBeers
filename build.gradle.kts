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
        classpath (libs.hiltAndroidGradlePlugin)
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

    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    id("jacoco")
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}

tasks.register<JacocoReport>("jacocoRootReport") {
    dependsOn(subprojects.map { it.tasks.withType<Test>() })
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
        "**/*_HiltModules*.*",
        "**/Hilt_*.*",
        "**/*_Factory*.*",
        "**/*_MembersInjector*.*",
        "**/*MapperImpl*.*",
        "**/*Module*.*",
        "**/*Component*.*"
    )

    additionalSourceDirs.setFrom(subprojectsWithJacoco.map { it.extensions.getByType<JacocoPluginExtension>().reportsDirectory })
    sourceDirectories.setFrom(subprojectsWithJacoco.flatMap {
        listOf(
            file("${it.projectDir}/src/main/java"),
            file("${it.projectDir}/src/main/kotlin")
        )
    })
    classDirectories.setFrom(subprojectsWithJacoco.flatMap {
        it.tasks.withType<JacocoReport>().matching { task -> task.name.contains("Debug") }.map { reportTask ->
            reportTask.classDirectories.asFileTree.matching {
                exclude(excludes)
            }
        }
    })
    executionData.setFrom(subprojectsWithJacoco.flatMap {
        it.tasks.withType<JacocoReport>().matching { task -> task.name.contains("Debug") }.map { reportTask ->
            reportTask.executionData
        }
    })

    reports {
        html.required.set(true)
        xml.required.set(false)
        csv.required.set(false)
    }
}