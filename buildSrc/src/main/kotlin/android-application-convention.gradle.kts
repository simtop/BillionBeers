// buildSrc/src/main/kotlin/android-application-convention.gradle.kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.kapt") // For Hilt and other annotation processors
    id("org.jetbrains.kotlin.plugin.parcelize")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    // It's crucial to use 'compileSdk' from libs or a defined version
    // Assuming libs.versions.toml has an entry like: androidCompileSdk = "34"
    // For now, hardcoding to 34, but ideally this comes from a version catalog or project property.
    compileSdk = 34 // TODO: Replace with libs.versions.androidCompileSdk.get().toInt() if available
    
    defaultConfig {
        // minSdk and targetSdk should also ideally come from a version catalog
        minSdk = 21 // TODO: Replace with libs.versions.androidMinSdk.get().toInt()
        targetSdk = 34 // TODO: Replace with libs.versions.androidTargetSdk.get().toInt()
        
        testInstrumentationRunner = "com.simtop.billionbeers.di.MockTestRunner" // Consider making this configurable
        multiDexEnabled = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21 // TODO: Use a common Java version variable
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_21.toString()
    }

    buildFeatures {
        compose = true
        viewBinding = true // Common for many modules
    }
    
    packagingOptions {
        resources.excludes.add("/META-INF/{AL2.0,LGPL2.1}")
        // Add other common packaging options if needed
    }
    
    // Common kapt configuration
    kapt {
        correctErrorTypes.set(true)
        generateStubs.set(true) // If this was a common setting
    }
}

dependencies {
    implementation(libs.findLibrary("coreKtx").get())
    implementation(libs.findLibrary("appcompat").get())
    implementation(libs.findLibrary("material").get())
    implementation(libs.findLibrary("constraintlayout").get()) // If commonly used

    // Hilt
    implementation(libs.findLibrary("hiltAndroid").get())
    kapt(libs.findLibrary("hiltCompiler").get())

    // Lifecycle & ViewModel (common for apps)
    implementation(libs.findLibrary("lifecycleRuntimeKtx").get()) // Example, check actual alias
    implementation(libs.findLibrary("lifecycleViewmodelKtx").get()) // Example, check actual alias
    
    // Compose foundational dependencies (examples, verify aliases)
    implementation(libs.findLibrary("composeUi").get())
    implementation(libs.findLibrary("composeMaterial").get()) // Or material3
    implementation(libs.findLibrary("composeUiToolingPreview").get())
    debugImplementation(libs.findLibrary("composeUiTooling").get()) // For debug builds
    
    // Testing
    testImplementation(libs.findLibrary("junit").get())
    androidTestImplementation(libs.findLibrary("androidxTestExtJunit").get()) // Example: androidx.test.ext:junit
    androidTestImplementation(libs.findLibrary("espressoCore").get())
    kaptAndroidTest(libs.findLibrary("hiltCompiler").get()) // Hilt compiler for Android tests
    androidTestImplementation(libs.findLibrary("hiltAndroidTesting").get()) // Hilt testing utilities for Android tests

}
