package com.simtop.billionbeers.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.withType
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.kotlin.jvm")
            }
            
            val libs = extensions.getByType<org.gradle.api.artifacts.VersionCatalogsExtension>().named("libs")
            dependencies {
                add("testImplementation", libs.findLibrary("junit").get())
                add("testImplementation", libs.findLibrary("mockk").get())
                add("testImplementation", libs.findLibrary("coroutinesTest").get())
                add("testImplementation", libs.findLibrary("kluentAndroid").get()) // Or just kluent? kluentAndroid works for JVM too usually
                add("testImplementation", libs.findLibrary("turbine").get())
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
