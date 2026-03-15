plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    alias(libs.plugins.androidx.benchmark)
}

android {
    namespace = "com.simtop.benchmark.microbenchmark"
    compileSdk = 35

    defaultConfig {
        minSdk = 28
        testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
        testInstrumentationRunnerArguments["androidx.benchmark.enabledRules"] = "Microbenchmark"
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR,DEBUGGABLE,NOT-SELF-INSTRUMENTING"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_23
        targetCompatibility = JavaVersion.VERSION_23
    }

    kotlinOptions {
        jvmTarget = "23"
    }

    testBuildType = "release"
}

dependencies {
    implementation(libs.benchmark.junit4)
    implementation(libs.junit)
    implementation(libs.testRunner)
    implementation(libs.testRules)
    implementation(libs.testCoreKtx)
    implementation(libs.junitKtx)

    implementation(project(":beer_data"))
    implementation(project(":beerdomain:api"))
    implementation(project(":beer_database"))
    implementation(project(":beer_network"))
}
