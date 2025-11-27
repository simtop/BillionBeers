plugins {
    id("billionbeers.android.library")
}

android {
    namespace = "com.simtop.beerdomain"
}

dependencies {
    implementation(project(":core-common"))
    
    implementation(libs.androidx.annotation)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)
}
