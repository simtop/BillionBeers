plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.parcelize")
    id("androidx.navigation.safeargs.kotlin") // Using .kotlin suffix
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.billionbeers"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.billionbeers"
        minSdk = 21 // Changed from minSdkVersion
        targetSdk = 34 // Changed from targetSdkVersion
        versionCode = 51
        versionName = "0.51"
        testInstrumentationRunner = "com.simtop.billionbeers.di.MockTestRunner"
        multiDexEnabled = true
    }

    dynamicFeatures = mutableSetOf(":feature:beerdetail") // Use mutableSetOf for KTS

    buildFeatures {
        compose = true
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_21.toString()
    }
}

// Call Groovy closure from common.gradle
// This assumes 'androidModule' is an extension property on rootProject of type Closure
@Suppress("UNCHECKED_CAST")
val androidModule = rootProject.ext["androidModule"] as groovy.lang.Closure<Any>
androidModule.call(true)

dependencies {
    implementation(project(":beerdomain"))
    implementation(project(":feature:beerslist"))
    implementation(project(":core"))
    implementation(project(":beer_data"))
    implementation(project(":beer_database"))
    implementation(project(":beer_network"))
    implementation(project(":presentation_utils"))

    implementation(libs.findLibrary("navigationFragmentKtx").get())
    implementation(libs.findLibrary("navigationUi").get())
    implementation(libs.findLibrary("navigationDynamicFeaturesFragment").get())
    implementation(libs.findLibrary("androidPlayCore").get())
    
    testImplementation(libs.findLibrary("striktCore").get())
    androidTestImplementation(libs.findLibrary("striktCore").get()) 
    
    testImplementation(libs.findLibrary("junit").get()) 
    testImplementation(libs.findLibrary("coroutinesTest").get())
}
