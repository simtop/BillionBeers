plugins {
  id("billionbeers.android.feature")
  id("billionbeers.android.screenshot")
  id("billionbeers.android.feature.uitest")
}

android { namespace = "com.simtop.feature.favorites" }

dependencies {
  implementation(this.project(":navigation"))
  implementation(this.project(":core:designsystem"))

  testImplementation(this.project(":beerdomain:fakes"))
  testImplementation(libs.striktCore)
}
