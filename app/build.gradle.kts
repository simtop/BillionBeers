plugins {
    id("billionbeers.android.application")
    id("billionbeers.android.compose")
    id("billionbeers.android.hilt")

    id("com.simtop.billionbeers.duplicate-classes")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.simtop.billionbeers"
    dynamicFeatures += setOf(":feature:beerdetail")
    
    packaging {
        resources {
            excludes += "META-INF/versions/9/module-info.class"
            excludes += "a/a.class"
            excludes += "a/b.class"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/LICENSE-notice.md"
        }
    }
}

dependencies {
    implementation(project(":beerdomain:api"))
    implementation(project(":beerdomain:impl"))
    androidTestImplementation(project(":beerdomain:fakes"))
    implementation(project(":feature:beerslist"))
    androidTestImplementation(project(":feature:beerdetail"))
    implementation(project(":core"))
    implementation(project(":navigation"))
    implementation(project(":beer_data"))
    implementation(project(":beer_database"))
    implementation(project(":beer_network"))
    implementation(project(":presentation_utils"))
    

    
    implementation(libs.androidPlayCore)
    implementation(libs.androidPlayCoreKtx)
    implementation(libs.androidxActivityCompose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.appcompat)    
    testImplementation(libs.striktCore)
    androidTestImplementation(libs.striktCore)
    
    // TODO: move to another module when we create the test module
    implementation(libs.junit)
    implementation(libs.coroutinesTest)
    
    testImplementation(libs.okhttp3Mockwebserver)
    testImplementation(libs.retrofit2ConverterGson)
    testImplementation(libs.okhttp3LoggingInterceptor)
    
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    androidTestImplementation(libs.roomRuntime)

}
