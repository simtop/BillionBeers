import com.android.build.api.dsl.CommonExtension

/**
 * The Android configuration every module gets, regardless of which AGP plugin it applies.
 *
 * **Why this is one function and not four copies.** `billionbeers.android.common` used to repeat
 * fifteen identical lines inside four `pluginManager.withPlugin` blocks, once per extension type.
 * A top-level function in a plain `.kt` file is visible to every precompiled script plugin in this
 * source set — the same mechanism `Versions.kt` already relies on — so sharing needs no migration
 * to binary plugins.
 *
 * **Why property access, not the usual DSL blocks.** In AGP 9 `CommonExtension` is no longer
 * generic (the `CommonExtension<*, *, *, *, *, *>` spelling every pre-9 guide uses will not
 * compile), and it declares *no* `Action`-taking overloads at all — verified with `javap`, the
 * count is zero. So `defaultConfig { }` and `compileOptions { }` do not exist on this type; only
 * `getDefaultConfig()` / `getCompileOptions()` do. Hence `defaultConfig.apply { }`. The concrete
 * subtypes still offer the block form, which is why the old per-type code could use it.
 *
 * `compileSdk`/`minSdk` arrive as parameters because the version catalog accessor
 * (`the<LibrariesForLibs>()`) needs a `Project` receiver, which an extension on `CommonExtension`
 * does not have.
 */
fun CommonExtension.configureBillionBeersAndroid(
  compileSdkVersion: Int,
  minSdkVersion: Int,
  withTestCoverage: Boolean = true,
) {
  compileSdk = compileSdkVersion

  defaultConfig.apply {
    minSdk = minSdkVersion
    // Only opt a module up to the shared runner if it has not named one itself, and treat the
    // ancient framework default as "unset". :app overrides this with MockTestRunner in its own
    // build script; Versions.kt explains why the shared default is the stock runner.
    if (
      testInstrumentationRunner == null ||
        testInstrumentationRunner == "android.test.InstrumentationTestRunner"
    ) {
      testInstrumentationRunner = PROJECT_TEST_RUNNER
    }
  }

  compileOptions.apply {
    sourceCompatibility = PROJECT_JAVA_VERSION
    targetCompatibility = PROJECT_JAVA_VERSION
  }

  // `com.android.test` opts out, matching the original four blocks: that tier produces a standalone
  // test APK and has no coverage story.
  if (withTestCoverage) {
    testCoverage.jacocoVersion = PROJECT_JACOCO_VERSION
  }
}
