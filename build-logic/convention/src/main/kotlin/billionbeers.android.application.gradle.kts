import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import java.util.Properties

plugins {
    id("com.android.application")
    id("kotlin-parcelize")
}

//Because of Gradle 9 Bug: I moved them below the plugins block using
// apply(plugin = "billionbeers.*"). This defers the evaluation of the plugin until runtime
// rather than build-script compilation time, bypassing the accessor generation bug completely
// while preserving the exact same functionality.
apply(plugin = "billionbeers.android.common")
apply(plugin = "billionbeers.jacoco")
apply(plugin = "billionbeers.spotless")
apply(plugin = "billionbeers.detekt")
apply(plugin = "billionbeers.unused-dependencies")

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()
val android = the<ApplicationExtension>()

android.apply {
    defaultConfig {
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = PROJECT_VERSION_CODE
        versionName = PROJECT_VERSION_NAME
        multiDexEnabled = true
    }

    signingConfigs {
        create("release") {
            val keystorePropertiesFile = rootProject.file("keystore.properties")
            val keystoreProperties = Properties()
            if (keystorePropertiesFile.exists()) {
                keystoreProperties.load(keystorePropertiesFile.inputStream())
            }

            val storeFileName = keystoreProperties.getProperty("STORE_FILE") ?: System.getenv("STORE_FILE")
            val storePass = keystoreProperties.getProperty("STORE_PASSWORD") ?: System.getenv("STORE_PASSWORD")
            val alias = keystoreProperties.getProperty("ALIAS") ?: System.getenv("ALIAS")
            val keyPass = keystoreProperties.getProperty("PASSWORD") ?: System.getenv("PASSWORD")

            if (storeFileName != null) {
                storeFile = file(storeFileName)
                storePassword = storePass
                keyAlias = alias
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        getByName("debug") {
        }

        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }

        create("benchmark") {
            initWith(getByName("release"))
            matchingFallbacks.add("release")
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "benchmark-rules.pro")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/licenses/ASM"
            excludes += "META-INF/*.kotlin_module"
        }
    }
}

// The AndroidX Baseline Profile plugin auto-generates `nonMinifiedRelease` and `benchmarkRelease`
// build types from `release`, so they inherit the real release signing key. That clashes with
// debug-signed installs on a test device (INSTALL_FAILED_UPDATE_INCOMPATIBLE). These variants are
// only ever run on a device to capture profiles — never shipped — so sign them with the debug key,
// mirroring what the hand-written `benchmark` build type already does above.
//
// Registering this inside `withId("androidx.baselineprofile")` is what makes it stick: the plugin
// applies *after* this convention plugin and copies the release signing onto the generated types in
// its own finalizeDsl. Our withId callback fires once that plugin is applied, so the finalizeDsl we
// register here runs *last* and wins.
plugins.withId("androidx.baselineprofile") {
    extensions.configure<ApplicationAndroidComponentsExtension> {
        finalizeDsl { ext ->
            ext.buildTypes.configureEach {
                if (name.startsWith("nonMinified") || name.startsWith("benchmark")) {
                    signingConfig = ext.signingConfigs.getByName("debug")
                }
            }
        }
    }
}

dependencies {
    "implementation"(libs.tracing.perfetto)
    "implementation"(libs.tracing.perfetto.binary)
    "testImplementation"(libs.junit)
    "testImplementation"(libs.mockk)
    "testImplementation"(libs.coreTesting)
    "testImplementation"(libs.coroutinesTest)
    "testImplementation"(libs.kluentAndroid)
    "testImplementation"(libs.turbine)

    "androidTestImplementation"(libs.junit)
    "androidTestImplementation"(libs.kotlinTestJunit)
    "androidTestImplementation"(libs.coroutinesTest)
    "androidTestImplementation"(libs.espressoCore)
    "androidTestImplementation"(libs.testRunner)
    "androidTestImplementation"(libs.testRules)
    "androidTestImplementation"(libs.testCoreKtx)
    "androidTestImplementation"(libs.mockkAndroid)
    "androidTestImplementation"(libs.junitKtx)
    "androidTestImplementation"(libs.coreTesting)
}
