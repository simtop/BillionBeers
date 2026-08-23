plugins {
  id("billionbeers.android.dynamic.feature")
  id("billionbeers.android.screenshot")
}

android { namespace = "com.simtop.feature.beerbrowse" }

dependencies {
  implementation(this.project(":beerdomain:api"))
  implementation(this.project(":presentation_utils"))
  implementation(this.project(":core"))
  implementation(this.project(":core:designsystem"))
  implementation(this.project(":navigation"))
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.kotlinx.serialization.json)

  testImplementation(this.project(":beerdomain:fakes"))
  testImplementation(libs.striktCore)
}
