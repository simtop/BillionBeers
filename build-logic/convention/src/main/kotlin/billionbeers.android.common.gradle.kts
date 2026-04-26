import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.DynamicFeatureExtension
import com.android.build.api.dsl.TestExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_23)
        freeCompilerArgs.add("-Xstring-concat=inline")
    }
}
