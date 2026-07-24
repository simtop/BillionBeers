import com.android.build.api.dsl.TestExtension

plugins {
    id("com.android.test")
    id("billionbeers.android.common")
}

val android = the<TestExtension>()

android.apply {
    namespace = "com.simtop.benchmark.macrobenchmark"
    
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["androidx.benchmark.enabledRules"] = "Macrobenchmark"
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR,DEBUGGABLE,NOT-SELF-INSTRUMENTING"
    }

    // Measure against :app's `benchmark` build type (non-debuggable + minified, the realistic
    // shape), not debug. A com.android.test module matches the target by build-type name, so it must
    // declare its own `benchmark` build type; beforeVariants (below) then disables every other
    // variant so `connectedCheck` runs only the benchmark lane. The app manifest is
    // <profileable android:shell="true"/>, so the non-debuggable target is still measurable.
    buildTypes {
        create("benchmark") {
            // Library modules publish only debug/release, so the benchmark consumer must fall back
            // to release when resolving :app's transitive deps - mirroring :app's own benchmark type.
            matchingFallbacks += "release"
        }
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

// Only the benchmark variant should exist - macrobenchmark measurements on debug are meaningless,
// and leaving debug enabled would make `connectedCheck` run it too.
extensions.configure<com.android.build.api.variant.TestAndroidComponentsExtension> {
    beforeVariants(selector().all()) { variant ->
        variant.enable = variant.buildType == "benchmark"
    }
}

// (The kotlin block is now handled by configureKotlinAndroid)

dependencies {
    "implementation"(libs.benchmark.macro.junit4)
    "implementation"(libs.uiautomator)
    "implementation"(libs.junit)
    "implementation"(libs.testRunner)
    "implementation"(libs.testRules)
    "implementation"(libs.testCoreKtx)
    "implementation"(libs.junitKtx)
}
