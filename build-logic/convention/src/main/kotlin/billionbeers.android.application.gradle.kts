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

val libs = billionBeersCatalog()
val android = the<ApplicationExtension>()

android.apply {
    defaultConfig {
        targetSdk = libs.billionBeersVersion("targetSdk").toInt()
        // From gradle.properties, not a constant in the convention jar - see Versions.kt for why.
        versionCode = providers.gradleProperty("billionbeers.versionCode").get().toInt()
        versionName = providers.gradleProperty("billionbeers.versionName").get()
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

    // `benchmark` mirrors `release` via initWith, but initWith copies build-type settings, not
    // source sets - so benchmark lacked src/release, which holds the no-op DebugDrawerHost stub that
    // src/main references (the real drawer lives in src/debug). Without this the benchmark variant
    // does not compile, breaking macrobenchmark. The baseline-profile variants (benchmarkRelease,
    // nonMinifiedRelease) already extend release; this gives the hand-rolled build type the same.
    // AGP 9's built-in kotlinc compiles from the source set's kotlin dirs, so the .kt stub must be
    // added there (java.srcDir alone does not reach kotlin compilation).
    sourceSets.getByName("benchmark").kotlin.directories.add("src/release/java")

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/licenses/ASM"
            excludes += "META-INF/*.kotlin_module"
        }
    }

    lint {
        // One Android Lint pass over the app and its entire library graph (checkDependencies), so
        // a single `:app:lintDebug` covers the whole product with one checked-in baseline. This is
        // Google's Android-platform corpus (NewApi/minSdk misuse, a11y, manifest, security,
        // resources) - orthogonal to Detekt (Kotlin smells) and Konsist (our architecture rules),
        // none of which understand the framework. New errors fail CI; everything present at
        // adoption is grandfathered in lint-baseline.xml and burned down over time - never by
        // regenerating the baseline to bury a regression.
        baseline = project.layout.projectDirectory.file("lint-baseline.xml").asFile
        checkDependencies = true
        abortOnError = true
        warningsAsErrors = false
        // Lint runs as its own CI job via lintDebug, so it need not also gate release assembly.
        checkReleaseBuilds = false
        // Dependency-freshness checks are Dependabot's job (ADR 0005); leaving them on would only
        // add stale, version-churning noise to the baseline. Cosmetic - they are warning severity,
        // so with warningsAsErrors=false they never gated CI anyway; this just keeps the baseline
        // meaningful (latent bugs, not "an update exists").
        disable += setOf("NewerVersionAvailable", "GradleDependency", "AndroidGradlePluginVersion")
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
    "implementation"(libs.billionBeersLibrary("tracing-perfetto"))
    "implementation"(libs.billionBeersLibrary("tracing-perfetto-binary"))
    // JUnit 4, deliberately: this tier applies neither android-junit5 nor useJUnitPlatform(), and
    // :app's unit tests are `org.junit.Test`. It shares the bundles with the JUnit 5 tiers but not
    // the platform - see billionbeers.android.testing for why that split is intentional.
    "testImplementation"(libs.billionBeersLibrary("junit"))
    "testImplementation"(libs.billionBeersBundle("unitTest"))

    "androidTestImplementation"(libs.billionBeersBundle("instrumentedTest"))
}
