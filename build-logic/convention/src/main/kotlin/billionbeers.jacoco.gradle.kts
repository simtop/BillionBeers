import org.gradle.internal.extensions.stdlib.capitalized
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    id("jacoco")
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

configure<JacocoPluginExtension> {
    toolVersion = "0.8.11"
}

// In Pure Kotlin Modules, applying id("jacoco") automatically creates a jacocoTestReport task.
// In Android Modules, Applying id("jacoco") does NOT create the jacocoTestReport task
// automatically. You have to create it yourself.
val jacocoTestReport = if (tasks.findByName("jacocoTestReport") != null) {
    tasks.named("jacocoTestReport")
} else {
    tasks.register("jacocoTestReport", JacocoReport::class)
}

val androidComponents = extensions.findByType(com.android.build.api.variant.AndroidComponentsExtension::class.java)
androidComponents?.onVariants { variant ->
    val testTaskName = "test${variant.name.capitalized()}UnitTest"

    val reportTask = tasks.register<JacocoReport>("jacoco${variant.name.capitalize()}Report") {
        dependsOn(testTaskName)

        reports {
            xml.required.set(true)
            html.required.set(true)
        }

        classDirectories.setFrom(
            fileTree("$projectDir/tmp/kotlin-classes/${variant.name}") {
                exclude(
                    "**/R.class",
                    "**/R$*.class",
                    "**/BuildConfig.*",
                    "**/Manifest*.*",
                    "**/*Test*.*",
                    "android/**/*.*",
                    "**/databinding/*",
                    "**/generated/*",
                    "**/model/*",
                    "**/di/*"
                )
            }
        )

        sourceDirectories.setFrom(
            files(
                "$projectDir/src/main/java",
                "$projectDir/src/main/kotlin"
            )
        )

        executionData.setFrom(file("$projectDir/jacoco/$testTaskName.exec"))
    }

    jacocoTestReport.configure { dependsOn(reportTask) }
}
