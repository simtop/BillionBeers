plugins {
    id("android-application-convention") // Apply the convention plugin
    id("androidx.navigation.safeargs.kotlin") // Keep app-specific plugins
}

android {
    namespace = "com.example.billionbeers" // App-specific
    
    defaultConfig {
        applicationId = "com.example.billionbeers" // App-specific
        // minSdk, targetSdk, versionCode, versionName, testInstrumentationRunner, multiDexEnabled
        // are now expected to come from the convention plugin.
    }

    dynamicFeatures = mutableSetOf(":feature:beerdetail") // App-specific

    // buildFeatures, compileOptions, kotlinOptions, packagingOptions, kapt
    // are now expected to come from the convention plugin.
}

// The androidModule.call(true) lines are removed.

dependencies {
    // Project dependencies (app-specific)
    implementation(project(":beerdomain"))
    implementation(project(":feature:beerslist"))
    implementation(project(":core")) // Note: If :core provides very basic utils, it might be common. For now, app-specific.
    implementation(project(":beer_data"))
    implementation(project(":beer_database"))
    implementation(project(":beer_network"))
    implementation(project(":presentation_utils"))

    // Navigation libraries (app-specific, as not all modules need all of these)
    implementation(libs.findLibrary("navigationFragmentKtx").get())
    implementation(libs.findLibrary("navigationUi").get())
    implementation(libs.findLibrary("navigationDynamicFeaturesFragment").get())
    
    // Other app-specific libraries
    implementation(libs.findLibrary("androidPlayCore").get())

    // App-specific test libraries (if not covered by convention plugin's common test deps)
    testImplementation(libs.findLibrary("striktCore").get())
    androidTestImplementation(libs.findLibrary("striktCore").get())

    // Common test dependencies like junit, espressoCore, hiltAndroidTesting
    // are now expected to come from the convention plugin.
}
