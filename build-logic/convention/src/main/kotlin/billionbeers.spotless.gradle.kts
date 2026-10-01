import com.diffplug.gradle.spotless.SpotlessExtension

plugins {
    id("com.diffplug.spotless")
}

val libs = billionBeersCatalog()

configure<SpotlessExtension> {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**/*.kt", "**/bin/**/*.kt")
        ktfmt(libs.billionBeersVersion("ktfmt")).googleStyle()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktfmt(libs.billionBeersVersion("ktfmt")).googleStyle()
    }
}
