plugins {
    alias(libs.plugins.android.library.convention)
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.billionbeers.beer_data"
    // buildFeatures { compose = true } // This is set by the android-library-convention plugin
}

dependencies {
    implementation(project(":core"))
    implementation(project(":beerdomain"))
    implementation(project(":beer_database"))
    implementation(project(":beer_network"))
}
