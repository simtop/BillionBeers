plugins {
  id("billionbeers.android.feature")
  id("billionbeers.android.screenshot")
}

android { namespace = "com.simtop.feature.beerslist" }

dependencies {
  implementation(project(":beerdomain:api"))
  implementation(project(":navigation"))
  implementation(project(":presentation_utils"))
  implementation(project(":core"))
  implementation(project(":core:designsystem"))
  implementation(libs.kotlinx.serialization.json)
  testImplementation(project(":beerdomain:fakes"))
  testImplementation(libs.striktCore)

  implementation(libs.androidx.material3.android)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.ui.tooling.preview.android)
}
