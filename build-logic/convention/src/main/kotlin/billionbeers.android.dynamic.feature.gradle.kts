import com.android.build.api.dsl.DynamicFeatureExtension

plugins {
    id("com.android.dynamic-feature")
    id("de.mannodermaus.android-junit5")
}

apply(plugin = "billionbeers.android.common")
apply(plugin = "billionbeers.android.testing")
apply(plugin = "billionbeers.android.metro")
apply(plugin = "billionbeers.android.compose")
apply(plugin = "billionbeers.jacoco")
apply(plugin = "billionbeers.spotless")
apply(plugin = "billionbeers.detekt")
// Matches android.library / android.application. Its absence here meant dynamic features were the
// one tier the unused-dependency check never looked at.
apply(plugin = "billionbeers.unused-dependencies")

registerDataLayerClasspathBoundaryCheck()

val libs = billionBeersCatalog()

configure<DynamicFeatureExtension> {
    packaging {
        resources {
            pickFirsts += "**/attach_hotspot_windows.dll"
        }
    }
}

dependencies {
    "implementation"(this.project(":app"))
    "implementation"(libs.billionBeersLibrary("coreKtx"))
    "implementation"(libs.billionBeersLibrary("appcompat"))
    "implementation"(libs.billionBeersLibrary("material"))
    "implementation"(libs.billionBeersLibrary("constraintlayout"))
    
    "implementation"(libs.billionBeersLibrary("lifecycleRuntimeKtx"))
    "implementation"(libs.billionBeersLibrary("navigationFragmentKtx"))
    "implementation"(libs.billionBeersLibrary("navigationUi"))

    // The JUnit 5 tier, :testing-utils and useJUnitPlatform() come from billionbeers.android.testing.
    // This tier declares no JUnit 4: dynamic-feature modules have no `org.junit.Test` sources.
    "androidTestImplementation"(libs.billionBeersLibrary("junit"))
    "androidTestImplementation"(libs.billionBeersLibrary("espressoCore"))
    "androidTestImplementation"(libs.billionBeersLibrary("coreTesting"))
}
