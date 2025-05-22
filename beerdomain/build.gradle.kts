plugins {
    id("org.jetbrains.kotlin.plugin.compose")
}

// Call Groovy closure from common.gradle (this will be removed later)
@Suppress("UNCHECKED_CAST")
val androidModule = rootProject.ext["androidModule"] as groovy.lang.Closure<Any>
androidModule.call(false) // Parameter 'false' for library modules

android {
    namespace = "com.example.billionbeers.beerdomain"
    buildFeatures {
        compose = true
    }
    // Other configurations like compileSdk, minSdk will come from common.gradle via androidModule
}

dependencies {
    implementation(project(":core"))
}
