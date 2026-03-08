plugins {
    id("billionbeers.android.application")
    id("billionbeers.android.compose")
    id("billionbeers.android.screenshot")
}

android {
    namespace = "com.simtop.billionbeers.catalog"
    
    defaultConfig {
        applicationId = "com.simtop.billionbeers.catalog"
        versionCode = 1
        versionName = "1.0"
    }
}

dependencies {
    implementation(project(":feature:beerslist"))
    implementation(project(":core:designsystem"))
    implementation(project(":presentation_utils"))
    implementation(project(":catalog-annotations"))
    
    // Compose
    implementation(libs.androidx.foundation.android)
    implementation(libs.androidx.material3.android)
    implementation(libs.androidx.ui.tooling.preview.android)
    implementation(libs.androidxActivityCompose)
    implementation(libs.androidx.navigation.compose)

    testImplementation(libs.junit)
}

tasks.withType<Test>().configureEach {
    configure<JacocoTaskExtension> {
        isEnabled = false
    }
}
