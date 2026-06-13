plugins {
  id("billionbeers.android.library")
  id("billionbeers.android.metro")
  alias(libs.plugins.kotlin.serialization)
}

android { namespace = "com.simtop.core" }

dependencies {
  api(project(":core-common"))
  implementation(libs.coreKtx)
  implementation(libs.appcompat)
  implementation(libs.material)
  implementation(libs.kotlinx.serialization.json)

  implementation(libs.retrofit2)
  implementation(libs.retrofit2ConverterSerialization)
  implementation(libs.okhttp3)
  implementation(libs.okhttp3LoggingInterceptor)
  implementation(libs.lifecycleRuntimeKtx)
}
