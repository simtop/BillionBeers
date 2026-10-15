package com.simtop.billionbeers

import android.os.Environment
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.simtop.billionbeers.presentation.MainActivity
import com.simtop.billionbeers.utils.useWithFailureScreenshot
import com.simtop.testing_utils_android.FailureScreenshotRule
import java.io.File
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FailureScreenshotRuleTest {
  @get:Rule
  val failureScreenshots =
    FailureScreenshotRule(
      emitForHostArtifact =
        InstrumentationRegistry.getArguments()
          .getString("VERIFY_FAILURE_SCREENSHOT_ARTIFACT")
          .toBoolean()
    )

  @Test
  fun scenarioFailureCapturesBeforeActivityClosesAndKeepsTheOriginalFailure() {
    val failure = AssertionError("synthetic failure for screenshot verification")
    val startTime = System.currentTimeMillis()
    var observedFailure: Throwable? = null
    try {
      ActivityScenario.launch(MainActivity::class.java).useWithFailureScreenshot {
        throw failure
      }
    } catch (error: Throwable) {
      observedFailure = error
    }

    assertSame(failure, observedFailure)
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val outputDirectory =
      File(
        context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir,
        "failure-screenshots",
      )
    assertTrue(
      "The failure rule should persist a PNG under $outputDirectory",
      outputDirectory.listFiles().orEmpty().any {
        it.name.startsWith("FailureScreenshotRuleTest-") &&
          it.extension == "png" &&
          it.lastModified() >= startTime
      },
    )
  }
}
