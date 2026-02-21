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

project.extensions.findByType(com.android.build.gradle.BaseExtension::class.java)?.let { android ->
    val outputDir = layout.buildDirectory.dir("generated/paparazzi-test/kotlin").get().asFile
    android.sourceSets.getByName("test").java.srcDir(outputDir)
}
    
project.afterEvaluate {
    val ext = project.extensions.findByType(com.android.build.api.dsl.CommonExtension::class.java)
    val capturedModuleNamespace = ext?.namespace ?: "com.simtop.unknown"

    val generatePaparazziTest by tasks.registering {
        val outputDir = layout.buildDirectory.dir("generated/paparazzi-test/kotlin")
        outputs.dir(outputDir)
        
        val safeClassName = capturedModuleNamespace.replace(".", "_").replaceFirstChar { it.uppercase() } + "ScreenshotTest"
        
        doLast {
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

    // Ensure the generate task runs before compilation and KSP to satisfy Gradle inputs
    tasks.matching { it.name.contains("ksp", ignoreCase = true) || it.name.contains("compile", ignoreCase = true) }.configureEach {
        if (name.contains("Test", ignoreCase = true)) {
            dependsOn(generatePaparazziTest)
        }
    }
}



// Isolate ordinary tests from Paparazzi screenshot tests entirely
tasks.withType<Test>().configureEach {
    val isPaparazziRun = project.gradle.startParameter.taskNames.any { 
        it.contains("Paparazzi", ignoreCase = true) 
    }
    
    if (isPaparazziRun) {
        // Paparazzi task strictly executes the auto-generated Screenshot runner
        filter {
            includeTestsMatching("com.simtop.billionbeers.screenshot.*")
        }
    } else {
        // Standard Android/JVM test runs skip the Paparazzi runner
        filter {
            excludeTestsMatching("com.simtop.billionbeers.screenshot.*")
        }
    }
}

