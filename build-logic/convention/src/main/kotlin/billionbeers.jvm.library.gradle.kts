import org.gradle.accessors.dm.LibrariesForLibs
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.JavaVersion

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("billionbeers.jacoco")
    id("billionbeers.spotless")
    id("billionbeers.detekt")
}

val libs = the<LibrariesForLibs>()
configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutinesTest)
    testImplementation(libs.kluentAndroid)
    testImplementation(libs.turbine)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xstring-concat=inline")
    }
}
