plugins {
    id("org.jetbrains.kotlin.plugin.compose")
}

@Suppress("UNCHECKED_CAST")
val androidModule = rootProject.ext["androidModule"] as groovy.lang.Closure<Any>
androidModule.call(false)

android {
    namespace = "com.example.billionbeers.beer_network"
    buildFeatures {
        compose = true // Assuming compose is used if plugin is applied
    }
}

dependencies {
    implementation(project(":core"))
}
