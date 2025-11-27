import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

plugins {
    id("dagger.hilt.android.plugin")
    id("com.google.devtools.ksp")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    "implementation"(libs.findLibrary("hilt.android").get())
    "ksp"(libs.findLibrary("hilt.compiler").get())
    "ksp"(libs.findLibrary("androidx.hilt.compiler").get())
    "androidTestImplementation"(libs.findLibrary("hiltAndroidTesting").get())
    "kspAndroidTest"(libs.findLibrary("hilt.compiler").get())
}
