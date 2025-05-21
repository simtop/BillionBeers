buildscript {
    project.apply {
        from("$rootDir/common.gradle")
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

/*
public val PluginDependenciesSpec.sonarQube: PluginDependencySpec
    inline get() =
            id("org.sonarqube").version("3.2.0")

 */

allprojects {
    apply {
        from("$rootDir/common.gradle")
    }
}

apply(plugin = "android-reporting")

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}

plugins {
    id("org.sonarqube").version("6.2.0.5505")
    id("org.jetbrains.kotlin.plugin.compose").version(libs.versions.org.jetbrains.kotlin.get())

    alias(libs.plugins.hilt) apply false
}