plugins {
  id("billionbeers.android.dynamic.feature")
  id("billionbeers.android.screenshot")
  id("billionbeers.android.managed.device")
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

  androidTestImplementation(this.project(":testing-utils-android"))
  androidTestImplementation(libs.androidx.ui.test.junit4)
  debugImplementation(libs.androidx.ui.test.manifest)
}
