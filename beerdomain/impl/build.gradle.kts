plugins { id("billionbeers.android.library") }

android { namespace = "com.simtop.beerdomain.impl" }

dependencies {
  implementation(project(":beerdomain:api"))
  implementation(project(":core-common"))

  implementation(libs.javax.inject)
  implementation(libs.kotlinx.coroutines.core)

  testImplementation(project(":beerdomain:fakes"))
  testImplementation(libs.junit)
  testImplementation(libs.mockk)
  testImplementation(libs.coroutinesTest)
  testImplementation(libs.turbine)
}
