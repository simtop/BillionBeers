plugins {
    id("com.android.dynamic-feature")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.parcelize")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.dagger.hilt.android")
    id("androidx.navigation.safeargs.kotlin") // .kotlin suffix
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.billionbeers.feature.beerdetail"
    compileSdk = 34

    defaultConfig {
        minSdk = 21 // Changed from minSdkVersion
        testInstrumentationRunner = "com.simtop.billionbeers.di.MockTestRunner"
        
        // For Hilt arguments in KTS
        javaCompileOptions {
            annotationProcessorOptions {
                arg("dagger.hilt.disableModulesHaveInstallInCheck", "true")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            enableUnitTestCoverage = true // Changed from testCoverageEnabled
        }
        getByName("release") {
            // Define release specific properties if any
        }
    }

    kapt {
        correctErrorTypes.set(true)
        generateStubs.set(true)
    }
    
    testOptions {
        unitTests.all {
            testLogging {
                events("passed", "skipped", "failed", "standardOut", "standardError")
            }
            maxHeapSize = "1024m" // Added from original (was inside all{})
        }
        unitTests.isIncludeAndroidResources = true // Changed from includeAndroidResources = true
    }

    buildFeatures {
        viewBinding = true
        // compose = true // If needed, though not in original feature/beerdetail buildFeatures
                       // The compose plugin is applied, so this could be true.
                       // common.gradle sets it to true. For consistency, let's assume it's true
                       // or relies on common.gradle. If this file overrides, it should be explicit.
                       // For now, matching the example, which implies it might be inherited or not strictly needed here if plugin applied.
    }

    packagingOptions {
        pickFirsts += "**/attach_hotspot_windows.dll" // Changed from pickFirst
        resources.excludes += "META-INF/AL2.0"
        resources.excludes += "META-INF/LGPL2.1"
        resources.excludes += "META-INF/licenses/ASM"
    }
    
    compileOptions { // Added from original
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions { // Added from original
        jvmTarget = JavaVersion.VERSION_21.toString()
    }
}

dependencies {
    implementation(fileTree("libs") { include("*.jar") })
    implementation(libs.findLibrary("coreKtx").get())
    implementation(libs.findLibrary("appcompat").get())
    implementation(libs.findLibrary("material").get())
    implementation(libs.findLibrary("constraintlayout").get())

    implementation(libs.findLibrary("glide").get())
    implementation(libs.findLibrary("lifecycleExtensions").get()) // Consider if this is still needed/latest
    implementation(libs.findLibrary("retrofit2ConverterGson").get())
    implementation(libs.findLibrary("okhttp3LoggingInterceptor").get())
    
    kapt(libs.findLibrary("roomCompiler").get())
    implementation(libs.findLibrary("roomKtx").get())
    // kapt libs.roomRuntime - roomRuntime is usually implementation or api, not kapt. Check usage.
    // Assuming it's meant to be:
    implementation(libs.findLibrary("roomRuntime").get()) 
    
    implementation(libs.findLibrary("hiltAndroid").get())
    kapt(libs.findLibrary("hiltCompiler").get())

    implementation(libs.findLibrary("navigationFragmentKtx").get())
    implementation(libs.findLibrary("navigationUi").get())
    implementation(project(":beerdomain"))
    implementation(project(":presentation_utils"))
    implementation(project(":core"))
    implementation(project(":app")) // Dynamic features can depend on :app
    implementation(libs.findLibrary("navigationDynamicFeaturesFragment").get())

    testImplementation(libs.findLibrary("mockkAndroid").get())
    testImplementation(libs.findLibrary("mockk").get()) // alias for mockk-jvm
    testImplementation(libs.findLibrary("coreTesting").get()) // androidx.arch.core:core-testing
    testImplementation(libs.findLibrary("coroutinesTest").get())
    testImplementation(libs.findLibrary("kluentAndroid").get())
    testImplementation(libs.findLibrary("okhttp3Mockwebserver").get())
    testImplementation(libs.findLibrary("junit").get())

    androidTestImplementation(libs.findLibrary("hiltAndroidTesting").get())
    kaptAndroidTest(libs.findLibrary("hiltCompiler").get()) // Kapt for AndroidTest

    androidTestImplementation(libs.findLibrary("junit").get()) // androidx.test.ext:junit or junit:junit? Assuming junit:junit
    androidTestImplementation(libs.findLibrary("kotlinTestJunit").get()) // org.jetbrains.kotlin:kotlin-test-junit
    // CoroutinesTest already as testImplementation, ensure it's not duplicated if not needed for androidTest specifically
    androidTestImplementation(libs.findLibrary("coroutinesTest").get()) 
    androidTestImplementation(libs.findLibrary("espressoCore").get())
    androidTestImplementation(libs.findLibrary("espressoContrib").get())
    androidTestImplementation(libs.findLibrary("espressoIdlingResource").get())
    androidTestImplementation(libs.findLibrary("testRunner").get()) // androidx.test:runner
    androidTestImplementation(libs.findLibrary("testRules").get()) // androidx.test:rules
    androidTestImplementation(libs.findLibrary("testCoreKtx").get()) // androidx.test:core-ktx
    androidTestImplementation(libs.findLibrary("mockkAndroid").get()) // Already testImplementation, but often for both
    androidTestImplementation(libs.findLibrary("junitKtx").get()) // androidx.test.ext:junit-ktx
    androidTestImplementation(libs.findLibrary("navigationTesting").get())
    androidTestImplementation(libs.findLibrary("coreTesting").get()) // Already testImplementation
    androidTestImplementation(libs.findLibrary("fragmentTesting").get())

    testImplementation(libs.findLibrary("striktCore").get())
    androidTestImplementation(libs.findLibrary("striktCore").get())
}
