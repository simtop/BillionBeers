plugins {
  id("billionbeers.android.feature")
  id("billionbeers.android.screenshot")
  id("billionbeers.android.feature.uitest")
}

android { namespace = "com.simtop.feature.beerslist" }

dependencies {
  // :core, :core-common, :presentation_utils and :beerdomain:api come from the
  // billionbeers.android.feature plugin - see :feature:beersearch for the same shape.
  implementation(this.project(":navigation"))
  implementation(this.project(":core:designsystem"))
  implementation(libs.kotlinx.serialization.json)
  testImplementation(this.project(":beerdomain:fakes"))
  testImplementation(libs.striktCore)
}
