plugins {
    id("com.android.dynamic-feature")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.parcelize")
    id("org.jetbrains.kotlin.kapt")
    alias(libs.plugins.hilt)
    alias(libs.plugins.androidx.navigation.safeargs.kotlin)
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.billionbeers.feature.beerdetail"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        testInstrumentationRunner = "com.simtop.billionbeers.di.MockTestRunner"

        javaCompileOptions {
            annotationProcessorOptions {
                arguments["dagger.hilt.disableModulesHaveInstallInCheck"] = "true"
            }
        }
    }

    buildTypes {
        getByName("debug") {
            isTestCoverageEnabled = true
        }
        getByName("release") {
            // Empty in original, so nothing to add here
        }
    }

    (this as org.gradle.api.plugins.ExtensionAware).extensions.configure<org.jetbrains.kotlin.gradle.plugin.KaptExtension> {
        correctErrorTypes = true
        generateStubs = true
    }

    testOptions {
        unitTests.all {
            it.testLogging {
                events("passed", "skipped", "failed", "standardOut", "standardError")
            }
            it.maxHeapSize = "1024m"
        }
        // includeAndroidResources was in the original testOptions.unitTests block,
        // but outside the .all {}. It defaults to false.
        // If it needs to be true, it should be: unitTests.isIncludeAndroidResources = true
        // Original: testOptions.unitTests { includeAndroidResources = true; all { ... } }
        // For now, assuming it was meant to be inside `all` or specific to a sub-block not shown.
        // The common convention plugins set it to true. This module doesn't use them.
        // Let's add it directly under unitTests.
        unitTests.isIncludeAndroidResources = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
    }

    buildFeatures {
        viewBinding = true
        // compose is not explicitly set in the original buildFeatures block for this module.
        // However, the 'org.jetbrains.kotlin.plugin.compose' is applied.
        // For consistency with how convention plugins handle it: if the compose plugin is applied,
        // this feature should typically be true. The convention plugins set it.
        // Since this module does *not* use the convention plugin, we should set it if compose is used.
        // Given the compose plugin is applied, we'll set compose = true here.
        compose = true
    }

    packagingOptions {
        resources.pickFirsts.add("**/attach_hotspot_windows.dll")
        resources.excludes.add("META-INF/AL2.0")
        resources.excludes.add("META-INF/LGPL2.1")
        resources.excludes.add("META-INF/licenses/ASM")
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(libs.coreKtx)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)

    implementation(libs.glide)
    implementation(libs.lifecycleExtensions)
    implementation(libs.retrofit2ConverterGson)
    implementation(libs.okhttp3LoggingInterceptor)
    kapt(libs.roomCompiler)
    implementation(libs.roomKtx)
    kapt(libs.roomRuntime)
    implementation(libs.hiltAndroid)
    kapt(libs.hiltCompiler)

    implementation(libs.navigationFragmentKtx)
    implementation(libs.navigationUi)
    implementation(project(":beerdomain"))
    implementation(project(":presentation_utils"))
    implementation(project(":core"))

    implementation(project(":app"))
    implementation(libs.navigationDynamicFeaturesFragment)

    testImplementation(libs.mockkAndroid)
    testImplementation(libs.mockk)
    testImplementation(libs.coreTesting)
    testImplementation(libs.coroutinesTest)
    testImplementation(libs.kluentAndroid)
    testImplementation(libs.okhttp3Mockwebserver)
    testImplementation(libs.junit)

    androidTestImplementation(libs.hiltAndroidTesting)
    kaptAndroidTest(libs.hiltCompiler)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.kotlinTestJunit)
    androidTestImplementation(libs.coroutinesTest)
    androidTestImplementation(libs.espressoCore)
    androidTestImplementation(libs.espressoContrib)
    androidTestImplementation(libs.espressoIdlingResource)
    androidTestImplementation(libs.testRunner)
    androidTestImplementation(libs.testRules)
    androidTestImplementation(libs.testCoreKtx)
    androidTestImplementation(libs.mockkAndroid)
    androidTestImplementation(libs.junitKtx)
    androidTestImplementation(libs.navigationTesting)

    androidTestImplementation(libs.coreTesting)
    androidTestImplementation(libs.fragmentTesting)

    testImplementation(libs.striktCore)
    androidTestImplementation(libs.striktCore)
}

// Helper for kotlinOptions if needed, but direct configuration is used above.
// fun com.android.build.api.dsl.CommonExtension<*, *, *, *, *, *>.kotlinOptions(configure: org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions.() -> Unit) {
//    (this as org.gradle.api.plugins.ExtensionAware).extensions.configure("kotlinOptions", configure)
// }
