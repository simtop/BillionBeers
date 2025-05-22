plugins {
    id("android-library-convention")
    id("androidx.navigation.safeargs.kotlin") // Preserved
}

android {
    namespace = "com.example.billionbeers.feature.beerslist"
}

dependencies {
    implementation(libs.findLibrary("navigationFragmentKtx").get())
    implementation(libs.findLibrary("navigationUi").get())
    implementation(project(":beerdomain"))
    implementation(project(":presentation_utils"))
    implementation(project(":core"))
    implementation(libs.findLibrary("navigationDynamicFeaturesFragment").get())
}
