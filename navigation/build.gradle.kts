plugins {
  id("billionbeers.android.library")
  id("billionbeers.android.compose")
  alias(libs.plugins.kotlin.serialization)
}

android { namespace = "com.simtop.navigation" }

dependencies {
  implementation(this.project(":beerdomain:api"))
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.kotlinx.serialization.json)

  testImplementation(libs.striktCore)
}
