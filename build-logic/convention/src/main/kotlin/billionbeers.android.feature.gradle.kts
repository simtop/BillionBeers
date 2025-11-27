import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

plugins {
    id("billionbeers.android.library")
    id("billionbeers.android.hilt")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    "implementation"(project(":core"))
    "implementation"(project(":core-common"))
    "implementation"(project(":presentation_utils"))
    "implementation"(project(":beerdomain"))

    "implementation"(libs.findLibrary("lifecycleRuntimeKtx").get())
    "implementation"(libs.findLibrary("navigationFragmentKtx").get())
    "implementation"(libs.findLibrary("navigationUi").get())
}
