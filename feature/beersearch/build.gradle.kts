plugins {
  id("billionbeers.android.feature")
  id("billionbeers.android.screenshot")
  id("billionbeers.android.feature.uitest")
}

android { namespace = "com.simtop.feature.beersearch" }

dependencies {
  implementation(this.project(":navigation"))
  implementation(libs.androidx.navigation3.runtime)
  implementation(this.project(":core:designsystem"))
  implementation(this.project(":shared:beersearch"))
  implementation(libs.lifecycle.runtime.compose)

  testImplementation(this.project(":beerdomain:fakes"))
  testImplementation(libs.striktCore)
}
