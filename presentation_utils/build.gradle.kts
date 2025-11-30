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
    implementation(project(":beerdomain"))
    implementation(project(":core"))
    
    implementation(libs.androidx.foundation.android)
    implementation(libs.androidx.material3.android)
    implementation(libs.androidx.ui.tooling.preview.android)
    
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    
    implementation(libs.coil3)
    implementation(libs.coil3.view)
    implementation(libs.coil3.network)
}
