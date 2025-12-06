plugins {
  id("billionbeers.android.library")
  id("billionbeers.android.hilt")
}

android { namespace = "com.simtop.core" }

dependencies {
  api(project(":core-common"))
  implementation(libs.coreKtx)
  implementation(libs.appcompat)
  implementation(libs.material)

  implementation(libs.retrofit2ConverterGson)
  implementation(libs.okhttp3LoggingInterceptor)
}
