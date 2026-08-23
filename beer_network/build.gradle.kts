plugins {
  id("billionbeers.android.library")
  id("billionbeers.android.metro")
  alias(libs.plugins.kotlin.serialization)
}

android { namespace = "com.simtop.beer_network" }

dependencies {
  implementation(this.project(":core"))

  // Main declares the service and DTOs, so it needs Retrofit itself. It used to reach it only
  // transitively through the converter below - which main never actually used.
  implementation(libs.retrofit2)
  implementation(libs.kotlinx.serialization.json)

  // The converter and logging interceptor are test-only: the production Retrofit/OkHttp stack is
  // assembled in :core's NetworkingModule, and only TestMockWebService builds one by hand.
  testImplementation(this.project(":beer_network:fixtures"))
  testImplementation(libs.okhttp3Mockwebserver)
  testImplementation(libs.retrofit2ConverterSerialization)
  testImplementation(libs.okhttp3LoggingInterceptor)
}
