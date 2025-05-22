plugins {
    // com.android.library, kotlin-android, Hilt etc. are currently via androidModule.
    id("androidx.navigation.safeargs.kotlin") // Added .kotlin suffix
    id("org.jetbrains.kotlin.plugin.compose") // Added because compose is true
}

// Call Groovy closure from common.gradle (this will be removed later)
@Suppress("UNCHECKED_CAST")
val androidModule = rootProject.ext["androidModule"] as groovy.lang.Closure<Any>
androidModule.call(false) // Parameter 'false' for library modules

android {
    namespace = "com.example.billionbeers.feature.beerslist"
    buildFeatures {
        compose = true
    }
    // Other configurations like compileSdk, minSdk will come from common.gradle via androidModule
}

dependencies {
    implementation(libs.findLibrary("navigationFragmentKtx").get())
    implementation(libs.findLibrary("navigationUi").get())
    implementation(project(":beerdomain"))
    implementation(project(":presentation_utils"))
    implementation(project(":core"))
    implementation(libs.findLibrary("navigationDynamicFeaturesFragment").get())
}
