plugins {
  id("billionbeers.android.feature")
  id("billionbeers.android.screenshot")
  id("billionbeers.android.feature.uitest")
}

android { namespace = "com.simtop.feature.beersearch" }

dependencies {
  implementation(project(":navigation"))
  implementation(project(":core:designsystem"))
  implementation(libs.androidx.material3.android)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.ui.tooling.preview.android)

  testImplementation(project(":beerdomain:fakes"))
  testImplementation(libs.striktCore)
}
