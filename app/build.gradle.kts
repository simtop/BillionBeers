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
  // The AndroidX baseline-profile plugin currently requires a Project object here. Gradle 9.6
  // reports its generic Project-as-dependency notation as deprecated, but the consumer extension's
  // public API is `from(Project)` (see the plugin source). Remove this exception when AndroidX
  // exposes a path/Provider overload.
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

  buildFeatures { buildConfig = true }

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
  implementation(this.project(":beerdomain:api"))
  androidTestImplementation(this.project(":beerdomain:fakes"))
  testImplementation(this.project(":testing-utils"))
  androidTestImplementation(this.project(":testing-utils"))
  androidTestImplementation(this.project(":testing-utils-android"))
  implementation(this.project(":feature:beerslist"))
  implementation(this.project(":feature:beersearch"))
  androidTestImplementation(this.project(":feature:beerdetail"))
  androidTestImplementation(this.project(":feature:beerbrowse"))
  implementation(this.project(":core"))
  implementation(this.project(":core:designsystem"))
  implementation(this.project(":navigation"))
  implementation(this.project(":beer_data"))
  implementation(this.project(":beer_database"))
  implementation(this.project(":beer_network"))
  implementation(this.project(":presentation_utils"))

  implementation(libs.androidPlayCore)
  implementation(libs.androidPlayCoreKtx)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.compose.material3.adaptive.navigation3)
  // appcompat stays: the manifest's @style/AppTheme resolves to a Theme.AppCompat parent
  // (core/src/main/res/values/styles.xml), which no import scan can see.
  implementation(libs.appcompat)
  testImplementation(libs.striktCore)
  androidTestImplementation(libs.striktCore)

  androidTestImplementation(libs.androidx.ui.test.junit4)
  debugImplementation(libs.androidx.ui.test.manifest)

  androidTestImplementation(libs.roomRuntime)
}
