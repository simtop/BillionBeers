plugins { id("billionbeers.android.feature") }

android { namespace = "com.simtop.feature.beersearch" }

dependencies {
  implementation(project(":navigation"))
  implementation(project(":core:designsystem"))
  implementation(libs.androidx.material3.android)
  implementation(libs.androidx.ui.tooling.preview.android)

  testImplementation(project(":beerdomain:fakes"))
  testImplementation(libs.striktCore)
}
