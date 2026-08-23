plugins {
  id("billionbeers.android.library")
  id("billionbeers.android.metro")
}

android { namespace = "com.simtop.beer_data" }

dependencies {
  implementation(this.project(":core"))
  implementation(this.project(":beerdomain:api"))
  implementation(this.project(":beer_database"))
  implementation(this.project(":beer_network"))
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.retrofit2)
  testImplementation(libs.striktCore)
}
