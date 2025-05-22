plugins {
    alias(libs.plugins.android.application.convention)
    alias(libs.plugins.androidx.navigation.safeargs.kotlin) // Using the Kotlin-specific version
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.billionbeers"
    dynamicFeatures.addAll(":feature:beerdetail")
    // buildFeatures { compose = true } // This is set by the android-application-convention plugin
}

dependencies {
    implementation(libs.navigationFragmentKtx)
    implementation(libs.navigationUi)
    implementation(project(":beerdomain"))
    implementation(project(":feature:beerslist"))
    implementation(project(":core"))
    implementation(project(":beer_data"))
    implementation(project(":beer_database"))
    implementation(project(":beer_network"))
    implementation(project(":presentation_utils"))

    implementation(libs.navigationDynamicFeaturesFragment)

    implementation(libs.androidPlayCore)

    testImplementation(libs.striktCore)

    androidTestImplementation(libs.striktCore)

    //TODO: move to another module when we create the test module
    implementation(libs.junit)
    implementation(libs.coroutinesTest)
}
