
plugins {
    id("com.android.library")
    id("kotlin-parcelize")
    id("de.mannodermaus.android-junit5")
}

apply(plugin = "billionbeers.android.common")
apply(plugin = "billionbeers.android.testing")
apply(plugin = "billionbeers.jacoco")
apply(plugin = "billionbeers.spotless")
apply(plugin = "billionbeers.detekt")
apply(plugin = "billionbeers.unused-dependencies")

val libs = billionBeersCatalog()

dependencies {
    // The JUnit 5 tier and useJUnitPlatform() come from billionbeers.android.testing. JUnit 4 stays
    // declared here: library modules still hold `org.junit.Test` tests, which the vintage engine
    // (also supplied there) runs alongside the Jupiter ones.
    "testImplementation"(libs.billionBeersLibrary("junit"))

    "androidTestImplementation"(libs.billionBeersBundle("instrumentedTest"))
}
