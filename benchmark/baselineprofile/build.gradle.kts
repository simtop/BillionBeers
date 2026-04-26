import com.android.build.api.dsl.TestExtension

plugins {
    id("com.android.test")
    id("billionbeers.android.common")
    alias(libs.plugins.androidx.baseline.profile)
}

val android = the<TestExtension>()

android.apply {
    namespace = "com.simtop.benchmark.baselineprofile"
    
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"
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
