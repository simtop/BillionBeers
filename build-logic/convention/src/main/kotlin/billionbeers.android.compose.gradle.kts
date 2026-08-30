import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.DynamicFeatureExtension

plugins {
    id("org.jetbrains.kotlin.plugin.compose")
}

val libs = billionBeersCatalog()

composeCompiler {
    // Classes the compiler cannot infer as stable but that we know are: see the file's header
    // for the contract each entry must uphold. Applies to every compose module via this plugin.
    stabilityConfigurationFiles.add(
        rootProject.layout.projectDirectory.file("compose-stability.conf")
    )

    // Metrics (module-level JSON) + reports (per-class stability, per-composable skippability)
    // are opt-in because they add compiler work on every build. `make compose-metrics` sets the
    // property; output lands in <module>/build/compose_compiler/.
    if (providers.gradleProperty("composeCompilerReports").isPresent) {
        metricsDestination.set(layout.buildDirectory.dir("compose_compiler"))
        reportsDestination.set(layout.buildDirectory.dir("compose_compiler"))
    }
}

pluginManager.withPlugin("com.android.base") {
    dependencies {
        "implementation"(platform(libs.billionBeersLibrary("androidxComposeBom")))
        "androidTestImplementation"(platform(libs.billionBeersLibrary("androidxComposeBom")))
        
        "implementation"(libs.billionBeersLibrary("androidxActivityCompose"))
        "implementation"(libs.billionBeersLibrary("androidx-foundation-android"))
        "implementation"(libs.billionBeersLibrary("androidx-material3-android"))
        "implementation"(libs.billionBeersLibrary("androidx-compose-material-icons-core"))
        "implementation"(libs.billionBeersLibrary("androidx-ui-tooling-preview-android"))
        "implementation"(libs.billionBeersLibrary("androidx-runtime-livedata"))
        "implementation"(libs.billionBeersLibrary("metrox-viewmodel-compose"))
    }
}
