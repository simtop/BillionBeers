import org.gradle.kotlin.dsl.`kotlin-dsl`

plugins {
    `kotlin-dsl`
}

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation("com.android.tools.build:gradle:8.2.0")
    implementation("com.android.tools.build:gradle-api:8.2.0") // For com.android.application/library
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.20")
    implementation("com.google.dagger:hilt-android-gradle-plugin:2.56.2") // For com.google.dagger.hilt.android
}
