import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.DynamicFeatureExtension
import com.android.build.api.dsl.TestExtension
import org.gradle.api.JavaVersion
import kotlin.text.toInt

apply(plugin = "billionbeers.kotlin.options")

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

pluginManager.withPlugin("com.android.application") {
    extensions.configure<ApplicationExtension> {
        compileSdk = libs.versions.compileSdk.get().toInt()
        defaultConfig {
            minSdk = libs.versions.minSdk.get().toInt()
            if (testInstrumentationRunner == null || testInstrumentationRunner == "android.test.InstrumentationTestRunner") {
                testInstrumentationRunner = "com.simtop.billionbeers.di.MockTestRunner"
            }
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_23
            targetCompatibility = JavaVersion.VERSION_23
        }
        testCoverage {
            jacocoVersion = "0.8.13"
        }
    }
}
pluginManager.withPlugin("com.android.library") {
    extensions.configure<LibraryExtension> {
        compileSdk = libs.versions.compileSdk.get().toInt()
        defaultConfig {
            minSdk = libs.versions.minSdk.get().toInt()
            if (testInstrumentationRunner == null || testInstrumentationRunner == "android.test.InstrumentationTestRunner") {
                testInstrumentationRunner = "com.simtop.billionbeers.di.MockTestRunner"
            }
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_23
            targetCompatibility = JavaVersion.VERSION_23
        }
        testCoverage {
            jacocoVersion = "0.8.13"
        }
    }
}
pluginManager.withPlugin("com.android.dynamic-feature") {
    extensions.configure<DynamicFeatureExtension> {
        compileSdk = libs.versions.compileSdk.get().toInt()
        defaultConfig {
            minSdk = libs.versions.minSdk.get().toInt()
            if (testInstrumentationRunner == null || testInstrumentationRunner == "android.test.InstrumentationTestRunner") {
                testInstrumentationRunner = "com.simtop.billionbeers.di.MockTestRunner"
            }
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_23
            targetCompatibility = JavaVersion.VERSION_23
        }
        testCoverage {
            jacocoVersion = "0.8.13"
        }
    }
}
pluginManager.withPlugin("com.android.test") {
    extensions.configure<TestExtension> {
        compileSdk = libs.versions.compileSdk.get().toInt()
        defaultConfig {
            minSdk = libs.versions.minSdk.get().toInt()
            if (testInstrumentationRunner == null || testInstrumentationRunner == "android.test.InstrumentationTestRunner") {
                testInstrumentationRunner = "com.simtop.billionbeers.di.MockTestRunner"
            }
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_23
            targetCompatibility = JavaVersion.VERSION_23
        }
    }
}
