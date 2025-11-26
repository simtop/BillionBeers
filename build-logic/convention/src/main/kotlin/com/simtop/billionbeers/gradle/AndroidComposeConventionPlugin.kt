package com.simtop.billionbeers.gradle

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

            fun configureCompose(commonExtension: CommonExtension<*, *, *, *, *, *>) {
                commonExtension.buildFeatures {
                    compose = true
                }

                dependencies {
                    val bom = libs.findLibrary("androidxComposeBom").get()
                    add("implementation", platform(bom))
                    add("androidTestImplementation", platform(bom))
                    
                    add("implementation", libs.findLibrary("androidxActivityCompose").get())
                    add("implementation", libs.findLibrary("androidx-foundation-android").get())
                    add("implementation", libs.findLibrary("androidx-material3-android").get())
                    add("implementation", libs.findLibrary("androidx-ui-tooling-preview-android").get())
                    add("implementation", libs.findLibrary("androidx-runtime-livedata").get())
                }
            }

            pluginManager.withPlugin("com.android.application") {
                val extension = extensions.getByType<ApplicationExtension>()
                configureCompose(extension)
            }

            pluginManager.withPlugin("com.android.library") {
                val extension = extensions.getByType<LibraryExtension>()
                configureCompose(extension)
            }

            pluginManager.withPlugin("com.android.dynamic-feature") {
                val extension = extensions.getByType<com.android.build.api.dsl.DynamicFeatureExtension>()
                configureCompose(extension)
            }
        }
    }
}
