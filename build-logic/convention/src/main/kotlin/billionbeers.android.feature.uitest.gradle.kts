import org.gradle.accessors.dm.LibrariesForLibs

/**
 * Opts a feature module into the instrumented UI-test tier: one plugin id instead of the six
 * declarations each module would otherwise repeat.
 *
 * Applied on top of `billionbeers.android.feature`, which already supplies the Compose BOM (on the
 * androidTest configuration too) and the Espresso / test-runner baseline from
 * `billionbeers.android.library`. What is left is the managed device, the Compose test rules, and
 * the domain fakes every screen test drives its state with.
 *
 * **This tier is for behaviour that only a real device can show** - fling and layout-driven paging,
 * a real IME, accessibility traversal, state restoration. Anything a JVM test can already prove
 * belongs in `src/test/`, and anything about a static rendering belongs in Paparazzi; both are
 * faster and neither needs an emulator. `:feature:beerslist`'s `InfiniteListHandler` is the model
 * case: its signal logic has seven unit tests in `:presentation_utils`, so the instrumented test
 * covers only the part those cannot reach - the real `LazyColumn` layout feeding the handler.
 *
 * Applying this plugin adds the module to `ciGroupDebugAndroidTest`, so its tests run on every push
 * (AGENTS.md §5). Dynamic-feature modules cannot use this tier - resources declared inside them
 * crash instrumented tests, which is invariant 5.
 */
apply(plugin = "billionbeers.android.managed.device")

val libs = the<LibrariesForLibs>()

dependencies {
    // Fakes for the domain interfaces, so a screen test drives state without the data layer.
    "androidTestImplementation"(project(":beerdomain:fakes"))

    // Shared robot base. Brings the Compose test rules and Espresso with it (they are `api` there),
    // so a screen robot needs nothing else.
    "androidTestImplementation"(project(":testing-utils-android"))

    // Versionless: the Compose BOM is already on androidTestImplementation via
    // billionbeers.android.compose, which billionbeers.android.feature applies.
    "androidTestImplementation"(libs.androidx.ui.test.junit4)

    // Provides the empty ComponentActivity that createComposeRule() hosts the content in. It is a
    // debug-only manifest contribution, hence debugImplementation rather than an androidTest
    // configuration - the test APK reads it from the module under test.
    "debugImplementation"(libs.androidx.ui.test.manifest)
}
