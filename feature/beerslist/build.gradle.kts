plugins {
    alias(libs.plugins.android.library.convention)
    alias(libs.plugins.androidx.navigation.safeargs.kotlin)
}

android {
    namespace = "com.example.billionbeers.feature.beerslist"
    // buildFeatures { compose = true } // This is set by the android-library-convention plugin
}

dependencies {
    implementation(libs.navigationFragmentKtx)
    implementation(libs.navigationUi)
    implementation(project(":beerdomain"))
    implementation(project(":presentation_utils"))
    implementation(project(":core"))

    implementation(libs.navigationDynamicFeaturesFragment)
}
