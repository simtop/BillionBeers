plugins {
    alias(libs.plugins.android.library.convention)
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.billionbeers.core"
    // buildFeatures { compose = true } // This is set by the android-library-convention plugin
}

// Empty dependencies block omitted
