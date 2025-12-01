import com.android.build.gradle.LibraryExtension
import org.gradle.api.JavaVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
    id("billionbeers.jacoco")
    id("billionbeers.spotless")
    id("billionbeers.detekt")
    id("de.mannodermaus.android-junit5")
}

val libs = the<LibrariesForLibs>()

configure<LibraryExtension> {
    compileSdk = 35

    defaultConfig {
        minSdk = 28
        testInstrumentationRunner = "com.simtop.billionbeers.di.MockTestRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xstring-concat=inline")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
