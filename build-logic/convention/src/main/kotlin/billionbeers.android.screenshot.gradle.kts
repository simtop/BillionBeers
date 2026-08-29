import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.DynamicFeatureExtension
import com.android.build.api.dsl.LibraryExtension
import java.io.File

plugins {
    id("app.cash.paparazzi")
    id("com.google.devtools.ksp")
}
val inventoryPathProvider = providers.gradleProperty("billionbeers.screenshot.inventory")

dependencies {
    add("implementation", this.project(":snapshot-testing"))
    add("ksp", this.project(":snapshot-processor"))
}

// Paparazzi's plugin calls Test.setTestReporter(PaparazziTestReporter) on every Test task in the
// module - not just during record/verify - and that reporter's ClassPageRenderer calls the Gradle 8
// internal TestResultsProvider.hasOutput, gone in Gradle 9.4. Report generation then fails a build
// whose tests passed, so the HTML report stays off; the Paparazzi one in build/reports/paparazzi is
// the one worth reading anyway.
//
// JUnit XML is NOT affected and must stay on. Gradle generates it through Binary2JUnitXmlReport-
// Generator, a path setTestReporter does not touch - the reporter it replaces is the HTML one only.
// Disabling it here was collateral damage, and it cost more than the bug: it is the only
// machine-readable record a test run leaves, so CI's unit-test-reports artifact was empty for the
// seven screenshot-enabled modules and "the tests passed" could not be verified from CI output.
tasks.withType<Test>().configureEach {
    reports.html.required.set(false)
    reports.junitXml.required.set(true)
    inventoryPathProvider.orNull?.let { path ->
        systemProperty("billionbeers.screenshot.inventory", path)
    }
}

val namespaceProvider = provider {
    project.extensions.findByType(com.android.build.api.dsl.CommonExtension::class.java)?.namespace ?: project.name
}

// A module may own handwritten Paparazzi tests without owning Compose @Preview functions. The
// catalog is intentionally such a module: its one screenshot is a direct Paparazzi test, not a
// preview inventory. Keep the opt-out explicit rather than turning an empty inventory into a
// silently green generated runner.
val previewDiscoveryEnabledProvider =
    providers.gradleProperty("billionbeers.screenshot.previewDiscovery")
        .map(String::toBoolean)
        .orElse(
            providers.provider {
                project.extensions.extraProperties.properties["billionbeers.screenshot.previewDiscovery"]
                    ?.toString()
                    ?.toBoolean()
                    ?: true
            },
        )

project.afterEvaluate {
    pluginManager.withPlugin("com.google.devtools.ksp") {
        extensions.configure<com.google.devtools.ksp.gradle.KspExtension> {
            arg("billionbeers.screenshot.namespace", namespaceProvider.get())
        }
    }
}

