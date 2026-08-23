plugins {
  id("billionbeers.android.dynamic.feature")
  id("billionbeers.android.screenshot")
}

android { namespace = "com.simtop.feature.beerdetail" }

dependencies {
  implementation(this.project(":beerdomain:api"))
  implementation(this.project(":presentation_utils"))
  implementation(this.project(":core"))
  implementation(this.project(":core:designsystem"))
  implementation(this.project(":navigation"))
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.lifecycle.viewmodel.savedstate)

  implementation(libs.coil3)
  implementation(libs.coil3.network)

  implementation(libs.retrofit2ConverterSerialization)
  implementation(libs.okhttp3LoggingInterceptor)

  testImplementation(libs.mockkAndroid)
  testImplementation(libs.mockk)
  testImplementation(libs.coreTesting)
  testImplementation(libs.coroutinesTest)
  testImplementation(libs.kluentAndroid)
  testImplementation(libs.okhttp3Mockwebserver)
  testImplementation(libs.turbine)

  androidTestImplementation(libs.kotlinTestJunit)
  androidTestImplementation(libs.coroutinesTest)
  androidTestImplementation(libs.espressoContrib)
  androidTestImplementation(libs.espressoIdlingResource)
  androidTestImplementation(libs.testRunner)
  androidTestImplementation(libs.testRules)
  androidTestImplementation(libs.testCoreKtx)
  androidTestImplementation(libs.mockkAndroid)
  androidTestImplementation(libs.junitKtx)

  testImplementation(this.project(":beerdomain:fakes"))
  testImplementation(libs.striktCore)
  androidTestImplementation(libs.striktCore)
}
