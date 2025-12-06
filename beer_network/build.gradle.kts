plugins {
  id("billionbeers.android.library")
  id("billionbeers.android.hilt")
}

android { namespace = "com.simtop.beer_network" }

dependencies {
  implementation(project(":core"))

  implementation(libs.retrofit2ConverterGson)
  implementation(libs.okhttp3LoggingInterceptor)
  implementation(libs.androidx.annotation)
}
