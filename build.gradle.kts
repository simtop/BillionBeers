buildscript {
    project.apply {
        from("$rootDir/common.gradle")
    }

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

//gradlew versionCatalogUpdate --create //--create is just for starting it
apply(plugin = "com.github.ben-manes.versions")
apply(plugin = "nl.littlerobots.version-catalog-update")


/*
public val PluginDependenciesSpec.sonarQube: PluginDependencySpec
    inline get() =
            id("org.sonarqube").version("3.2.0")

 */
plugins {
    id("org.sonarqube").version("3.2.0")
}

allprojects {
    apply {
        from("$rootDir/common.gradle")
    }
    repositories {
        google()
        mavenCentral()
    }
}

apply(plugin = "android-reporting")

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}