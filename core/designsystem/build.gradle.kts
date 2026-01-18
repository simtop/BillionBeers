plugins {
    id("billionbeers.android.library")
    id("billionbeers.android.compose")
}

android {
    namespace = "com.simtop.billionbeers.core.designsystem"
}

dependencies {
    implementation(libs.androidx.foundation.android)
    implementation(libs.androidx.material3.android)
}
