import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.DynamicFeatureExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.ManagedDevices
import com.android.build.api.dsl.ManagedVirtualDevice

/**
 * Gradle Managed Devices: the test emulator is declared in the build, so Gradle provisions, boots,
 * runs and tears it down. The same device spec runs locally and in CI, with no hand-started AVD.
 *
 * Two devices, deliberately:
 *
 * - **`atdApi35`** - the fast lane. ATD (Automated Test Device) images are headless and stripped
 *   of most system apps, so they boot faster and use far less RAM than a full image. API 35 is the
 *   *highest* level Google publishes ATD images for, so this is as new as the fast lane can get.
 * - **`pixel9Api37`** - the newest lane, one API above the project's `targetSdk` (36), which is
 *   what makes it forward-compatibility coverage rather than a duplicate of the fast lane. There
 *   are no ATD images for 37, so this one pays full price.
 *
 *     ./gradlew :app:atdApi35DebugAndroidTest       # one device, one module
 *     ./gradlew :app:ciGroupDebugAndroidTest        # both devices, one module
 *     ./gradlew ciGroupDebugAndroidTest             # both devices, every opted-in module (CI)
 *     ./gradlew allDevicesDebugAndroidTest          # every device, every opted-in module
 *
 * Opt in per module (`id("billionbeers.android.managed.device")`) rather than applying this from
 * the shared convention - only modules with an `androidTest` source set have anything to run, and
 * applying it everywhere would generate dead tasks in every module.
 */
fun ManagedDevices.configureBillionBeersDevices() {
    // Task names derive from the device names: `atdApi35DebugAndroidTest`, `atdApi35Setup`, ...
    val fastLane = localDevices.create("atdApi35") {
        device = "Pixel 6"
        sdkVersion = 35
        // google_atd, not aosp_atd: the app depends on Play Core (SplitInstall) and needs the
        // Google APIs present. ATD images have Google APIs but no Play Store - fine here, since
        // dynamic features are staged by bundletool local-testing rather than fetched from Play.
        systemImageSource = "google_atd"
    }

    val newest = localDevices.create("pixel9Api37") {
        device = "Pixel 9"
        sdkVersion = 37
        // API 37 ships only google_apis / google_apis_playstore - there are no ATD images and no
        // aosp images for it yet. Use google_apis: playstore images are not debuggable, so
        // instrumented tests cannot run on them.
        systemImageSource = "google_apis"
        // Required, not cosmetic. Every API 37 image is 16 KB-page (`..._ps16k`), but AGP 9.2.1
        // cannot yet infer that from sdkVersion 37 and would resolve a 4 KB image that does not
        // exist. Forcing the alignment is what makes the device resolvable at all.
        // Revisit when AGP learns API 37's page size - the config-time warning will disappear.
        pageAlignment = ManagedVirtualDevice.PageAlignment.FORCE_16KB_PAGES
    }

    // The set CI runs. Kept as an explicit group so the CI job never has to name devices - if the
    // lanes change, they change here and the workflow is untouched.
    groups.create("ci") {
        targetDevices.add(fastLane)
        targetDevices.add(newest)
    }
}

pluginManager.withPlugin("com.android.application") {
    extensions.configure<ApplicationExtension> {
        testOptions.managedDevices.configureBillionBeersDevices()
    }
}
pluginManager.withPlugin("com.android.library") {
    extensions.configure<LibraryExtension> {
        testOptions.managedDevices.configureBillionBeersDevices()
    }
}
pluginManager.withPlugin("com.android.dynamic-feature") {
    extensions.configure<DynamicFeatureExtension> {
        testOptions.managedDevices.configureBillionBeersDevices()
    }
}
