plugins {
    id("billionbeers.android.library")
    id("billionbeers.android.compose")
}

android {
    namespace = "com.simtop.presentation_utils"
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(project(":beerdomain:api"))
    implementation(project(":core"))
    
    implementation(libs.androidx.foundation.android)
    implementation(libs.androidx.material3.android)
    // TODO: think on how could I do it impl
    api(libs.androidPlayCore)
    api(libs.androidPlayCoreKtx)

    implementation(libs.androidx.ui.tooling.preview.android)
    
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    
    implementation(libs.coil3)
    implementation(libs.coil3.view)
    implementation(libs.coil3.network)
}
