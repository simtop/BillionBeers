import com.android.build.api.dsl.TestExtension

plugins {
  id("com.android.test")
  id("billionbeers.android.common")
  id("billionbeers.android.managed.device")
}

val android = the<TestExtension>()

android.apply {
  namespace = "com.simtop.billionbeers.releasesmoke"

  defaultConfig {
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    create("releaseSmoke") {
      matchingFallbacks += "release"
      signingConfig = signingConfigs.getByName("debug")
      isMinifyEnabled = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }

  targetProjectPath = ":app"
}

extensions.configure<com.android.build.api.variant.TestAndroidComponentsExtension> {
  beforeVariants(selector().all()) { variant ->
    variant.enable = variant.buildType == "releaseSmoke"
  }
}

dependencies {
  implementation(platform(libs.androidxComposeBom))
  implementation(this.project(":beerdomain:api"))
  implementation(this.project(":beer_data"))
  implementation(this.project(":beer_database"))
  implementation(this.project(":beer_network"))
  implementation(this.project(":core"))
  implementation(this.project(":navigation"))
  implementation(libs.androidPlayCore)
  implementation("com.google.errorprone:error_prone_annotations:2.41.0")
  implementation(libs.androidx.compose.runtime)
  implementation("androidx.activity:activity:1.13.0")
  implementation(libs.metrox.viewmodel)
  implementation(libs.junit)
  implementation(libs.testRunner)
  implementation(libs.testRules)
  implementation(libs.testCoreKtx)
  implementation(libs.junitKtx)
}
