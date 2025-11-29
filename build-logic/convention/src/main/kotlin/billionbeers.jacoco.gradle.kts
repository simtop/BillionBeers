import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    id("jacoco")
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

configure<JacocoPluginExtension> {
    toolVersion = "0.8.11"
}

val jacocoTestReport = tasks.create("jacocoTestReport")

val androidComponents = extensions.findByType(com.android.build.api.variant.AndroidComponentsExtension::class.java)
androidComponents?.onVariants { variant ->
    val testTaskName = "test${variant.name.capitalize()}UnitTest"

    val reportTask = tasks.register<JacocoReport>("jacoco${variant.name.capitalize()}Report") {
        dependsOn(testTaskName)

        reports {
            xml.required.set(true)
            html.required.set(true)
        }

        classDirectories.setFrom(
            fileTree("$buildDir/tmp/kotlin-classes/${variant.name}") {
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

        executionData.setFrom(file("$buildDir/jacoco/$testTaskName.exec"))
    }

    jacocoTestReport.dependsOn(reportTask)
}
