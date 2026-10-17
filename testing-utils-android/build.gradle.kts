plugins { id("billionbeers.android.library") }

android { namespace = "com.simtop.testing_utils_android" }

// The robot base class lives in `main`, not `androidTest`: this module *is* test code, consumed by
// other modules' androidTest configurations. That is the same shape as :testing-utils (pure JVM)
// and :beerdomain:fakes - a sibling module rather than Gradle's java-test-fixtures, per ADR 0001.
//
// The public robot/rule signatures expose ComposeTestRule, StringRes, and JUnit rule types. Its
// Espresso, runner and test-core helpers stay internal; Android consumer conventions supply them
// through the shared instrumentedTest bundle.
dependencies {
  implementation(platform(libs.androidxComposeBom))
  api(libs.androidx.ui.test.junit4)
  implementation(libs.bundles.androidTestHelperInternals)
  api(libs.androidx.annotation)
  api(libs.junit)
}
