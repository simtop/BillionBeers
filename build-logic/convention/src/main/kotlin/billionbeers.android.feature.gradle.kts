import org.gradle.accessors.dm.LibrariesForLibs

apply(plugin = "billionbeers.android.library")
apply(plugin = "billionbeers.android.metro")
apply(plugin = "billionbeers.android.compose")
apply(plugin = "billionbeers.jacoco")

registerDataLayerClasspathBoundaryCheck()

val libs = the<LibrariesForLibs>()

dependencies {
    "implementation"(project(":core"))
    "implementation"(project(":core-common"))
    "implementation"(project(":presentation_utils"))
    "implementation"(project(":beerdomain:api"))

    "implementation"(libs.lifecycleRuntimeKtx)
    "implementation"(libs.navigationFragmentKtx)
    "implementation"(libs.navigationUi)
}
