import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.DynamicFeatureExtension
import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("org.jetbrains.kotlin.plugin.compose")
}

val libs = the<LibrariesForLibs>()

fun configureCompose(commonExtension: CommonExtension<*, *, *, *, *, *>) {
    commonExtension.buildFeatures {
        compose = true
    }

    dependencies {
        val bom = libs.androidxComposeBom
        add("implementation", platform(bom))
        add("androidTestImplementation", platform(bom))
        
        add("implementation", libs.androidxActivityCompose)
        add("implementation", libs.androidx.foundation.android)
        add("implementation", libs.androidx.material3.android)
        add("implementation", libs.androidx.ui.tooling.preview.android)
        add("implementation", libs.androidx.runtime.livedata)
    }
}

pluginManager.withPlugin("com.android.application") {
    val extension = extensions.getByType<ApplicationExtension>()
    configureCompose(extension)
}

pluginManager.withPlugin("com.android.library") {
    val extension = extensions.getByType<LibraryExtension>()
    configureCompose(extension)
}

pluginManager.withPlugin("com.android.dynamic-feature") {
    val extension = extensions.getByType<DynamicFeatureExtension>()
    configureCompose(extension)
}
