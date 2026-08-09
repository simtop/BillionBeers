import com.android.build.api.dsl.CommonExtension
import kotlin.text.toInt

apply(plugin = "billionbeers.kotlin.options")

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

val PROJECT_COMPILE_SDK = libs.versions.compileSdk.get().toInt()
val PROJECT_MIN_SDK = libs.versions.minSdk.get().toInt()

// One block, not four. `com.android.base` is applied by every AGP plugin (application, library,
// dynamic-feature and test), and in AGP 9 `CommonExtension` is a non-generic supertype of all four
// concrete extensions - so a single `configure<CommonExtension>` reaches every module type. The
// shared body lives in AndroidCommon.kt.
pluginManager.withPlugin("com.android.base") {
  extensions.configure<CommonExtension> {
    configureBillionBeersAndroid(PROJECT_COMPILE_SDK, PROJECT_MIN_SDK)
  }
}

// Coverage is the one setting that is not universal: `com.android.test` never had it, because that
// tier produces a standalone test APK with no coverage story.
//
// The tiers that opt *in* are listed, rather than excluding the one that opts out, and that is
// load-bearing. Skipping the assignment in a second `withPlugin("com.android.test")` block would
// not work: the base block above has already set the value by then and nothing unsets it, so the
// exclusion would silently do nothing. Measured before this was fixed - a macrobenchmark module
// reported jacocoVersion 0.8.13. Listing the opt-ins also makes it independent of the order the
// plugins happen to be applied in.
listOf("com.android.application", "com.android.library", "com.android.dynamic-feature").forEach {
  pluginId ->
  pluginManager.withPlugin(pluginId) {
    extensions.configure<CommonExtension> { testCoverage.jacocoVersion = PROJECT_JACOCO_VERSION }
  }
}
