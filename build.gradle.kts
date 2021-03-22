buildscript {
    project.apply {
        from("$rootDir/common.gradle")
    }
    val kotlin_version by extra("1.4.31")
    repositories {
        google()
        jcenter()
    }
    dependencies {
        classpath(Libs.com_android_tools_build_gradle)
        classpath(Libs.kotlin_gradle_plugin)
        classpath(Libs.navigation_safe_args_gradle_plugin)
        classpath (Libs.hilt_android_gradle_plugin)
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version")
    }
}

plugins {
    buildSrcVersions
}


allprojects {
    apply {
        from("$rootDir/common.gradle")
    }
    repositories {
        google()
        jcenter()
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}

