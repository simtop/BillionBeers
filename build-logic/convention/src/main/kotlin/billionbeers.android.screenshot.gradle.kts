import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.DynamicFeatureExtension
import com.android.build.api.dsl.LibraryExtension
import java.io.File
import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("app.cash.paparazzi")
    id("com.google.devtools.ksp")
}

val libs = the<LibrariesForLibs>()

dependencies {
    add("implementation", project(":snapshot-testing"))
    add("ksp", project(":snapshot-processor"))
}

// Paparazzi 2.0.0-alpha04's PaparazziTestReporter is wired into the Test
// task's HTML/Junit report generators and calls a Gradle 8.x internal method
// (TestResultProvider.hasOutput) that no longer exists in Gradle 9.4.
// This makes the build fail at report-generation even when the test itself passed and
// Images were recorded successfully. Disabling both reports because anyway we usually check
// the reports in build/reports/paparazzi.
tasks.withType<Test>().configureEach {
    reports.html.required.set(false)
    reports.junitXml.required.set(false)
}

val namespaceProvider = provider {
    project.extensions.findByType(com.android.build.api.dsl.CommonExtension::class.java)?.namespace ?: project.name
}

val generatePaparazziTest = tasks.register("generatePaparazziTest") {
    val outputDir = layout.buildDirectory.dir("generated/paparazzi-test/kotlin")
    outputs.dir(outputDir)
    
    val nsProvider = namespaceProvider
    doLast {
        val capturedModuleNamespace = nsProvider.get()
        val safeClassName = capturedModuleNamespace.replace(".", "_").replaceFirstChar { it.uppercase() } + "ScreenshotTest"
        
        val outDirFile = outputDir.get().asFile
        val packageDir = File(outDirFile, "com/simtop/billionbeers/screenshot")
        packageDir.mkdirs()
        
        val testFile = File(packageDir, "${safeClassName}.kt")
        testFile.writeText("""
            package com.simtop.billionbeers.screenshot

            import app.cash.paparazzi.DeviceConfig
            import app.cash.paparazzi.Paparazzi
            import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme
            import com.simtop.billionbeers.snapshot_testing.PreviewProvider
            import org.junit.Assume
            import org.junit.Rule
            import org.junit.Test
            import org.junit.runner.RunWith
            import org.junit.runners.Parameterized
            import java.util.ServiceLoader

            @RunWith(Parameterized::class)
            class ${safeClassName}(
                private val snapshotName: String,
                private val content: @androidx.compose.runtime.Composable () -> Unit
            ) {

                @get:Rule
                val paparazzi = Paparazzi(
                    deviceConfig = DeviceConfig.PIXEL_5,
                    theme = "android:Theme.Material.Light.NoActionBar"
                )

                @Test
                fun snapshot() {
                    Assume.assumeTrue("Skipping dummy snapshot", snapshotName != "DUMMY_NO_PREVIEWS_FOUND")
                    
                    paparazzi.snapshot(name = snapshotName) {
                        BillionBeersTheme {
                            content()
                        }
                    }
                }

                companion object {
                    @JvmStatic
                    @Parameterized.Parameters(name = "{0}")
                    fun data(): Collection<Array<Any>> {
                        val allProviders = ServiceLoader.load(PreviewProvider::class.java).toList()
                        
                        val namespace = "$capturedModuleNamespace"
                        val localProviders = allProviders.filter { provider ->
                            provider::class.java.name.startsWith(namespace)
                        }
                        
                        val allSnapshots = localProviders.flatMap { it.snapshots }
                        
                        if (allSnapshots.isEmpty()) {
                            return listOf(arrayOf("DUMMY_NO_PREVIEWS_FOUND", @androidx.compose.runtime.Composable {}))
                        }

                        return allSnapshots.map { snapshot ->
                            arrayOf(snapshot.name, snapshot.content)
                        }
                    }
                }
            }
        """.trimIndent())
    }
}

pluginManager.withPlugin("com.android.application") {
    extensions.configure<ApplicationExtension>() {
        sourceSets.getByName("test").java.srcDir(generatePaparazziTest)
        sourceSets.getByName("test").resources.srcDir(layout.buildDirectory.dir("generated/ksp/debug/resources"))
    }
}

pluginManager.withPlugin("com.android.library") {
    extensions.configure<LibraryExtension>(){
        sourceSets.getByName("test").java.srcDir(generatePaparazziTest)
        sourceSets.getByName("test").resources.srcDir(layout.buildDirectory.dir("generated/ksp/debug/resources"))
    }
}

pluginManager.withPlugin("com.android.dynamic-feature") {
    extensions.configure<DynamicFeatureExtension>(){
        sourceSets.getByName("test").java.srcDir(generatePaparazziTest)
        sourceSets.getByName("test").resources.srcDir(layout.buildDirectory.dir("generated/ksp/debug/resources"))
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    if (name.contains("Test", ignoreCase = true)) {
        source(generatePaparazziTest)
    }
}

// Fix implicit dependency for KSP
tasks.matching { it.name.contains("ksp", ignoreCase = true) && it.name.contains("UnitTest", ignoreCase = true) }.configureEach {
    dependsOn(generatePaparazziTest)
}



// Isolate ordinary tests from Paparazzi screenshot tests entirely
tasks.withType<Test>().configureEach {
    val isPaparazziRun = project.gradle.startParameter.taskNames.any { 
        it.contains("Paparazzi", ignoreCase = true) 
    }
    
    filter {
        isFailOnNoMatchingTests = false
        if (isPaparazziRun) {
            // Paparazzi task strictly executes the auto-generated Screenshot runner
            includeTestsMatching("com.simtop.billionbeers.screenshot.*")
        } else {
            // Standard Android/JVM test runs skip the Paparazzi runner
            excludeTestsMatching("com.simtop.billionbeers.screenshot.*")
        }
    }
}

