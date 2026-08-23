import org.gradle.internal.extensions.stdlib.capitalized
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension

plugins {
    id("jacoco")
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

configure<JacocoPluginExtension> {
    toolVersion = PROJECT_JACOCO_VERSION
}

// Coverage is owned by the variant-API report tasks below; the junit5 plugin's own jacoco
// integration is redundant and calls the legacy libraryVariants API, which AGP 10 removes.
pluginManager.withPlugin("de.mannodermaus.android-junit5") {
    extensions.configure<de.mannodermaus.gradle.plugins.junit5.dsl.AndroidJUnitPlatformExtension>("junitPlatform") {
        jacocoOptions { taskGenerationEnabled.set(false) }
    }
}

tasks.withType<Test>().configureEach {
    extensions.configure<JacocoTaskExtension> {
        val jdkAndToolExcludes = listOf(
            "jdk.internal.*",
            "sun.*",
            "com.sun.*",
            "javax.*",
            "org.junit.*",
            "org.gradle.*",
            "org.jacoco.*"
        )
        excludes = (excludes ?: emptyList()) + jdkAndToolExcludes
    }
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
// Coverage is measured on the debug variant only. Registering a report for every variant made
// jacoco<Variant>Report depend on a test<Variant>UnitTest task that doesn't exist for the AndroidX
// baseline-profile variants (benchmarkRelease, nonMinifiedRelease), breaking jacocoRootReport at
// configuration time. Scoping to debug is both the fix and the coverage convention.
val debugVariants = androidComponents?.selector()?.withBuildType("debug")
if (androidComponents != null && debugVariants != null) androidComponents.onVariants(debugVariants) { variant ->
    val testTaskName = "test${variant.name.capitalized()}UnitTest"

    val reportTask = tasks.register<JacocoReport>("jacoco${variant.name.capitalize()}Report") {
        dependsOn(testTaskName)

        reports {
            xml.required.set(true)
            html.required.set(true)
        }

        // AGP 9's built-in kotlinc writes classes under build/intermediates/built_in_kotlinc/...,
        // not the old build/tmp/kotlin-classes/ this hardcoded (and it dropped the build/ segment
        // too). Take the class output straight from the compile task so the path follows AGP
        // wherever it moves it, and so the report gets an implicit dependency on compilation.
        classDirectories.setFrom(
            tasks.named("compile${variant.name.capitalized()}Kotlin").map { task ->
                task.outputs.files.asFileTree.matching {
                    exclude(
                        "**/R.class",
                        "**/R$*.class",
                        "**/BuildConfig.*",
                        "**/Manifest*.*",
                        "**/*Test*.*",
                        "android/**/*.*",
                        "**/databinding/*",
                        "**/generated/*",
                        "**/*_HiltModules*.*",
                        "**/Hilt_*.*",
                        "**/*_Factory*.*",
                        "**/*_MembersInjector*.*",
                        "**/*MapperImpl*.*"
                    )
                }
            }
        )

        sourceDirectories.setFrom(
            files(
                "$projectDir/src/main/java",
                "$projectDir/src/main/kotlin"
            )
        )

        // Test tasks write coverage to build/jacoco/, not $projectDir/jacoco/ - the missing
        // build/ segment meant this report (and the aggregated root report) had no execution data
        // and produced empty coverage.
        executionData.setFrom(layout.buildDirectory.file("jacoco/$testTaskName.exec"))
    }

    jacocoTestReport.configure { dependsOn(reportTask) }
}
