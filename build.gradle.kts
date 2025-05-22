buildscript {
    // common.gradle application removed
    dependencies {
        classpath(libs.androidToolsBuildGradle)
        classpath(libs.kotlinGradlePlugin)
        classpath(libs.navigationSafeArgsPlugin)
        classpath (libs.hiltAndroidGradlePlugin)
        classpath(libs.benManesPlugin)
        classpath(libs.littlerobotsPlugin)
    }
}

// Commented-out sonarQube accessor removed

// allprojects block removed

apply(plugin = "android-reporting") // Kept as is

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}

plugins {
    id("org.sonarqube").version("6.2.0.5505")
    id("org.jetbrains.kotlin.plugin.compose").version(libs.versions.org.jetbrains.kotlin.get())

    alias(libs.plugins.hilt) apply false
}