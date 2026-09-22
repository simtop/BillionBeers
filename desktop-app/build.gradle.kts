plugins {
  id("billionbeers.jvm.library")
  id("org.jetbrains.compose")
  id("org.jetbrains.kotlin.plugin.compose")
  id("dev.zacsweers.metro")
}

val catalog = billionBeersCatalog()

compose.desktop {
  application {
    mainClass = "com.simtop.billionbeers.desktop.MainKt"
    nativeDistributions {
      targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg)
      packageName = "BillionBeers"
      packageVersion = "1.0.0"
      macOS {
        bundleID = "com.simtop.billionbeers.desktop"
      }
    }
  }
}

dependencies {
  implementation(project(":shared:app"))
  implementation(project(":shared:beerbrowse"))
  implementation(project(":shared:beerdetail"))
  implementation(compose.desktop.currentOs)
  implementation(compose.material3)
  implementation(compose.materialIconsExtended)
  implementation(libs.coil3)
  implementation(catalog.billionBeersLibrary("coil3-network"))
  implementation(catalog.billionBeersLibrary("kotlinx-coroutines-swing"))
  implementation(project(":beer_data"))
  implementation(project(":beer_database"))
  implementation(project(":beer_network"))
  implementation(project(":beerdomain:api"))
  implementation(project(":core-common"))
  implementation(project(":shared:favorites"))
  implementation(catalog.billionBeersLibrary("lifecycle-viewmodel"))
  implementation(libs.ktorClientContentNegotiation)
  implementation(libs.ktorClientOkhttp)
  implementation(libs.ktorSerializationKotlinxJson)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.roomRuntime)
  implementation(libs.sqliteBundled)

  testImplementation(libs.okhttp3Mockwebserver)
  testImplementation(project(":beer_network:fixtures"))
}

tasks.withType<JavaExec>().configureEach {
  standardInput = System.`in`
}
