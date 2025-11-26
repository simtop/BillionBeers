package com.simtop.billionbeers.gradle

import com.android.build.api.dsl.DynamicFeatureExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

class AndroidDynamicFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.dynamic-feature")
                apply("org.jetbrains.kotlin.android")
                apply("com.google.devtools.ksp") // Needed for Hilt KSP
            }

            extensions.configure<DynamicFeatureExtension> {
                compileSdk = 35

                defaultConfig {
                    minSdk = 26
                    testInstrumentationRunner = "com.simtop.billionbeers.di.MockTestRunner"
                }

                compileOptions {
                    sourceCompatibility = org.gradle.api.JavaVersion.VERSION_17
                    targetCompatibility = org.gradle.api.JavaVersion.VERSION_17
                }
                
                // Fix for R8/Java 17
                kotlinOptions {
                    jvmTarget = "17"
                    freeCompilerArgs += listOf("-Xstring-concat=inline")
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
            
            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
            dependencies {
                add("implementation", project(":app"))
                add("implementation", libs.findLibrary("coreKtx").get())
                add("implementation", libs.findLibrary("appcompat").get())
                add("implementation", libs.findLibrary("material").get())
                add("implementation", libs.findLibrary("constraintlayout").get())
                
                add("implementation", libs.findLibrary("lifecycleRuntimeKtx").get())
                add("implementation", libs.findLibrary("navigationFragmentKtx").get())
                add("implementation", libs.findLibrary("navigationUi").get())
                add("implementation", libs.findLibrary("navigationDynamicFeaturesFragment").get())
                
                // Hilt dependencies for Dynamic Feature
                add("implementation", libs.findLibrary("hilt.android").get())
                add("ksp", libs.findLibrary("hilt.compiler").get())
                add("ksp", libs.findLibrary("androidx.hilt.compiler").get())
                
                add("testImplementation", libs.findLibrary("junit").get())
                add("androidTestImplementation", libs.findLibrary("junit").get())
                add("androidTestImplementation", libs.findLibrary("espressoCore").get())
            }
        }
    }
    
    private fun com.android.build.api.dsl.DynamicFeatureExtension.kotlinOptions(block: org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions.() -> Unit) {
        (this as org.gradle.api.plugins.ExtensionAware).extensions.configure("kotlinOptions", block)
    }
}
