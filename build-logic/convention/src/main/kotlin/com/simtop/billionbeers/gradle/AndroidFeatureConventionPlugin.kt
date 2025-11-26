package com.simtop.billionbeers.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("billionbeers.android.library")
            pluginManager.apply("billionbeers.android.hilt")

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
            dependencies {
                add("implementation", project(":core"))
                add("implementation", project(":core-common"))
                add("implementation", project(":presentation_utils"))
                add("implementation", project(":beerdomain"))

                add("implementation", libs.findLibrary("lifecycleRuntimeKtx").get())
                // add("implementation", libs.findLibrary("lifecycleViewModelKtx").get()) // Not in TOML yet
                add("implementation", libs.findLibrary("navigationFragmentKtx").get())
                add("implementation", libs.findLibrary("navigationUi").get())
            }
        }
    }
}
