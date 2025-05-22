import com.android.build.gradle.LibraryExtension // For library specific settings
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions

plugins {
    id("com.android.library") // Changed from application
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

android<LibraryExtension> { // Changed from BaseAppModuleExtension
    compileSdkVersion(35)

    defaultConfig {
        minSdk = 21
        // targetSdk is not typically set in library defaultConfig, but common.gradle had it.
        // It's inherited by consuming apps. For consistency with common.gradle, we keep it.
        targetSdk = 35
        // versionCode and versionName are not applicable to libraries and will cause warnings or errors.
        // Omitting them for the library convention plugin.
        // versionCode = 51
        // versionName = "0.51"

        multiDexEnabled = true // Usually not needed for libraries, but common.gradle had it.
        testInstrumentationRunner = "com.simtop.billionbeers.di.MockTestRunner"
    }

    signingConfigs {
        maybeCreate("release").apply {
            val storeFileProp = getProperty("STORE_FILE")
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
            // For libraries, isMinifyEnabled should ideally be false so Proguarding is handled by the consuming application.
            // However, common.gradle had it as true for release builds.
            // To maintain consistency, we'll set it, but it's often better to let the app handle this.
            // isMinifyEnabled = false // Default for libraries
        }
        getByName("release") {
            isMinifyEnabled = true // As per common.gradle
            // isDebuggable = false // Default for release, already covered
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Libraries typically don't have signing configs applied directly, this is for apps.
            // However, common.gradle applied it. For consistency, we keep it.
            signingConfig = signingConfigs.getByName("release")
        }
    }
    
    // For libraries, consumer Proguard files are important.
    // common.gradle didn't specify this, but it's good practice.
    // defaultConfig {
    //    consumerProguardFile("consumer-rules.pro")
    // }


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
        compose = true // common.gradle had this for all modules.
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

    implementation(libs.hiltAndroid) // Hilt is used in libraries for injection
    kapt(libs.hiltCompiler)

    // Compose dependencies - using string versions from common.gradle
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
