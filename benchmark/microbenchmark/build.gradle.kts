import com.android.build.api.dsl.LibraryExtension

plugins {
    id("com.android.library")
    id("billionbeers.android.common")
    alias(libs.plugins.androidx.benchmark)
}

val android = the<LibraryExtension>()

android.apply {
    namespace = "com.simtop.benchmark.microbenchmark"
    
    defaultConfig {
        testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
        testInstrumentationRunnerArguments["androidx.benchmark.enabledRules"] = "Microbenchmark"
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR,DEBUGGABLE,NOT-SELF-INSTRUMENTING"
    }

    testBuildType = "release"
}

// (The kotlin block is now handled by configureKotlinAndroid)

dependencies {
    "implementation"(libs.benchmark.junit4)
    "implementation"(libs.junit)
    "implementation"(libs.testRunner)
    "implementation"(libs.testRules)
    "implementation"(libs.testCoreKtx)
    "implementation"(libs.junitKtx)

    "implementation"(this.project(":beer_data"))
    "implementation"(this.project(":beerdomain:api"))
    "implementation"(this.project(":beer_database"))
    "implementation"(this.project(":beer_network"))
    // BeersMapper's constructor takes LanguageProvider/Logger from core-common.
    "implementation"(this.project(":core-common"))
}
