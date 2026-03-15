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
    
    testImplementation(libs.junit)
}

tasks.withType<Test>().configureEach {
    configure<JacocoTaskExtension> {
        isEnabled = false
    }
}
