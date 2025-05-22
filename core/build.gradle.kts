plugins {
    // Assuming 'com.android.library' and 'kotlin-android' etc. are applied by androidModule for now.
    // This will change with convention plugins.
    // For now, we only list plugins explicitly applied in the Groovy script *besides* those in androidModule.
    id("org.jetbrains.kotlin.plugin.compose")
}

// Call Groovy closure from common.gradle (this will be removed later)
// 'false' indicates it's a library module.
@Suppress("UNCHECKED_CAST")
val androidModule = rootProject.ext["androidModule"] as groovy.lang.Closure<Any>
androidModule.call(false) // Parameter 'false' for library modules

android {
    namespace = "com.example.billionbeers.core"
    buildFeatures {
        compose = true
    }
    // Other configurations like compileSdk, minSdk will come from common.gradle via androidModule
}

dependencies {
    // Dependencies are currently handled by common.gradle via androidModule()
}
