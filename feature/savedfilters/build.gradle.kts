plugins {
  id("billionbeers.android.feature")
  id("billionbeers.android.screenshot")
}

android { namespace = "com.simtop.feature.savedfilters" }

dependencies {
  implementation(project(":beerdomain:api"))
  implementation(project(":navigation"))
  implementation(project(":presentation_utils"))
  implementation(project(":core"))
  implementation(project(":shared:beerbrowse"))
  implementation(project(":core:designsystem"))
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.androidx.navigation3.runtime)

  implementation(libs.androidx.material3.android)
  implementation(libs.androidx.ui.tooling.preview.android)

  testImplementation(project(":beerdomain:fakes"))
  testImplementation(libs.striktCore)
}
