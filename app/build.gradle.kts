plugins {
  id("billionbeers.android.application")
  id("billionbeers.android.compose")
  id("billionbeers.android.metro")
  id("billionbeers.android.managed.device")

  id("billionbeers.duplicate-classes")
  id("billionbeers.unused-dependencies")
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.androidx.baseline.profile)
  alias(libs.plugins.dependency.guard)
}

baselineProfile {
  from(project(":benchmark:baselineprofile"))
  automaticGenerationDuringBuild = true
}

dependencyGuard {
  // Locks the release runtime classpath - everything that actually ships - into a committed
  // baseline (app/dependencies/releaseRuntimeClasspath.txt). Any change to the fully-resolved
  // graph (a new transitive, a shared library bumped by an added SDK - the classic runtime binary
  // incompatibility cause) then fails CI until the baseline is regenerated in the same PR, making
  // it a reviewed decision. Regenerate with `make dependency-guard-baseline`.
  configuration("releaseRuntimeClasspath")
}

android {
  namespace = "com.simtop.billionbeers"
  dynamicFeatures += setOf(":feature:beerdetail", ":feature:beerbrowse")

  // Opt up from the shared default (androidx.test.runner.AndroidJUnitRunner). MockTestRunner
  // substitutes BillionBeersApplication so the instrumented tests can swap in the fake Metro graph
  // - it lives in this module's androidTest source set, so only this module can name it.
  defaultConfig { testInstrumentationRunner = "com.simtop.billionbeers.di.MockTestRunner" }

  packaging {
    resources {
      excludes += "META-INF/versions/9/module-info.class"
      excludes += "a/a.class"
      excludes += "a/b.class"
      excludes += "META-INF/LICENSE.md"
      excludes += "META-INF/LICENSE-notice.md"
    }
  }
}

dependencies {
  implementation(project(":beerdomain:api"))
  androidTestImplementation(project(":beerdomain:fakes"))
  testImplementation(project(":testing-utils"))
  androidTestImplementation(project(":testing-utils"))
  implementation(project(":feature:beerslist"))
  implementation(project(":feature:beersearch"))
  androidTestImplementation(project(":feature:beerdetail"))
  androidTestImplementation(project(":feature:beerbrowse"))
  implementation(project(":core"))
  implementation(project(":core:designsystem"))
  implementation(project(":navigation"))
  implementation(project(":beer_data"))
  implementation(project(":beer_database"))
  implementation(project(":beer_network"))
  implementation(project(":presentation_utils"))

  implementation(libs.androidPlayCore)
  implementation(libs.androidPlayCoreKtx)
  implementation(libs.androidxActivityCompose)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.navigation3.ui)
  // appcompat stays: the manifest's @style/AppTheme resolves to a Theme.AppCompat parent
  // (core/src/main/res/values/styles.xml), which no import scan can see.
  implementation(libs.appcompat)
  testImplementation(libs.striktCore)
  androidTestImplementation(libs.striktCore)

  androidTestImplementation("androidx.compose.ui:ui-test-junit4")
  debugImplementation("androidx.compose.ui:ui-test-manifest")

  androidTestImplementation(libs.roomRuntime)
}
