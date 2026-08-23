import org.gradle.accessors.dm.LibrariesForLibs

apply(plugin = "billionbeers.android.library")
apply(plugin = "billionbeers.android.metro")
apply(plugin = "billionbeers.android.compose")
apply(plugin = "billionbeers.jacoco")

registerDataLayerClasspathBoundaryCheck()

val libs = the<LibrariesForLibs>()

dependencies {
    "implementation"(this.project(":core"))
    "implementation"(this.project(":core-common"))
    "implementation"(this.project(":presentation_utils"))
    "implementation"(this.project(":beerdomain:api"))

    "implementation"(libs.lifecycleRuntimeKtx)
    "implementation"(libs.navigationFragmentKtx)
    "implementation"(libs.navigationUi)
}
