plugins {
  id("billionbeers.android.dynamic.feature")
  id("billionbeers.android.screenshot")
}

android { namespace = "com.simtop.feature.beerbrowse" }

dependencies {
  implementation(project(":beerdomain:api"))
  implementation(project(":presentation_utils"))
  implementation(project(":core"))
  implementation(project(":core:designsystem"))
  implementation(project(":navigation"))
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.kotlinx.serialization.json)

  testImplementation(project(":beerdomain:fakes"))
  testImplementation(libs.striktCore)
}
