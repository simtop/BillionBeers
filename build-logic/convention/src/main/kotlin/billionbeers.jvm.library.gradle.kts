import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.JavaVersion

plugins {
    id("org.jetbrains.kotlin.jvm")
}

apply(plugin = "billionbeers.jacoco")
apply(plugin = "billionbeers.spotless")
apply(plugin = "billionbeers.detekt")
apply(plugin = "billionbeers.kotlin.options")

val libs = the<LibrariesForLibs>()
configure<JavaPluginExtension> {
    sourceCompatibility = PROJECT_JAVA_VERSION
    targetCompatibility = PROJECT_JAVA_VERSION
}

dependencies {
    "testImplementation"(libs.junit)
    "testImplementation"(libs.mockk)
    "testImplementation"(libs.coroutinesTest)
    "testImplementation"(libs.kluentAndroid)
    "testImplementation"(libs.turbine)
}
