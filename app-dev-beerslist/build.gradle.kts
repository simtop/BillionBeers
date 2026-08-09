plugins {
  id("billionbeers.android.application")
  id("billionbeers.android.compose")
  id("billionbeers.android.metro")
}

android {
  namespace = "com.simtop.billionbeers.devbeerslist"

  defaultConfig {
    applicationId = "com.simtop.billionbeers.devbeerslist"
    versionCode = 1
    versionName = "1.0"
  }
}

dependencies {
  // Only the module under active development, plus its fakes - no :beer_data, :beer_database,
  // :beer_network, or :feature:beerdetail, so this assembles in seconds instead of minutes.
  implementation(project(":feature:beerslist"))
  implementation(project(":beerdomain:api"))
  implementation(project(":beerdomain:fakes"))
  implementation(project(":core"))
  implementation(project(":core:designsystem"))
  implementation(project(":navigation"))
  implementation(project(":presentation_utils"))

  implementation(libs.androidPlayCore)
  implementation(libs.androidPlayCoreKtx)
  implementation(libs.kotlinx.serialization.json)
}
