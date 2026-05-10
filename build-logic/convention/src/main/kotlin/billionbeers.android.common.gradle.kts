import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.DynamicFeatureExtension
import com.android.build.api.dsl.TestExtension
import kotlin.text.toInt

apply(plugin = "billionbeers.kotlin.options")

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

val PROJECT_COMPILE_SDK = libs.versions.compileSdk.get().toInt()
val PROJECT_MIN_SDK = libs.versions.minSdk.get().toInt()

pluginManager.withPlugin("com.android.application") {
    extensions.configure<ApplicationExtension> {
        compileSdk = PROJECT_COMPILE_SDK
        defaultConfig {
            minSdk = PROJECT_MIN_SDK
            if (testInstrumentationRunner == null || testInstrumentationRunner == "android.test.InstrumentationTestRunner") {
                testInstrumentationRunner = PROJECT_TEST_RUNNER
            }
        }
        compileOptions {
            sourceCompatibility = PROJECT_JAVA_VERSION
            targetCompatibility = PROJECT_JAVA_VERSION
        }
        testCoverage {
            jacocoVersion = PROJECT_JACOCO_VERSION
        }
    }
}
pluginManager.withPlugin("com.android.library") {
    extensions.configure<LibraryExtension> {
        compileSdk = PROJECT_COMPILE_SDK
        defaultConfig {
            minSdk = PROJECT_MIN_SDK
            if (testInstrumentationRunner == null || testInstrumentationRunner == "android.test.InstrumentationTestRunner") {
                testInstrumentationRunner = PROJECT_TEST_RUNNER
            }
        }
        compileOptions {
            sourceCompatibility = PROJECT_JAVA_VERSION
            targetCompatibility = PROJECT_JAVA_VERSION
        }
        testCoverage {
            jacocoVersion = PROJECT_JACOCO_VERSION
        }
    }
}
pluginManager.withPlugin("com.android.dynamic-feature") {
    extensions.configure<DynamicFeatureExtension> {
        compileSdk = PROJECT_COMPILE_SDK
        defaultConfig {
            minSdk = PROJECT_MIN_SDK
            if (testInstrumentationRunner == null || testInstrumentationRunner == "android.test.InstrumentationTestRunner") {
                testInstrumentationRunner = PROJECT_TEST_RUNNER
            }
        }
        compileOptions {
            sourceCompatibility = PROJECT_JAVA_VERSION
            targetCompatibility = PROJECT_JAVA_VERSION
        }
        testCoverage {
            jacocoVersion = PROJECT_JACOCO_VERSION
        }
    }
}
pluginManager.withPlugin("com.android.test") {
    extensions.configure<TestExtension> {
        compileSdk = PROJECT_COMPILE_SDK
        defaultConfig {
            minSdk = PROJECT_MIN_SDK
            if (testInstrumentationRunner == null || testInstrumentationRunner == "android.test.InstrumentationTestRunner") {
                testInstrumentationRunner = PROJECT_TEST_RUNNER
            }
        }
        compileOptions {
            sourceCompatibility = PROJECT_JAVA_VERSION
            targetCompatibility = PROJECT_JAVA_VERSION
        }
    }
}
