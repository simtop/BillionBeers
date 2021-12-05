buildscript {
    project.apply {
        from("$rootDir/common.gradle")
    }

    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:7.0.3")
        classpath(Libs.kotlin_gradle_plugin)
        classpath(Libs.navigation_safe_args_gradle_plugin)
        classpath (Libs.hilt_android_gradle_plugin)
        classpath("com.github.ben-manes:gradle-versions-plugin:0.39.0")
        classpath("nl.littlerobots.vcu:plugin:0.2.1")
    }
}

//gradlew versionCatalogUpdate --create //--create is just for starting it
apply(plugin = "com.github.ben-manes.versions")
apply(plugin = "nl.littlerobots.version-catalog-update")

plugins {
    buildSrcVersions
    sonarQube
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