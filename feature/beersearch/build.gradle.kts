plugins {
  id("billionbeers.android.feature")
  id("billionbeers.android.screenshot")
  id("billionbeers.android.feature.uitest")
}

android { namespace = "com.simtop.feature.beersearch" }

dependencies {
  implementation(project(":navigation"))
  implementation(project(":core:designsystem"))

  testImplementation(project(":beerdomain:fakes"))
  testImplementation(libs.striktCore)
}
