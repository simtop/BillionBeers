import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("com.android.library")
    id("kotlin-parcelize")
    id("billionbeers.jacoco")
    id("billionbeers.spotless")
    id("billionbeers.detekt")
    id("de.mannodermaus.android-junit5")
    id("billionbeers.unused-dependencies")
}

val libs = the<LibrariesForLibs>()

val android = the<LibraryExtension>()

android.apply {
    compileSdk = 35

    defaultConfig {
        minSdk = 28
        testInstrumentationRunner = "com.simtop.billionbeers.di.MockTestRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_23
        targetCompatibility = JavaVersion.VERSION_23
    }

    packaging {
        resources {
            excludes += "META-INF/AL2.0"
            excludes += "META-INF/LGPL2.1"
            excludes += "META-INF/licenses/ASM"
            excludes += "META-INF/*.kotlin_module"
        }
    }
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coreTesting)
    testImplementation(libs.coroutinesTest)
    testImplementation(libs.kluentAndroid)
    testImplementation(libs.turbine)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.vintage.engine)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.kotlinTestJunit)
    androidTestImplementation(libs.coroutinesTest)
    androidTestImplementation(libs.espressoCore)
    androidTestImplementation(libs.testRunner)
    androidTestImplementation(libs.testRules)
    androidTestImplementation(libs.testCoreKtx)
    androidTestImplementation(libs.mockkAndroid)
    androidTestImplementation(libs.junitKtx)
    androidTestImplementation(libs.coreTesting)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
