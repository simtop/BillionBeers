import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("billionbeers.android.library")
    id("billionbeers.android.hilt")
    id("billionbeers.jacoco")
}

val libs = the<LibrariesForLibs>()

dependencies {
    "implementation"(project(":core"))
    "implementation"(project(":core-common"))
    "implementation"(project(":presentation_utils"))
    "implementation"(project(":beerdomain"))

    "implementation"(libs.lifecycleRuntimeKtx)
    "implementation"(libs.navigationFragmentKtx)
    "implementation"(libs.navigationUi)
}
