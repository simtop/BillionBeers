plugins {
  id("billionbeers.android.library")
  id("billionbeers.android.metro")
}

android { namespace = "com.simtop.beer_data" }

dependencies {
  implementation(project(":core"))
  implementation(project(":beerdomain:api"))
  implementation(project(":beer_database"))
  implementation(project(":beer_network"))
  implementation(libs.kotlinx.coroutines.core)
}
