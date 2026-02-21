plugins {
  id("billionbeers.android.feature")
  id("billionbeers.android.compose")
  id("billionbeers.android.screenshot")
}

android { namespace = "com.simtop.feature.beerslist" }

dependencies {
  implementation(project(":beerdomain:api"))
  implementation(project(":beerdomain:impl"))
  implementation(project(":navigation"))
  implementation(project(":presentation_utils"))
  implementation(project(":core:designsystem"))
  testImplementation(project(":beerdomain:fakes"))

  implementation(libs.androidx.material3.android)
  implementation(libs.androidx.ui.tooling.preview.android)
  implementation(libs.hilt.navigation.compose)
}
