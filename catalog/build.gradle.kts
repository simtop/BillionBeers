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
  implementation(this.project(":feature:beerslist"))
  implementation(this.project(":core:designsystem"))
  implementation(this.project(":presentation_utils"))
  implementation(this.project(":catalog-annotations"))

  // Compose: foundation, material3, ui-tooling-preview and activity-compose come from
  // billionbeers.android.compose.
  implementation(libs.androidx.navigation.compose)

  testImplementation(libs.junit)
}

tasks.withType<Test>().configureEach { configure<JacocoTaskExtension> { isEnabled = false } }
