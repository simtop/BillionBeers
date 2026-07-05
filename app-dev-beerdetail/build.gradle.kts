plugins {
  id("billionbeers.android.application")
  id("billionbeers.android.compose")
  id("billionbeers.android.metro")
}

android {
  namespace = "com.simtop.billionbeers.devbeerdetail"

  defaultConfig {
    applicationId = "com.simtop.billionbeers.devbeerdetail"
    versionCode = 1
    versionName = "1.0"
  }
}

dependencies {
  // Only the module under active development, plus its fakes - keep this list as small as
  // possible so this assembles in seconds. Add whichever fakes module(s) beerdetail needs.
  implementation(project(":feature:beerdetail"))
  implementation(project(":beerdomain:api"))
  implementation(project(":beerdomain:fakes"))
  implementation(project(":core"))
  implementation(project(":core:designsystem"))
  implementation(project(":navigation"))
  implementation(project(":presentation_utils"))

  implementation(libs.androidPlayCore)
  implementation(libs.androidPlayCoreKtx)
  implementation(libs.androidxActivityCompose)
  implementation(libs.kotlinx.serialization.json)
}
