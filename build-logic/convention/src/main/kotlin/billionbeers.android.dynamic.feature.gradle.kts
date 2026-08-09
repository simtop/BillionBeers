import com.android.build.api.dsl.DynamicFeatureExtension
import org.gradle.accessors.dm.LibrariesForLibs

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

val libs = the<LibrariesForLibs>()

configure<DynamicFeatureExtension> {
    packaging {
        resources {
            pickFirsts += "**/attach_hotspot_windows.dll"
        }
    }
}

dependencies {
    "implementation"(project(":app"))
    "implementation"(libs.coreKtx)
    "implementation"(libs.appcompat)
    "implementation"(libs.material)
    "implementation"(libs.constraintlayout)
    
    "implementation"(libs.lifecycleRuntimeKtx)
    "implementation"(libs.navigationFragmentKtx)
    "implementation"(libs.navigationUi)

    // The JUnit 5 tier, :testing-utils and useJUnitPlatform() come from billionbeers.android.testing.
    // This tier declares no JUnit 4: dynamic-feature modules have no `org.junit.Test` sources.
    "androidTestImplementation"(libs.junit)
    "androidTestImplementation"(libs.espressoCore)
    "androidTestImplementation"(libs.coreTesting)
}
