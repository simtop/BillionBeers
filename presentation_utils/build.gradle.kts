plugins {
  id("billionbeers.android.library")
  id("billionbeers.android.compose")
  id("billionbeers.android.screenshot")
  id("billionbeers.android.catalog")
}

android { namespace = "com.simtop.presentation_utils" }

dependencies {
  implementation(this.project(":beerdomain:api"))
  implementation(this.project(":core"))
  implementation(this.project(":core:designsystem"))

  // activity-compose, foundation, material3 and ui-tooling-preview come from
  // billionbeers.android.compose.
  implementation(libs.androidx.runtime.retain)
  // TODO: think on how could I do it impl
  api(libs.androidPlayCore)
  api(libs.androidPlayCoreKtx)

  implementation(libs.coil3)
  implementation(libs.coil3.view)
  implementation(libs.coil3.network)
  implementation(libs.lifecycle.viewmodel.compose)
}
