import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("com.android.library")
    id("kotlin-parcelize")
    id("de.mannodermaus.android-junit5")
}

apply(plugin = "billionbeers.android.common")
apply(plugin = "billionbeers.jacoco")
apply(plugin = "billionbeers.spotless")
apply(plugin = "billionbeers.detekt")
apply(plugin = "billionbeers.unused-dependencies")

val libs = the<LibrariesForLibs>()

dependencies {
    "testImplementation"(libs.junit)
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
    "androidTestImplementation"(libs.kotlinTestJunit)
    "androidTestImplementation"(libs.coroutinesTest)
    "androidTestImplementation"(libs.espressoCore)
    "androidTestImplementation"(libs.testRunner)
    "androidTestImplementation"(libs.testRules)
    "androidTestImplementation"(libs.testCoreKtx)
    "androidTestImplementation"(libs.mockkAndroid)
    "androidTestImplementation"(libs.junitKtx)
    "androidTestImplementation"(libs.coreTesting)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
