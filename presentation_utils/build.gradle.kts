plugins {
    alias(libs.plugins.android.library.convention)
}

android {
    namespace = "com.example.billionbeers.presentation_utils"
    // buildFeatures { compose = true } // This is set by the android-library-convention plugin
    // The Kotlin Compose plugin is also applied by the convention plugin.
}

dependencies {
    implementation(project(":beerdomain"))
}
