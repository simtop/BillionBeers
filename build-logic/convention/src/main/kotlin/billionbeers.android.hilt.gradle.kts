import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("dagger.hilt.android.plugin")
    id("com.google.devtools.ksp")
}

val libs = the<LibrariesForLibs>()

dependencies {
    add("implementation", libs.hilt.android)
    add("ksp", libs.hilt.compiler)
    add("ksp", libs.androidx.hilt.compiler)
    add("androidTestImplementation", libs.hiltAndroidTesting)
    add("kspAndroidTest", libs.hilt.compiler)
}
