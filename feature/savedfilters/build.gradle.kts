plugins {
  id("billionbeers.android.feature")
  id("billionbeers.android.screenshot")
}

android { namespace = "com.simtop.feature.savedfilters" }

dependencies {
  implementation(project(":beer-domain:api"))
  implementation(project(":navigation"))
  implementation(project(":presentation-utils"))
  implementation(project(":core"))
  implementation(project(":shared:presentation"))
  implementation(project(":shared:savedfilters"))
  implementation(project(":core:designsystem"))
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.lifecycle.runtime.compose)

  implementation(libs.androidx.material3.android)
  implementation(libs.androidx.ui.tooling.preview.android)

  testImplementation(project(":beer-domain:fakes"))
  testImplementation(libs.striktCore)
}
