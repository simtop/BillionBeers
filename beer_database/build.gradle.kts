plugins {
  id("billionbeers.android.library")
  id("billionbeers.room")
  id("billionbeers.android.metro")
  id("billionbeers.android.managed.device")
}

android { namespace = "com.simtop.beer_database" }

dependencies {
  // The Room provider still needs :core's Android qualifier and BuildConfig; common values use the
  // contract module directly rather than relying on :core's re-export.
  implementation(this.project(":core"))
  implementation(this.project(":core-common"))
  implementation(libs.kotlinx.serialization.json)
  // MigrationTestHelper; the Room Gradle plugin exposes the exported schemas to the test.
  androidTestImplementation(libs.roomTesting)
}
