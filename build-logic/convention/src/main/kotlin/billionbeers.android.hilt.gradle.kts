import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("dagger.hilt.android.plugin")
    id("com.google.devtools.ksp")
}

val libs = the<LibrariesForLibs>()

dependencies {
    "implementation"(libs.hilt.android)
    "ksp"(libs.hilt.compiler)
    "ksp"(libs.androidx.hilt.compiler)
    "androidTestImplementation"(libs.hiltAndroidTesting)
    "kspAndroidTest"(libs.hilt.compiler)
}
