
apply(plugin = "billionbeers.android.library")
apply(plugin = "billionbeers.android.metro")
apply(plugin = "billionbeers.android.compose")
apply(plugin = "billionbeers.jacoco")

registerDataLayerClasspathBoundaryCheck()

val libs = billionBeersCatalog()

dependencies {
    "implementation"(this.project(":core"))
    "implementation"(this.project(":core-common"))
    "implementation"(this.project(":presentation_utils"))
    "implementation"(this.project(":beerdomain:api"))

    "implementation"(libs.billionBeersLibrary("lifecycleRuntimeKtx"))
    "implementation"(libs.billionBeersLibrary("navigationFragmentKtx"))
    "implementation"(libs.billionBeersLibrary("navigationUi"))
}
