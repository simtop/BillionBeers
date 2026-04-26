plugins {
    id("com.android.test")
    alias(libs.plugins.androidx.baseline.profile)
}

android {
    namespace = "com.simtop.benchmark.baselineprofile"
    compileSdk = 35

    defaultConfig {
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_23
        targetCompatibility = JavaVersion.VERSION_23
    }

    targetProjectPath = ":app"
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_23)
    }
}

dependencies {
    implementation(libs.benchmark.macro.junit4)
    implementation(libs.uiautomator)
    implementation(libs.junit)
    implementation(libs.testRunner)
    implementation(libs.testRules)
    implementation(libs.testCoreKtx)
    implementation(libs.junitKtx)
}
