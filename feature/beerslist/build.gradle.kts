plugins {
  id("billionbeers.android.feature")
  id("billionbeers.android.screenshot")
  id("billionbeers.android.feature.uitest")
}

android { namespace = "com.simtop.feature.beerslist" }

dependencies {
  // :core, :core-common, :presentation_utils and :beerdomain:api come from the
  // billionbeers.android.feature plugin - see :feature:beersearch for the same shape.
  implementation(project(":navigation"))
  implementation(project(":core:designsystem"))
  implementation(libs.kotlinx.serialization.json)
  testImplementation(project(":beerdomain:fakes"))
  testImplementation(libs.striktCore)

  implementation(libs.androidx.material3.android)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.ui.tooling.preview.android)
}
