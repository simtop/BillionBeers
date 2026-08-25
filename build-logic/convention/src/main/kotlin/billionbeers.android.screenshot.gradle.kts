import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.DynamicFeatureExtension
import com.android.build.api.dsl.LibraryExtension
import java.io.File
import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("app.cash.paparazzi")
}

val libs = the<LibrariesForLibs>()
val inventoryPathProvider = providers.gradleProperty("billionbeers.screenshot.inventory")

dependencies {
    add("implementation", this.project(":snapshot-testing"))
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
val cpsDiscoveryEnabledProvider =
    providers.gradleProperty("billionbeers.screenshot.cpsDiscovery")
        .map(String::toBoolean)
        .orElse(
            providers.provider {
                project.extensions.extraProperties.properties["billionbeers.screenshot.cpsDiscovery"]
                    ?.toString()
                    ?.toBoolean()
                    ?: true
            },
        )

val discoveryBackendProvider =
    providers.gradleProperty("billionbeers.screenshot.discovery")
        .map(String::lowercase)
        .orElse("cps")

require(discoveryBackendProvider.get() in setOf("cps", "ksp")) {
    "billionbeers.screenshot.discovery must be cps or ksp"
}

if (discoveryBackendProvider.get() == "ksp") {
    pluginManager.apply("com.google.devtools.ksp")
}

// Resolve only the selected discovery backend. CPS remains the default; KSP is deliberately
// opt-in so normal CI continues to exercise the runtime scanner until the comparison is complete.
project.afterEvaluate {
    if (!cpsDiscoveryEnabledProvider.get()) return@afterEvaluate
    if (discoveryBackendProvider.get() == "cps") {
        dependencies.add("testImplementation", libs.composablePreviewScannerAndroid)
        dependencies.add("testImplementation", libs.classgraph)
    }
}

project.afterEvaluate {
    if (discoveryBackendProvider.get() == "ksp" && cpsDiscoveryEnabledProvider.get()) {
        dependencies.add("ksp", project.project(":snapshot-processor"))
    }
}

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
    inputs.property("cpsDiscoveryEnabled", cpsDiscoveryEnabledProvider)
    inputs.property("discoveryBackend", discoveryBackendProvider.get())

    // The module namespace is the task's only input, and it must be declared. Without it the task
    // had outputs and no inputs, so Gradle held it UP-TO-DATE forever once the file existed.
    //
    // That is not cosmetic. The namespace is baked into the generated runner as its class name
    // and as the package tree passed to CPS. Renaming a module's namespace left the stale class in
    // place, so the runner could scan the wrong package and report an empty inventory.
    // Measured before this fix: changing :core:designsystem's namespace regenerated nothing and
    // the file kept the old value.
    inputs.property("moduleNamespace", namespaceProvider.get())

    val nsProvider = namespaceProvider
    val cpsProvider = cpsDiscoveryEnabledProvider
    val backendProvider = discoveryBackendProvider
    doLast {
        val outDirFile = outputDir.get().asFile
        // Wipe first: the class name is derived from the namespace, so regenerating after a rename
        // writes a *new* file and would otherwise leave the old one beside it - a second runner
        // class still filtering on the dead prefix, which compiles and asserts nothing.
        outDirFile.deleteRecursively()
        if (!cpsProvider.get()) return@doLast

        val capturedModuleNamespace = nsProvider.get()
        val backend = backendProvider.get()
        val safeClassName = capturedModuleNamespace.replace(".", "_").replaceFirstChar { it.uppercase() } + "ScreenshotTest"
        val packageDir = File(outDirFile, "com/simtop/billionbeers/screenshot")
        packageDir.mkdirs()
        
        val testFile = File(packageDir, "${safeClassName}.kt")
        if (backend == "ksp") {
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
            return@doLast
        }
        testFile.writeText("""
            package com.simtop.billionbeers.screenshot

            import app.cash.paparazzi.DeviceConfig
            import app.cash.paparazzi.Paparazzi
            import com.android.resources.LayoutDirection
            import com.android.resources.NightMode
            import com.android.resources.UiMode
            import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme
            import com.simtop.billionbeers.snapshot_testing.AccessibilityMatrix
            import com.simtop.billionbeers.snapshot_testing.PreviewConfiguration
            import com.simtop.billionbeers.snapshot_testing.SnapshotImageEnvironment
            import java.io.File
            import org.junit.Rule
            import org.junit.Test
            import org.junit.runner.RunWith
            import org.junit.runners.Parameterized
            import sergio.sastre.composable.preview.scanner.android.AndroidComposablePreviewScanner
            import sergio.sastre.composable.preview.scanner.android.AndroidPreviewInfo
            import sergio.sastre.composable.preview.scanner.android.screenshotid.AndroidPreviewScreenshotIdBuilder
            import sergio.sastre.composable.preview.scanner.core.preview.ComposablePreview
            import sergio.sastre.composable.preview.scanner.core.scanner.config.classpath.Classpath

            private const val MODULE_NAMESPACE = "$capturedModuleNamespace"
            private const val MATRIX_ANNOTATION =
                "com.simtop.billionbeers.core.designsystem.component.AccessibilityMatrixPreview"
            private const val CUSTOM_PREVIEW_PACKAGE =
                "com.simtop.billionbeers.core.designsystem.component"
            private val COMPILED_MAIN_CLASSES_BY_VARIANT =
                mapOf(
                    "debug" to "build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes",
                    "release" to "build/intermediates/built_in_kotlinc/release/compileReleaseKotlin/classes",
                )

            private data class PreviewCase(
                val snapshotName: String,
                val preview: ComposablePreview<AndroidPreviewInfo>,
                val configuration: PreviewConfiguration,
            )

            @RunWith(Parameterized::class)
            class ${safeClassName}(
                private val snapshotName: String,
                private val preview: ComposablePreview<AndroidPreviewInfo>,
                private val configuration: PreviewConfiguration,
            ) {

                @get:Rule
                val paparazzi = Paparazzi(
                    deviceConfig = deviceConfig(configuration),
                    theme = if (configuration.theme == "dark") {
                        "android:Theme.Material.NoActionBar"
                    } else {
                        "android:Theme.Material.Light.NoActionBar"
                    }
                )

                @Test
                fun snapshot() {
                    paparazzi.snapshot(name = snapshotName) {
                        SnapshotImageEnvironment {
                            BillionBeersTheme {
                                preview()
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
                        val cases = discoverCases()
                        check(cases.isNotEmpty()) {
                            "CPS discovered no previews for " + MODULE_NAMESPACE +
                                "; expected compiled classes under " +
                                COMPILED_MAIN_CLASSES_BY_VARIANT.values.joinToString()
                        }
                        check(cases.map { it.snapshotName }.toSet().size == cases.size) {
                            "CPS produced duplicate screenshot IDs for " + MODULE_NAMESPACE
                        }
                        writeInventory(cases)
                        return cases.map { case ->
                            arrayOf<Any>(case.snapshotName, case.preview, case.configuration)
                        }
                    }

                    private fun writeInventory(cases: List<PreviewCase>) {
                        val path = System.getProperty("billionbeers.screenshot.inventory") ?: return
                        val file = File(path)
                        file.parentFile?.mkdirs()
                        file.writeText(
                            cases.joinToString(separator = "") { case ->
                                listOf(
                                    MODULE_NAMESPACE,
                                    case.snapshotName,
                                    case.configuration.theme,
                                    case.configuration.fontScale,
                                    case.configuration.locale,
                                    case.configuration.layoutDirection,
                                    case.configuration.width,
                                ).joinToString("\t") + "\n"
                            },
                        )
                    }

                    private fun discoverCases(): List<PreviewCase> {
                        val preferredVariant =
                            if (System.getProperty("java.class.path").orEmpty().contains("releaseUnitTest")) {
                                "release"
                            } else {
                                "debug"
                            }
                        val compiledMainClasses =
                            listOf(preferredVariant, if (preferredVariant == "debug") "release" else "debug")
                                .mapNotNull { COMPILED_MAIN_CLASSES_BY_VARIANT[it] }
                                .firstOrNull { File(it).isDirectory }
                                ?: error(
                                    "CPS compiled preview scan requires one of " +
                                        COMPILED_MAIN_CLASSES_BY_VARIANT.values.joinToString() +
                                        "; compile the module's debug or release Kotlin sources first"
                                )
                        val scanner =
                            AndroidComposablePreviewScanner()
                                .setTargetSourceSet(
                                    Classpath(compiledMainClasses, File(".").absolutePath),
                                    packageTreesOfCrossModuleCustomPreviews =
                                        listOf(CUSTOM_PREVIEW_PACKAGE),
                                )
                        val filter =
                            scanner
                                .scanPackageTrees(MODULE_NAMESPACE)
                                .includePrivatePreviews()
                        filter.includeAnnotationInfoForAllOf(
                            Class.forName(MATRIX_ANNOTATION).asSubclass(Annotation::class.java)
                        )
                        val previews =
                            filter
                                .getPreviews()
                                .distinctBy { AndroidPreviewScreenshotIdBuilder(it).build() }
                                .sortedWith(
                                    compareBy<ComposablePreview<AndroidPreviewInfo>>(
                                        { it.methodName },
                                        { if (isDark(it)) 1 else 0 },
                                        { it.previewIndex ?: -1 },
                                        { AndroidPreviewScreenshotIdBuilder(it).build() },
                                    )
                                )
                        val usedNames = mutableSetOf<String>()
                        val cases =
                            previews.flatMap { preview ->
                                if (isAccessibilityMatrix(preview)) {
                                    AccessibilityMatrix.configurations.map { configuration ->
                                        PreviewCase(
                                            preview.methodName + "_" + configuration.name,
                                            preview,
                                            configuration,
                                        )
                                    }
                                } else {
                                    val name = ordinaryName(preview, usedNames)
                                    listOf(PreviewCase(name, preview, previewConfiguration(name, preview)))
                                }
                            }
                        val matrixPreviewCount = previews.count(::isAccessibilityMatrix)
                        val matrixCaseCount = cases.count { isAccessibilityMatrix(it.preview) }
                        check(matrixCaseCount == matrixPreviewCount * AccessibilityMatrix.configurations.size) {
                            "CPS accessibility matrix expansion mismatch for " + MODULE_NAMESPACE +
                                ": expected " + matrixPreviewCount * AccessibilityMatrix.configurations.size +
                                ", got " + matrixCaseCount
                        }
                        return cases
                    }

                    private fun isAccessibilityMatrix(preview: ComposablePreview<AndroidPreviewInfo>): Boolean =
                        preview.otherAnnotationsInfo?.any { it.name == MATRIX_ANNOTATION } == true

                    private fun isDark(preview: ComposablePreview<AndroidPreviewInfo>): Boolean =
                        preview.previewInfo.uiMode and 0x30 == 0x20

                    private fun ordinaryName(
                        preview: ComposablePreview<AndroidPreviewInfo>,
                        usedNames: MutableSet<String>,
                    ): String {
                        val parameterSuffix =
                            if (preview.methodParametersType.isNotBlank()) {
                                preview.previewIndexDisplayName.orEmpty().ifBlank {
                                    preview.previewIndex?.toString() ?: "0"
                                }
                            } else {
                                ""
                            }
                        val baseName =
                            if (parameterSuffix.isEmpty()) {
                                preview.methodName
                            } else {
                                preview.methodName + "_" + parameterSuffix
                            }
                        if (usedNames.add(baseName)) return baseName

                        val info = preview.previewInfo
                        val suffix = buildList {
                            if (isDark(preview)) add("dark")
                            if (info.fontScale != 1f) add("font" + (info.fontScale * 100).toInt())
                            if (info.locale.isNotBlank()) add(info.locale.replace('-', '_'))
                            if (info.device.isNotBlank()) add("device")
                        }.ifEmpty {
                            listOf(preview.previewIndex?.toString() ?: "variant")
                        }.joinToString("_")
                        val candidate = baseName + "_" + suffix
                        if (usedNames.add(candidate)) return candidate
                        return candidate + "_" + AndroidPreviewScreenshotIdBuilder(preview).build().hashCode()
                    }

                    private fun previewConfiguration(
                        name: String,
                        preview: ComposablePreview<AndroidPreviewInfo>,
                    ): PreviewConfiguration {
                        val info = preview.previewInfo
                        val width =
                            if (info.widthDp >= 600 || info.device.orEmpty().contains("TABLET", ignoreCase = true)) {
                                "expanded"
                            } else {
                                "compact"
                            }
                        return PreviewConfiguration(
                            name = name,
                            theme = if (isDark(preview)) "dark" else "light",
                            fontScale = info.fontScale,
                            locale = info.locale.orEmpty().ifBlank { "en" },
                            layoutDirection = "ltr",
                            width = width,
                        )
                    }
                }
            }
        """.trimIndent())
    }
}

