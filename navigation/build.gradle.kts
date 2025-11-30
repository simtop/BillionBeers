plugins {
    id("billionbeers.android.library")
    id("billionbeers.android.compose")
}

android {
    namespace = "com.simtop.navigation"
}

dependencies {
    implementation(project(":beerdomain"))
    implementation(libs.androidx.navigation.dynamic.features.runtime)
}
