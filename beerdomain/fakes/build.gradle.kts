plugins { id("billionbeers.kmp.library") }

kotlin {
  android {
    namespace = "com.simtop.beerdomain.fakes"
  }
}

val catalog = billionBeersCatalog()

dependencies {
  commonMainImplementation(this.project(":beerdomain:api"))
  commonMainImplementation(this.project(":core-common"))
  commonMainImplementation(libs.kotlinx.coroutines.core)

  commonTestImplementation(libs.coroutinesTest)
  commonTestImplementation(libs.turbine)

  jvmTestRuntimeOnly(catalog.billionBeersBundle("unitTestJunit5Runtime"))
  jvmTestRuntimeOnly(catalog.billionBeersLibrary("junit-platform-launcher"))
}

tasks.withType<Test>().configureEach {
  useJUnitPlatform()
}
