buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath(libs.androidToolsBuildGradle)
        classpath(libs.kotlinGradlePlugin)
        classpath(libs.navigationSafeArgsPlugin)
        classpath (libs.hiltAndroidGradlePlugin)
    }

    configurations.all {
        resolutionStrategy {
            force("com.squareup:javapoet:1.13.0")
        }
    }
}

plugins {
    id("org.sonarqube").version("3.2.0")
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.com.google.devtools.ksp) apply false

    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.hilt) apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}