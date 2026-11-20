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
  // Only the module under active development, plus its fakes - no :beer-data, :beer-database,
  // :beer-network, or :feature:beerdetail, so this assembles in seconds instead of minutes.
  implementation(this.project(":feature:beerslist"))
  implementation(this.project(":beer-domain:api"))
  implementation(this.project(":beer-domain:fakes"))
  implementation(this.project(":core"))
  implementation(this.project(":core:designsystem"))
  implementation(this.project(":navigation"))
  implementation(this.project(":presentation-utils"))

  implementation(libs.androidPlayCore)
  implementation(libs.androidPlayCoreKtx)
  implementation(libs.kotlinx.serialization.json)
}
