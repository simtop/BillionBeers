plugins {
  id("billionbeers.android.library")
  id("billionbeers.room")
  id("billionbeers.android.metro")
  id("billionbeers.android.managed.device")
}

android { namespace = "com.simtop.beer_database" }

dependencies {
  implementation(project(":core"))
  implementation(libs.kotlinx.serialization.json)
  // MigrationTestHelper; the Room Gradle plugin exposes the exported schemas to the test.
  androidTestImplementation(libs.roomTesting)
}
