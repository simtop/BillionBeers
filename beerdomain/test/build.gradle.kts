plugins {
    id("billionbeers.android.library")
}

android {
    namespace = "com.simtop.beerdomain.test"
}

dependencies {
    implementation(project(":beerdomain"))
    implementation(project(":core-common"))
    implementation(libs.kotlinx.coroutines.core)
}
