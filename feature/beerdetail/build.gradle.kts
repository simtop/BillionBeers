plugins {
    id("billionbeers.android.dynamic.feature")
    id("billionbeers.android.compose")
    id("org.jetbrains.kotlin.plugin.parcelize")
    id("androidx.navigation.safeargs.kotlin")
}

android {
    namespace = "com.simtop.feature.beerdetail"
}

dependencies {
    implementation(project(":beerdomain"))
    implementation(project(":presentation_utils"))
    implementation(project(":core"))
    implementation(project(":navigation"))
    
    implementation(libs.glide)
    
    implementation(libs.retrofit2ConverterGson)
    implementation(libs.okhttp3LoggingInterceptor)
    
    testImplementation(libs.mockkAndroid)
    testImplementation(libs.mockk)
    testImplementation(libs.coreTesting)
    testImplementation(libs.coroutinesTest)
    testImplementation(libs.kluentAndroid)
    testImplementation(libs.okhttp3Mockwebserver)
    testImplementation(libs.turbine)
    
    androidTestImplementation(libs.hiltAndroidTesting)
    
    androidTestImplementation(libs.kotlinTestJunit)
    androidTestImplementation(libs.coroutinesTest)
    androidTestImplementation(libs.espressoContrib)
    androidTestImplementation(libs.espressoIdlingResource)
    androidTestImplementation(libs.testRunner)
    androidTestImplementation(libs.testRules)
    androidTestImplementation(libs.testCoreKtx)
    androidTestImplementation(libs.mockkAndroid)
    androidTestImplementation(libs.junitKtx)
    androidTestImplementation(libs.navigationTesting)
    androidTestImplementation(libs.coreTesting)
    androidTestImplementation(libs.fragmentTesting)
    
    testImplementation(libs.striktCore)
    androidTestImplementation(libs.striktCore)
}
