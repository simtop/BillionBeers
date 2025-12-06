plugins {
    id("billionbeers.android.library")
    id("billionbeers.android.compose")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.simtop.navigation"
}

dependencies {
    implementation(project(":beerdomain:api"))
    implementation(libs.androidx.navigation.dynamic.features.runtime)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
}
