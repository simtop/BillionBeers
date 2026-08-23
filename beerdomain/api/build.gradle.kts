plugins {
  id("billionbeers.android.library")

  id("kotlin-parcelize")
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "com.simtop.beerdomain.api"

  //  @Suppress("UnstableApiUsage")
  //  testFixtures.enable = true
}

dependencies {
  implementation(this.project(":core-common"))
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.kotlinx.coroutines.core)
}