// AGP 9 forbids passing Providers to AndroidSourceSet.srcDir (android.sourceset.disallowProvider),
// so the generated dirs are registered as plain paths. Task dependencies are wired explicitly
// below: KotlinCompile tasks source(generatePaparazziTest) and unit-test resource processing depends on
// any KSP task used by the module's other conventions.
val generatedPaparazziTestDir = layout.buildDirectory.dir("generated/paparazzi-test/kotlin").get().asFile
val generatedKspTestResourcesDir = layout.buildDirectory.dir("generated/ksp/debug/resources").get().asFile

pluginManager.withPlugin("com.android.application") {
    extensions.configure<ApplicationExtension>() {
        sourceSets.getByName("test").java.directories.add(generatedPaparazziTestDir.path)
        sourceSets.getByName("test").resources.directories.add(generatedKspTestResourcesDir.path)
    }
}

pluginManager.withPlugin("com.android.library") {
    extensions.configure<LibraryExtension>(){
        sourceSets.getByName("test").java.directories.add(generatedPaparazziTestDir.path)
        sourceSets.getByName("test").resources.directories.add(generatedKspTestResourcesDir.path)
    }
}

pluginManager.withPlugin("com.android.dynamic-feature") {
    extensions.configure<DynamicFeatureExtension>(){
        sourceSets.getByName("test").java.directories.add(generatedPaparazziTestDir.path)
        sourceSets.getByName("test").resources.directories.add(generatedKspTestResourcesDir.path)
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

// KSP-generated resources from other module conventions are wired into every unit-test resource
// source set. Release and benchmark unit-test resource processing therefore needs the debug KSP task
// explicitly when a module applies such a convention.
tasks.matching {
    it.name.startsWith("process") &&
        it.name.contains("UnitTest", ignoreCase = true) &&
        it.name.endsWith("JavaRes")
}.configureEach {
    dependsOn(tasks.matching { it.name == "kspDebugKotlin" })
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
