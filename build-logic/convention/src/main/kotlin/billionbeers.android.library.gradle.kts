import com.android.build.gradle.LibraryExtension
import org.gradle.api.JavaVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

configure<LibraryExtension> {
    compileSdk = 35

    defaultConfig {
        minSdk = 26
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
    "testImplementation"(libs.findLibrary("junit").get())
    "testImplementation"(libs.findLibrary("mockk").get())
    "testImplementation"(libs.findLibrary("coreTesting").get())
    "testImplementation"(libs.findLibrary("coroutinesTest").get())
    "testImplementation"(libs.findLibrary("kluentAndroid").get())
    "testImplementation"(libs.findLibrary("turbine").get())

    "androidTestImplementation"(libs.findLibrary("junit").get())
    "androidTestImplementation"(libs.findLibrary("kotlinTestJunit").get())
    "androidTestImplementation"(libs.findLibrary("coroutinesTest").get())
    "androidTestImplementation"(libs.findLibrary("espressoCore").get())
    "androidTestImplementation"(libs.findLibrary("testRunner").get())
    "androidTestImplementation"(libs.findLibrary("testRules").get())
    "androidTestImplementation"(libs.findLibrary("testCoreKtx").get())
    "androidTestImplementation"(libs.findLibrary("mockkAndroid").get())
    "androidTestImplementation"(libs.findLibrary("junitKtx").get())
    "androidTestImplementation"(libs.findLibrary("coreTesting").get())
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xstring-concat=inline")
    }
}
