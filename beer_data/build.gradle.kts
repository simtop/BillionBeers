plugins {
    id("org.jetbrains.kotlin.plugin.compose")
}

@Suppress("UNCHECKED_CAST")
val androidModule = rootProject.ext["androidModule"] as groovy.lang.Closure<Any>
androidModule.call(false)

android {
    namespace = "com.example.billionbeers.beer_data"
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":beerdomain"))
    implementation(project(":beer_database"))
    implementation(project(":beer_network"))
}
