plugins {
    `kotlin-dsl`
}

group = "com.simtop.billionbeers.buildlogic"

dependencies {
    implementation(libs.androidToolsBuildGradle)
    implementation(libs.kotlinGradlePlugin)
    implementation(libs.roomGradlePlugin)
    implementation(libs.hiltAndroidGradlePlugin)
    implementation(libs.navigationSafeArgsPlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "billionbeers.android.application"
            implementationClass = "com.simtop.billionbeers.gradle.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "billionbeers.android.library"
            implementationClass = "com.simtop.billionbeers.gradle.AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "billionbeers.android.compose"
            implementationClass = "com.simtop.billionbeers.gradle.AndroidComposeConventionPlugin"
        }
        register("androidHilt") {
            id = "billionbeers.android.hilt"
            implementationClass = "com.simtop.billionbeers.gradle.AndroidHiltConventionPlugin"
        }
        register("jvmLibrary") {
            id = "billionbeers.jvm.library"
            implementationClass = "com.simtop.billionbeers.gradle.JvmLibraryConventionPlugin"
        }
        register("androidFeature") {
            id = "billionbeers.android.feature"
            implementationClass = "com.simtop.billionbeers.gradle.AndroidFeatureConventionPlugin"
        }
        register("room") {
            id = "billionbeers.room"
            implementationClass = "com.simtop.billionbeers.gradle.RoomConventionPlugin"
        }
        register("androidDynamicFeature") {
            id = "billionbeers.android.dynamic.feature"
            implementationClass = "com.simtop.billionbeers.gradle.AndroidDynamicFeatureConventionPlugin"
        }
    }
}
