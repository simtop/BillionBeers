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
        //classpath(libs.composeCompiler)
    }

    configurations.all {
        resolutionStrategy {
            force("com.squareup:javapoet:1.13.0")
        }
    }
}

/*
public val PluginDependenciesSpec.sonarQube: PluginDependencySpec
    inline get() =
            id("org.sonarqube").version("3.2.0")

 */
plugins {
    id("org.sonarqube").version("3.2.0")
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.com.google.devtools.ksp) apply false

    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.hilt) apply false

    //alias(libs.plugins.android.application) apply false
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

//apply(plugin = "android-reporting")

//tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
//    compilerOptions {
//        freeCompilerArgs.addAll(
//            "-P",
//            "plugin:androidx.compose.compiler.plugins.kotlin:experimentalStrongSkipping=true"
//        )
//    }
//}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}