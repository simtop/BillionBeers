plugins {
    id("billionbeers.android.library")
    id("billionbeers.android.compose")
}

android {
    namespace = "com.simtop.billionbeers.catalog_annotations"
}

dependencies {
    implementation(libs.androidx.compose.runtime)
}
