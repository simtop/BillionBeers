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
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
      testProguardFiles("proguard-rules.pro")
    }
  }

  targetProjectPath = ":app"
  // Run from the test APK so its runner dependencies never need target-app keep rules.
  experimentalProperties["android.experimental.self-instrumenting"] = true
}

extensions.configure<com.android.build.api.variant.TestAndroidComponentsExtension> {
  beforeVariants(selector().all()) { variant ->
    variant.enable = variant.buildType == "releaseSmoke"
  }
}

dependencies {
  implementation(libs.junit)
  implementation(libs.testRunner)
  implementation(libs.uiautomator)
}
