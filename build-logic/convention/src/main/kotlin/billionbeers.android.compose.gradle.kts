import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.DynamicFeatureExtension
import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("org.jetbrains.kotlin.plugin.compose")
}

val libs = the<LibrariesForLibs>()

pluginManager.withPlugin("com.android.base") {
    dependencies {
        "implementation"(platform(libs.androidxComposeBom))
        "androidTestImplementation"(platform(libs.androidxComposeBom))
        
        "implementation"(libs.androidxActivityCompose)
        "implementation"(libs.androidx.foundation.android)
        "implementation"(libs.androidx.material3.android)
        "implementation"(libs.androidx.compose.material.icons.core)
        "implementation"(libs.androidx.ui.tooling.preview.android)
        "implementation"(libs.androidx.runtime.livedata)
        "implementation"(libs.metrox.viewmodel.compose)
    }
}
