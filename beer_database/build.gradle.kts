plugins {
  id("billionbeers.android.library")
  id("billionbeers.room")
  id("billionbeers.android.metro")
  id("billionbeers.android.managed.device")
}

android {
  namespace = "com.simtop.beer_database"
  // The shared convention points every module at the app's MockTestRunner (Metro/mockk graph),
  // which doesn't exist here. This module's instrumented tests are plain Room migration checks, so
  // use the stock runner.
  defaultConfig { testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
}

dependencies {
  implementation(project(":core"))
  implementation(libs.kotlinx.serialization.json)
  // MigrationTestHelper; the Room Gradle plugin exposes the exported schemas to the test.
  androidTestImplementation(libs.roomTesting)
}
