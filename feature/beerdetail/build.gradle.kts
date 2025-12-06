plugins {
  id("billionbeers.android.dynamic.feature")
  id("billionbeers.android.compose")
}

android { namespace = "com.simtop.feature.beerdetail" }

dependencies {
  implementation(project(":beerdomain:api"))
  implementation(project(":beerdomain:impl"))
  implementation(project(":presentation_utils"))
  implementation(project(":core"))
  implementation(project(":navigation"))
  implementation(libs.androidx.navigation.compose)

  implementation(libs.coil3)
  implementation(libs.coil3.network)

  implementation(libs.retrofit2ConverterGson)
  implementation(libs.okhttp3LoggingInterceptor)

  testImplementation(libs.mockkAndroid)
  testImplementation(libs.mockk)
  testImplementation(libs.coreTesting)
  testImplementation(libs.coroutinesTest)
  testImplementation(libs.kluentAndroid)
  testImplementation(libs.okhttp3Mockwebserver)
  testImplementation(libs.turbine)

  androidTestImplementation(libs.hiltAndroidTesting)

  androidTestImplementation(libs.kotlinTestJunit)
  androidTestImplementation(libs.coroutinesTest)
  androidTestImplementation(libs.espressoContrib)
  androidTestImplementation(libs.espressoIdlingResource)
  androidTestImplementation(libs.testRunner)
  androidTestImplementation(libs.testRules)
  androidTestImplementation(libs.testCoreKtx)
  androidTestImplementation(libs.mockkAndroid)
  androidTestImplementation(libs.junitKtx)

  testImplementation(project(":beerdomain:fakes"))
  testImplementation(libs.striktCore)
  androidTestImplementation(libs.striktCore)
}
