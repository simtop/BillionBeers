package com.simtop.billionbeers.gradle

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.application")
                apply("org.jetbrains.kotlin.android")
                apply("kotlin-parcelize")
            }

            val libs = extensions.getByType<org.gradle.api.artifacts.VersionCatalogsExtension>().named("libs")
            dependencies {
                add("testImplementation", libs.findLibrary("junit").get())
                add("testImplementation", libs.findLibrary("mockk").get())
                add("testImplementation", libs.findLibrary("coreTesting").get())
                add("testImplementation", libs.findLibrary("coroutinesTest").get())
                add("testImplementation", libs.findLibrary("kluentAndroid").get())
                add("testImplementation", libs.findLibrary("turbine").get())
                
                add("androidTestImplementation", libs.findLibrary("junit").get())
                add("androidTestImplementation", libs.findLibrary("kotlinTestJunit").get())
                add("androidTestImplementation", libs.findLibrary("coroutinesTest").get())
                add("androidTestImplementation", libs.findLibrary("espressoCore").get())
                add("androidTestImplementation", libs.findLibrary("testRunner").get())
                add("androidTestImplementation", libs.findLibrary("testRules").get())
                add("androidTestImplementation", libs.findLibrary("testCoreKtx").get())
                add("androidTestImplementation", libs.findLibrary("mockkAndroid").get())
                add("androidTestImplementation", libs.findLibrary("junitKtx").get())
                add("androidTestImplementation", libs.findLibrary("coreTesting").get())
            }

            extensions.configure<ApplicationExtension> {
                compileSdk = 35

                defaultConfig {
                    minSdk = 26
                    targetSdk = 35
                    versionCode = 56
                    versionName = "0.56"
                    testInstrumentationRunner = "com.simtop.billionbeers.di.MockTestRunner"
                    multiDexEnabled = true
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
            
            tasks.withType<KotlinCompile>().configureEach {
                compilerOptions {
                    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
                    freeCompilerArgs.add("-Xstring-concat=inline")
                }
            }
        }
    }
}
