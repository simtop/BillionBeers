import com.android.build.api.dsl.DynamicFeatureExtension
import org.gradle.api.JavaVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("com.android.dynamic-feature")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

val libs = the<LibrariesForLibs>()

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
    "implementation"(libs.coreKtx)
    "implementation"(libs.appcompat)
    "implementation"(libs.material)
    "implementation"(libs.constraintlayout)
    
    "implementation"(libs.lifecycleRuntimeKtx)
    "implementation"(libs.navigationFragmentKtx)
    "implementation"(libs.navigationUi)
    "implementation"(libs.navigationDynamicFeaturesFragment)
    
    // Hilt dependencies for Dynamic Feature
    "implementation"(libs.hilt.android)
    "ksp"(libs.hilt.compiler)
    "ksp"(libs.androidx.hilt.compiler)
    
    "testImplementation"(libs.junit)
    "androidTestImplementation"(libs.junit)
    "androidTestImplementation"(libs.espressoCore)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xstring-concat=inline")
    }
}
