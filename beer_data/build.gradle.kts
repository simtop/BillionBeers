plugins {
  id("billionbeers.kmp.library")
  id("dev.zacsweers.metro")
}

val catalog = billionBeersCatalog()

kotlin {
  android {
    namespace = "com.simtop.beer_data"
  }
}

dependencies {
  commonMainImplementation(project(":core-common"))
  commonMainImplementation(project(":beerdomain:api"))
  commonMainImplementation(project(":beer_storage:api"))
  commonMainImplementation(project(":beer_network"))
  commonMainImplementation(project(":beer_network:api"))
  commonMainImplementation(libs.kotlinx.coroutines.core)
  commonMainImplementation(libs.kotlinx.serialization.json)

  commonTestImplementation(libs.coroutinesTest)
  commonTestImplementation(libs.turbine)

  jvmTestImplementation(libs.striktCore)
  jvmTestImplementation(catalog.billionBeersBundle("unitTestJunit5"))
  jvmTestRuntimeOnly(catalog.billionBeersBundle("unitTestJunit5Runtime"))
  jvmTestRuntimeOnly(catalog.billionBeersLibrary("junit-platform-launcher"))
}

tasks.withType<Test>().configureEach {
  useJUnitPlatform()
}
