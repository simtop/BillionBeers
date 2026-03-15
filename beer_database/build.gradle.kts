plugins {
  id("billionbeers.android.library")
  id("billionbeers.room")
  id("billionbeers.android.metro")
}

android { namespace = "com.simtop.beer_database" }

dependencies {
  implementation(project(":core"))
  implementation(libs.retrofit2ConverterGson)
}