val generatePaparazziTest = tasks.register("generatePaparazziTest") {
    val outputDir = layout.buildDirectory.dir("generated/paparazzi-test/kotlin")
    outputs.dir(outputDir)
    inputs.property("previewDiscoveryEnabled", previewDiscoveryEnabledProvider)

    // The module namespace is the task's only input, and it must be declared. Without it the task
    // had outputs and no inputs, so Gradle held it UP-TO-DATE forever once the file existed.
    //
    // That is not cosmetic. The namespace is baked into the generated runner as its class name
    // and as the import path of the generated KSP inventory. Renaming a module's namespace left the
    // stale class in place, so the runner could import the wrong inventory.
    // Measured before this fix: changing :core:designsystem's namespace regenerated nothing and
    // the file kept the old value.
    inputs.property("moduleNamespace", namespaceProvider.get())

    val nsProvider = namespaceProvider
    val previewDiscoveryProvider = previewDiscoveryEnabledProvider
    doLast {
        val outDirFile = outputDir.get().asFile
        // Wipe first: the class name is derived from the namespace, so regenerating after a rename
        // writes a *new* file and would otherwise leave the old one beside it - a second runner
        // class still filtering on the dead prefix, which compiles and asserts nothing.
        outDirFile.deleteRecursively()
        if (!previewDiscoveryProvider.get()) return@doLast

        val capturedModuleNamespace = nsProvider.get()
        val safeClassName = capturedModuleNamespace.replace(".", "_").replaceFirstChar { it.uppercase() } + "ScreenshotTest"
        val packageDir = File(outDirFile, "com/simtop/billionbeers/screenshot")
        packageDir.mkdirs()
        
        val testFile = File(packageDir, "${safeClassName}.kt")
        testFile.writeText("""
                package com.simtop.billionbeers.screenshot

                import app.cash.paparazzi.DeviceConfig
                import app.cash.paparazzi.Paparazzi
                import com.android.resources.LayoutDirection
                import com.android.resources.NightMode
                import com.android.resources.UiMode
                import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme
                import com.simtop.billionbeers.snapshot_testing.PreviewConfiguration
                import com.simtop.billionbeers.snapshot_testing.SnapshotImageEnvironment
                import ${capturedModuleNamespace}.GeneratedPreviewInventory
                import java.io.File
                import org.junit.Rule
                import org.junit.Test
                import org.junit.runner.RunWith
                import org.junit.runners.Parameterized

                private const val MODULE_NAMESPACE = "$capturedModuleNamespace"

                @RunWith(Parameterized::class)
                class ${safeClassName}(
                    private val snapshotName: String,
                    private val content: @androidx.compose.runtime.Composable () -> Unit,
                    private val configuration: PreviewConfiguration,
                ) {

                    @get:Rule
                    val paparazzi = Paparazzi(
                        deviceConfig = deviceConfig(configuration),
                        theme = if (configuration.theme == "dark") {
                            "android:Theme.Material.NoActionBar"
                        } else {
                            "android:Theme.Material.Light.NoActionBar"
                        },
                    )

                    @Test
                    fun snapshot() {
                        paparazzi.snapshot(name = snapshotName) {
                            SnapshotImageEnvironment {
                                BillionBeersTheme {
                                    content()
                                }
                            }
                        }
                    }

                    private fun deviceConfig(configuration: PreviewConfiguration): DeviceConfig {
                        val base = if (configuration.width == "expanded") {
                            DeviceConfig.PIXEL_TABLET
                        } else {
                            DeviceConfig.PIXEL_5
                        }
                        return base.copy(
                            fontScale = configuration.fontScale,
                            locale = configuration.locale,
                            layoutDirection = if (configuration.layoutDirection == "rtl") {
                                LayoutDirection.RTL
                            } else {
                                LayoutDirection.LTR
                            },
                            uiMode = UiMode.NORMAL,
                            nightMode = if (configuration.theme == "dark") {
                                NightMode.NIGHT
                            } else {
                                NightMode.NOTNIGHT
                            },
                        )
                    }

                    companion object {
                        @JvmStatic
                        @Parameterized.Parameters(name = "{0}")
                        fun data(): Collection<Array<Any>> {
                            val snapshots = GeneratedPreviewInventory.snapshots
                            check(snapshots.isNotEmpty()) {
                                "KSP discovered no previews for " + MODULE_NAMESPACE
                            }
                            check(snapshots.map { it.name }.toSet().size == snapshots.size) {
                                "KSP produced duplicate screenshot IDs for " + MODULE_NAMESPACE
                            }
                            writeInventory(snapshots)
                            return snapshots.map { snapshot ->
                                arrayOf<Any>(snapshot.name, snapshot.content, snapshot.configuration)
                            }
                        }

                        private fun writeInventory(snapshots: List<com.simtop.billionbeers.snapshot_testing.Snapshot>) {
                            val path = System.getProperty("billionbeers.screenshot.inventory") ?: return
                            val file = File(path)
                            file.parentFile?.mkdirs()
                            file.writeText(
                                snapshots.joinToString(separator = "") { snapshot ->
                                    listOf(
                                        MODULE_NAMESPACE,
                                        snapshot.name,
                                        snapshot.configuration.theme,
                                        snapshot.configuration.fontScale,
                                        snapshot.configuration.locale,
                                        snapshot.configuration.layoutDirection,
                                        snapshot.configuration.width,
                                        snapshot.configuration.previewName,
                                        snapshot.configuration.previewGroup,
                                        snapshot.configuration.device,
                                    ).joinToString("\t") + "\n"
                                },
                            )
                        }
                    }
                }
        """.trimIndent())
    }
}

