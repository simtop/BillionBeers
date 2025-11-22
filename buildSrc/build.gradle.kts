plugins {
    `kotlin-dsl`
}

group = "com.simtop.billionbeers.buildlogic"
version = "1.0-SNAPSHOT"

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("com.android.tools.build:gradle:8.10.0")
    implementation("com.android.tools.build:gradle-api:8.10.0")
    //implementation("org.jetbrains.kotlin.jvm:org.jetbrains.kotlin.jvm.gradle.plugin:2.1.21")
}

configurations.all {
    resolutionStrategy {
        force("com.squareup:javapoet:1.13.0")
    }
}