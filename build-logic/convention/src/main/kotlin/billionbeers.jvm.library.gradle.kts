import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.JavaVersion

plugins {
    id("org.jetbrains.kotlin.jvm")
}

apply(plugin = "billionbeers.jacoco")
apply(plugin = "billionbeers.spotless")
apply(plugin = "billionbeers.detekt")
apply(plugin = "billionbeers.kotlin.options")

val libs = the<LibrariesForLibs>()
configure<JavaPluginExtension> {
    sourceCompatibility = PROJECT_JAVA_VERSION
    targetCompatibility = PROJECT_JAVA_VERSION
}

// Declared explicitly rather than via libs.bundles.unitTest: that bundle carries
// androidx.arch.core:core-testing, which has no business on a pure-JVM classpath. The JUnit 5
// halves below are shared with the Android tiers, since those are plain JVM artifacts.
dependencies {
    "testImplementation"(libs.junit)
    "testImplementation"(libs.mockk)
    "testImplementation"(libs.coroutinesTest)
    "testImplementation"(libs.kluentAndroid)
    "testImplementation"(libs.turbine)

    "testImplementation"(libs.bundles.unitTestJunit5)
    "testRuntimeOnly"(libs.bundles.unitTestJunit5Runtime)
    // Required on a plain JVM module the moment useJUnitPlatform() is switched on below: without it
    // the test JVM fails to start at all ("Could not start Gradle Test Executor"). The Android tiers
    // never need it declared because the mannodermaus plugin puts it on the classpath for them.
    "testRuntimeOnly"(libs.junit.platform.launcher)
}

// This tier used to ship JUnit 4 with no platform switch, so a pure-JVM module needing JUnit 5 had
// to opt out of the convention entirely and hand-roll its dependencies - which is exactly what
// :konsist did, and why it also went without spotless and detekt. The vintage engine keeps the
// JUnit 4 tests that are already here (:core-common's are all org.junit.Test) running alongside.
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
