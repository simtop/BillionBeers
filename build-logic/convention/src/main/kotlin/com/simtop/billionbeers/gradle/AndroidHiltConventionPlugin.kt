package com.simtop.billionbeers.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

class AndroidHiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("dagger.hilt.android.plugin")
                apply("com.google.devtools.ksp")
            }

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
            dependencies {
                add("implementation", libs.findLibrary("hilt.android").get())
                add("ksp", libs.findLibrary("hilt.compiler").get())
                add("ksp", libs.findLibrary("androidx.hilt.compiler").get())
                add("androidTestImplementation", libs.findLibrary("hiltAndroidTesting").get())
                add("kspAndroidTest", libs.findLibrary("hilt.compiler").get())
            }
        }
    }
}
