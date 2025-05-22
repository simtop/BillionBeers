import com.android.build.gradle.internal.dsl.BaseAppModuleExtension // For application specific settings
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.parcelize")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.dagger.hilt.android")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = java.util.Properties()
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

fun getProperty(key: String): String? {
    return keystoreProperties.getProperty(key) ?: System.getenv(key)
}

android<BaseAppModuleExtension> {
    compileSdkVersion(35)

    defaultConfig {
        minSdk = 21
        targetSdk = 35
        versionCode = 51
        versionName = "0.51"

        multiDexEnabled = true
        testInstrumentationRunner = "com.simtop.billionbeers.di.MockTestRunner"
    }

    signingConfigs {
        maybeCreate("release").apply {
            val storeFileValue = getProperty("STORE_FILE")
            if (storeFileValue != null) { // Only proceed if STORE_FILE is set
                storeFile = project.file(storeFileValue)
                val storePasswordValue = getProperty("STORE_PASSWORD")
                val keyAliasValue = getProperty("ALIAS")
                val keyPasswordValue = getProperty("PASSWORD")

                if (storePasswordValue != null && keyAliasValue != null && keyPasswordValue != null) {
                    storePassword = storePasswordValue
                    keyAlias = keyAliasValue
                    keyPassword = keyPasswordValue
                } else {
                    println("Warning: STORE_FILE was found, but other keystore properties (STORE_PASSWORD, ALIAS, PASSWORD) for release signing are not fully set. APK may not be signable.")
                }
            } else {
                println("Warning: STORE_FILE property not found in keystore.properties or environment variables. Release signing will be skipped for application.")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            isTestCoverageEnabled = true
        }
        getByName("release") {
            isMinifyEnabled = true
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    (this as org.gradle.api.plugins.ExtensionAware).extensions.configure<org.jetbrains.kotlin.gradle.plugin.KaptExtension> {
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
        viewBinding = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "2.1.20" // common.gradle had kotlinCompilerVersion, KTS uses kotlinCompilerExtensionVersion
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
    kapt(libs.roomRuntime) // As per instruction, keeping kapt for roomRuntime

    implementation(libs.hiltAndroid)
    kapt(libs.hiltCompiler)

    // Compose dependencies - using string versions from common.gradle as libs.versions.toml doesn't have specific aliases for these exact versions
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
}

// Helper for android extension block
fun <T : com.android.build.api.dsl.CommonExtension<*, *, *, *, *, *>> android(configure: T.() -> Unit) {
    extensions.configure("android", configure)
}

// Helper for kotlinOptions within android extension
fun com.android.build.api.dsl.CommonExtension<*, *, *, *, *, *>.kotlinOptions(configure: org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions.() -> Unit) {
    (this as org.gradle.api.plugins.ExtensionAware).extensions.configure("kotlinOptions", configure)
}
