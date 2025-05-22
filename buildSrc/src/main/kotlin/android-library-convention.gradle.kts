// buildSrc/src/main/kotlin/android-library-convention.gradle.kts
plugins {
    id("com.android.library") // Changed from application
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.kapt")
    id("org.jetbrains.kotlin.plugin.parcelize")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    compileSdk = 34 // TODO: Replace with libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        minSdk = 21 // TODO: Replace with libs.versions.androidMinSdk.get().toInt()
        // targetSdk for libraries is often not explicitly set here, taken from compileSdk or consumer
        // but if set, it should align: targetSdk = 34 
        
        testInstrumentationRunner = "com.simtop.billionbeers.di.MockTestRunner" // If common for library tests too
        // No applicationId for libraries
        // multiDexEnabled for libraries is usually not needed at library level
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false // Default for libraries
            // Proguard files for libraries are usually consumer Proguard files
        }
        getByName("debug") {
            // Debug specific settings for libraries if any
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21 // TODO: Use a common Java version variable
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_21.toString()
    }

    buildFeatures {
        compose = true // If compose is commonly used in libraries
        viewBinding = true // If viewBinding is commonly used in libraries
    }
    
    packagingOptions { // Common packaging options for libraries
        resources.excludes.add("/META-INF/{AL2.0,LGPL2.1}")
    }

    kapt {
        correctErrorTypes.set(true)
        generateStubs.set(true) // If this was a common setting
    }
}

dependencies {
    implementation(libs.findLibrary("coreKtx").get())
    // No appcompat or material by default for pure libraries, unless they are UI libraries
    
    // Hilt
    implementation(libs.findLibrary("hiltAndroid").get())
    kapt(libs.findLibrary("hiltCompiler").get())

    // Compose foundational dependencies (if libraries expose UI)
    // Use api() if the library exposes Compose UI, implementation() if for internal use
    api(libs.findLibrary("composeUi").get()) // Example
    api(libs.findLibrary("composeMaterial").get()) // Example
    api(libs.findLibrary("composeUiToolingPreview").get()) // Example
    debugImplementation(libs.findLibrary("composeUiTooling").get()) // Example
    
    // Testing
    testImplementation(libs.findLibrary("junit").get())
    androidTestImplementation(libs.findLibrary("androidxTestExtJunit").get())
    androidTestImplementation(libs.findLibrary("espressoCore").get())
    kaptAndroidTest(libs.findLibrary("hiltCompiler").get())
    androidTestImplementation(libs.findLibrary("hiltAndroidTesting").get())
}
