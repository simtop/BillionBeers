package com.simtop.billionbeers.releasesmoke

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Proves the genuinely minified target can launch and show its public launcher window. */
class ReleaseLaunchSmokeTest {

  @Test
  fun minifiedAppLaunchesAndShowsItsLauncherWindow() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val testContext = instrumentation.context
    val launchIntent =
      requireNotNull(testContext.packageManager.getLaunchIntentForPackage(TARGET_PACKAGE)) {
        "No launcher activity found for $TARGET_PACKAGE"
      }
    val expectedComponent =
      requireNotNull(launchIntent.resolveActivity(testContext.packageManager)) {
        "Launcher intent does not resolve for $TARGET_PACKAGE"
      }
    val device = UiDevice.getInstance(instrumentation)

    launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)

    try {
      testContext.startActivity(launchIntent)
      assertTrue(
        "Launcher window for $TARGET_PACKAGE did not appear",
        device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE).depth(0)), LAUNCH_TIMEOUT_MS),
      )
      assertEquals(expectedComponent.packageName, device.currentPackageName)
    } finally {
      device.pressHome()
    }
  }

  private companion object {
    const val TARGET_PACKAGE = "com.simtop.billionbeers"
    const val LAUNCH_TIMEOUT_MS = 10_000L
  }
}
