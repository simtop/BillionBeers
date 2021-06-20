buildscript {
    project.apply {
        from("$rootDir/common.gradle")
    }

    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath(Libs.com_android_tools_build_gradle)
        classpath(Libs.kotlin_gradle_plugin)
        classpath(Libs.navigation_safe_args_gradle_plugin)
        classpath(Libs.hilt_android_gradle_plugin)
        //classpath ("com.vanniktech:gradle-android-junit-jacoco-plugin:0.16.0")
        //classpath("org.jacoco:org.jacoco.core:0.7.9")
    }
}

//apply(plugin = "com.vanniktech.android.junit.jacoco")
//apply(plugin = from("$rootDir/coverage.gradle"))

plugins {
    buildSrcVersions
    sonarQube
    jacoco
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

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}