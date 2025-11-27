import com.android.build.api.dsl.DynamicFeatureExtension
import org.gradle.api.JavaVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

plugins {
    id("com.android.dynamic-feature")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

configure<DynamicFeatureExtension> {
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
            pickFirsts += "**/attach_hotspot_windows.dll"
        }
    }
}

dependencies {
    "implementation"(project(":app"))
    "implementation"(libs.findLibrary("coreKtx").get())
    "implementation"(libs.findLibrary("appcompat").get())
    "implementation"(libs.findLibrary("material").get())
    "implementation"(libs.findLibrary("constraintlayout").get())
    
    "implementation"(libs.findLibrary("lifecycleRuntimeKtx").get())
    "implementation"(libs.findLibrary("navigationFragmentKtx").get())
    "implementation"(libs.findLibrary("navigationUi").get())
    "implementation"(libs.findLibrary("navigationDynamicFeaturesFragment").get())
    
    // Hilt dependencies for Dynamic Feature
    "implementation"(libs.findLibrary("hilt.android").get())
    "ksp"(libs.findLibrary("hilt.compiler").get())
    "ksp"(libs.findLibrary("androidx.hilt.compiler").get())
    
    "testImplementation"(libs.findLibrary("junit").get())
    "androidTestImplementation"(libs.findLibrary("junit").get())
    "androidTestImplementation"(libs.findLibrary("espressoCore").get())
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xstring-concat=inline")
    }
}
