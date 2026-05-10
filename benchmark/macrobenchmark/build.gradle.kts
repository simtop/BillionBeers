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

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
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
