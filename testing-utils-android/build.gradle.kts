plugins { id("billionbeers.android.library") }

android { namespace = "com.simtop.testing_utils_android" }

// The robot base class lives in `main`, not `androidTest`: this module *is* test code, consumed by
// other modules' androidTest configurations. That is the same shape as :testing-utils (pure JVM)
// and :beerdomain:fakes - a sibling module rather than Gradle's java-test-fixtures, per ADR 0001.
//
// Everything a robot subclass touches is `api`, so a consumer gets the Compose test rules and
// Espresso transitively from one androidTestImplementation line.
dependencies {
  api(platform(libs.androidxComposeBom))
  api(libs.androidx.ui.test.junit4)
  api(libs.espressoCore)
  api(libs.testRunner)
  api(libs.androidx.annotation)
}