// AGP 9 forbids passing Providers to AndroidSourceSet.srcDir (android.sourceset.disallowProvider),
// so the generated dirs are registered as plain paths. Task dependencies are wired explicitly
// below: KotlinCompile tasks source(generatePaparazziTest).
val generatedPaparazziTestDir = layout.buildDirectory.dir("generated/paparazzi-test/kotlin").get().asFile

pluginManager.withPlugin("com.android.application") {
    extensions.configure<ApplicationExtension>() {
        sourceSets.getByName("test").java.directories.add(generatedPaparazziTestDir.path)
    }
}

pluginManager.withPlugin("com.android.library") {
    extensions.configure<LibraryExtension>(){
        sourceSets.getByName("test").java.directories.add(generatedPaparazziTestDir.path)
    }
}

pluginManager.withPlugin("com.android.dynamic-feature") {
    extensions.configure<DynamicFeatureExtension>(){
        sourceSets.getByName("test").java.directories.add(generatedPaparazziTestDir.path)
    }
}

// UnitTest, not Test: the generated runner is a Paparazzi (JVM-only) test and belongs solely to the
// unit-test compilation. Matching "Test" also matched compileDebugAndroidTestKotlin, which fed the
// generated file into the *instrumented* compile - where Paparazzi is not on the classpath. That was
// latent only because no screenshot-enabled module had an androidTest source set; the first one to
// add one failed with "Unresolved reference 'Paparazzi'" before this task had any chance to run.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    if (name.contains("UnitTest", ignoreCase = true)) {
        source(generatePaparazziTest)
    }
}

// Fix implicit dependencies for generated test sources. Kotlin already declares the relationship
// through source(), but AGP's unit-test lint model discovers the same directory through the Android
// source set and validates it independently.
tasks.matching {
    it.name.contains("UnitTest", ignoreCase = true) &&
        (it.name.contains("ksp", ignoreCase = true) || it.name.contains("lint", ignoreCase = true))
}.configureEach {
    dependsOn(generatePaparazziTest)
}

// Isolate ordinary tests from Paparazzi screenshot tests entirely
tasks.withType<Test>().configureEach {
    val isPaparazziRun = project.gradle.startParameter.taskNames.any { 
        it.contains("Paparazzi", ignoreCase = true) 
    }
    
    // A `--tests` filter that matches nothing must fail, or a typo'd filter reads as a green run -
    // the bug that forced a mutation-probe to prove a test had executed at all. Restoring it
    // unconditionally is not an option: with the excludeTestsMatching below and no `--tests`, a
    // module holding only screenshot tests (:core:designsystem) legitimately runs zero tests, and
    // Gradle fails exactly that with "No tests found for given includes:" (empty list and all).
    // So it tracks `--tests`, which is stock Gradle behaviour for the filter the developer typed.
    // The per-task filter's commandLineIncludePatterns would be more precise, but it lives on the
    // internal DefaultTestFilter - and an internal-API dependency is what broke the reports above.
    //
    // Consequence, and it is the correct one: a project-wide `./gradlew testDebugUnitTest --tests
    // "*Foo*"` now fails these modules when they hold no match. Every module that does not apply
    // this plugin already did - :app, :beer_data, :beer_network and :navigation all fail that way -
    // so this ends a silent exemption rather than creating a new sharp edge. Scope the filter to a
    // module (`make test MODULE=:feature:beerslist`) as before.
    val hasCommandLineTestFilter = project.gradle.startParameter.taskRequests
        .any { request -> request.args.any { it == "--tests" } }

    filter {
        isFailOnNoMatchingTests = hasCommandLineTestFilter
        if (isPaparazziRun) {
            // Paparazzi task strictly executes the auto-generated Screenshot runner
            includeTestsMatching("com.simtop.billionbeers.screenshot.*")
        } else {
            // Standard Android/JVM test runs skip the Paparazzi runner
            excludeTestsMatching("com.simtop.billionbeers.screenshot.*")
        }
    }
}
