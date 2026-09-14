plugins { id("billionbeers.kmp.library") }

val catalog = billionBeersCatalog()

dependencies {
  commonMainImplementation(libs.kotlinx.coroutines.core)
  commonMainImplementation(libs.androidx.annotation)

  jvmTestImplementation(libs.junit)
  jvmTestImplementation(catalog.billionBeersBundle("unitTestJunit5"))
  jvmTestImplementation(libs.coroutinesTest)
  jvmTestImplementation(libs.turbine)
  jvmTestRuntimeOnly(catalog.billionBeersBundle("unitTestJunit5Runtime"))
  jvmTestRuntimeOnly(catalog.billionBeersLibrary("junit-platform-launcher"))
}

tasks.withType<Test>().configureEach {
  useJUnitPlatform()
}
