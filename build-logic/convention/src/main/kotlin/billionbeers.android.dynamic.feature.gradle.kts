import com.android.build.api.dsl.DynamicFeatureExtension
import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("com.android.dynamic-feature")
    id("de.mannodermaus.android-junit5")
}

apply(plugin = "billionbeers.android.common")
apply(plugin = "billionbeers.android.metro")
apply(plugin = "billionbeers.android.compose")
apply(plugin = "billionbeers.jacoco")
apply(plugin = "billionbeers.spotless")
apply(plugin = "billionbeers.detekt")

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
    "implementation"(libs.navigationDynamicFeaturesFragment)

    "testImplementation"(libs.mockk)
    "testImplementation"(libs.coreTesting)
    "testImplementation"(libs.coroutinesTest)
    "testImplementation"(libs.kluentAndroid)
    "testImplementation"(libs.turbine)
    "testImplementation"(libs.junit.jupiter.api)
    "testImplementation"(libs.junit.jupiter.params)
    "testRuntimeOnly"(libs.junit.jupiter.engine)
    "testRuntimeOnly"(libs.junit.vintage.engine)

    "androidTestImplementation"(libs.junit)
    "androidTestImplementation"(libs.espressoCore)
    "androidTestImplementation"(libs.coreTesting)
}


