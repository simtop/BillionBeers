buildscript {
    // project.apply { from("$rootDir/common.gradle") } // This line is commented out, effectively REMOVED
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath(libs.androidToolsBuildGradle)
        classpath(libs.kotlinGradlePlugin)
        classpath(libs.navigationSafeArgsPlugin)
        classpath (libs.hiltAndroidGradlePlugin)
        classpath(libs.benManesPlugin)
        classpath(libs.littlerobotsPlugin)
    }
}

// apply(from = "$rootDir/common.gradle") // REMOVED by BillionBeersBot

//gradlew versionCatalogUpdate --create //--create is just for starting it
// apply(plugin = "com.github.ben-manes.versions") // Temporarily removed
apply(plugin = "nl.littlerobots.version-catalog-update")

plugins {
    id("org.sonarqube").version("6.2.0.5505")
    id("org.jetbrains.kotlin.plugin.compose").version(libs.versions.org.jetbrains.kotlin.get())
}

allprojects {
    apply {
        // from("$rootDir/common.gradle") // This line Stays commented out
    }
    repositories {
        google()
        mavenCentral()
    }
}

// apply(plugin = "android-reporting") // Commented out

// tasks.register("clean", Delete::class) { // Commented out
//     delete(rootProject.buildDir)
// }