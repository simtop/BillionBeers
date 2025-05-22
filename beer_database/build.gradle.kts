val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = java.util.Properties()
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

fun getProperty(key: String): String? {
    return keystoreProperties.getProperty(key) ?: System.getenv(key)
}

plugins {
    // Plugins from convention (excluding the convention plugin itself)
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.parcelize")
    id("org.jetbrains.kotlin.kapt")
    alias(libs.plugins.hilt)

    // Existing plugins from beer_database/build.gradle.kts
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.billionbeers.beer_database" // Module-specific
    compileSdkVersion(35)

    defaultConfig {
        minSdk = 21
        targetSdk = 35 // As per common.gradle for libraries
        testInstrumentationRunner = "com.simtop.billionbeers.di.MockTestRunner"
        // consumerProguardFile("consumer-rules.pro") // Good practice, but not in original common.gradle
    }

    signingConfigs { // common.gradle applied this to libraries too
        maybeCreate("release").apply {
            val storeFileProp = getProperty("STORE_FILE") // Assumes getProperty is defined
            val storePasswordProp = getProperty("STORE_PASSWORD")
            val keyAliasProp = getProperty("ALIAS")
            val keyPasswordProp = getProperty("PASSWORD")

            if (storeFileProp != null && storePasswordProp != null && keyAliasProp != null && keyPasswordProp != null) {
                storeFile = project.file(storeFileProp)
                storePassword = storePasswordProp
                keyAlias = keyAliasProp
                keyPassword = keyPasswordProp
            } else {
                println("Warning: Keystore properties for release signing are not fully set. Library variants may not be signable if this config is used by an app consuming it directly for signing.")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            isTestCoverageEnabled = true
        }
        getByName("release") {
            isMinifyEnabled = true // As per common.gradle
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release") // As per common.gradle
        }
    }
    
    kapt { // Kapt configuration for libraries
        correctErrorTypes = true
        generateStubs = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.testLogging {
                events("passed", "skipped", "failed", "standardOut", "standardError")
            }
            it.maxHeapSize = "1024m"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    buildFeatures {
        viewBinding = true // common.gradle had this
        compose = true     // common.gradle had this
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "2.1.20"
    }

    packagingOptions {
        resources.pickFirsts.add("**/attach_hotspot_windows.dll")
        resources.excludes.add("META-INF/AL2.0")
        resources.excludes.add("META-INF/LGPL2.1")
        resources.excludes.add("META-INF/licenses/ASM")
        resources.excludes.add("META-INF/*.kotlin_module")
    }
}

dependencies {
    // Dependencies from the convention plugin
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

    // Compose dependencies from convention
    implementation("androidx.compose.runtime:runtime:1.0.5")
    implementation("androidx.compose.ui:ui:1.0.5")
    implementation("androidx.compose.foundation:foundation:1.0.5")
    implementation("androidx.compose.foundation:foundation-layout:1.0.5")
    implementation("androidx.compose.material:material:1.0.5")
    implementation("androidx.compose.runtime:runtime-livedata:1.0.5")
    implementation("androidx.compose.ui:ui-tooling:1.0.5")

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

    // Dependencies from the original beer_database/build.gradle.kts
    implementation(project(":core")) // This is distinct from libs.coreKtx and should be kept.
}
