plugins {
  id("billionbeers.android.library")
  id("billionbeers.android.metro")
  alias(libs.plugins.kotlin.serialization)
}

android { namespace = "com.simtop.beer_network" }

dependencies {
  implementation(project(":core"))

  implementation(libs.retrofit2ConverterSerialization)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.okhttp3LoggingInterceptor)
  implementation(libs.androidx.annotation)
}
