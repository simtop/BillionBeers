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
//
// The one behavioural split is test coverage, which `com.android.test` never had: that plugin is
// checked separately below and re-configured with it off.
pluginManager.withPlugin("com.android.base") {
  extensions.configure<CommonExtension> {
    configureBillionBeersAndroid(PROJECT_COMPILE_SDK, PROJECT_MIN_SDK)
  }
}

pluginManager.withPlugin("com.android.test") {
  extensions.configure<CommonExtension> {
    configureBillionBeersAndroid(PROJECT_COMPILE_SDK, PROJECT_MIN_SDK, withTestCoverage = false)
  }
}
