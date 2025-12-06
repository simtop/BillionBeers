plugins {
  id("billionbeers.android.feature")
  id("billionbeers.android.compose")
}

android { namespace = "com.simtop.feature.beerslist" }

dependencies {
  implementation(project(":beerdomain:api"))
  implementation(project(":beerdomain:impl"))
  implementation(project(":navigation"))
  implementation(project(":presentation_utils"))
  testImplementation(project(":beerdomain:fakes"))

  implementation(libs.androidx.material3.android)
  implementation(libs.androidx.runtime.livedata)
  implementation(libs.androidx.ui.tooling.preview.android)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.hilt.navigation.compose)
}
