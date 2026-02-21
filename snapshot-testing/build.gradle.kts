plugins {
    id("billionbeers.android.library")
    id("billionbeers.android.compose")
}

android {
    namespace = "com.simtop.billionbeers.snapshot_testing"
}

dependencies {
    implementation(libs.androidx.compose.runtime)
}
