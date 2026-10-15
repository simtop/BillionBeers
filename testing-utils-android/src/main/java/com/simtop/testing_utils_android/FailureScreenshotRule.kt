package com.simtop.testing_utils_android

import android.graphics.Bitmap
import android.os.Environment
import android.util.Base64
import android.util.Log
import androidx.test.core.app.takeScreenshot
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Saves a device screenshot when an instrumented test fails. */
class FailureScreenshotRule(private val emitForHostArtifact: Boolean = true) : TestWatcher() {
  override fun starting(description: Description) {
    activeDescription.set(description)
    activeRule.set(this)
  }

  override fun failed(e: Throwable?, description: Description) {
    if (e != null && capturedFailure.get() === e) {
      capturedFailure.remove()
      return
    }
    capture(e, description)
  }

  override fun finished(description: Description) {
    activeDescription.remove()
    activeRule.remove()
    capturedFailure.remove()
  }

  private fun capture(e: Throwable?, description: Description) {
    try {
      val instrumentation = InstrumentationRegistry.getInstrumentation()
      val context = instrumentation.targetContext
      val picturesDirectory = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
      val outputDirectory = File(picturesDirectory ?: context.filesDir, SCREENSHOT_DIRECTORY)
      check(outputDirectory.mkdirs() || outputDirectory.isDirectory) {
        "Could not create screenshot directory $outputDirectory"
      }

      val testName =
        "${description.className.substringAfterLast('.')}-${description.methodName}"
          .replace(INVALID_FILENAME_CHARACTERS, "_")
      val screenshotId = System.currentTimeMillis().toString()
      val screenshotFile = File(outputDirectory, "$testName-$screenshotId.png")
      val screenshotBytes =
        ByteArrayOutputStream().use { output ->
          check(
            takeScreenshot().compress(Bitmap.CompressFormat.PNG, PNG_COMPRESSION_QUALITY, output)
          ) {
            "Could not encode screenshot"
          }
          output.toByteArray()
        }
      FileOutputStream(screenshotFile).use { it.write(screenshotBytes) }
      if (emitForHostArtifact) {
        emitScreenshotForHostArtifact(screenshotId, testName, screenshotBytes)
      }
      Log.e(
        TAG,
        "Failure screenshot for ${description.displayName}: ${screenshotFile.absolutePath}",
        e,
      )
    } catch (captureError: IOException) {
      logCaptureFailure(description, captureError)
    } catch (captureError: IllegalStateException) {
      logCaptureFailure(description, captureError)
    } catch (captureError: SecurityException) {
      logCaptureFailure(description, captureError)
    }
  }

  private fun logCaptureFailure(description: Description, captureError: Throwable) {
    Log.e(
      TAG,
      "Could not capture failure screenshot for ${description.displayName}",
      captureError,
    )
  }

  private fun emitScreenshotForHostArtifact(
    screenshotId: String,
    testName: String,
    screenshotBytes: ByteArray,
  ) {
    val chunks = Base64.encodeToString(screenshotBytes, Base64.NO_WRAP).chunked(LOG_CHUNK_SIZE)
    Log.i(TAG, "SCREENSHOT_BEGIN|$screenshotId|$testName|${chunks.size}")
    chunks.forEachIndexed { index, chunk ->
      Log.i(TAG, "SCREENSHOT_CHUNK|$screenshotId|$index|$chunk")
    }
    Log.i(TAG, "SCREENSHOT_END|$screenshotId")
  }

  companion object {
    const val TAG = "FailureScreenshot"
    const val SCREENSHOT_DIRECTORY = "failure-screenshots"
    const val LOG_CHUNK_SIZE = 3_000
    const val PNG_COMPRESSION_QUALITY = 100
    val INVALID_FILENAME_CHARACTERS = Regex("[^A-Za-z0-9._-]")
    private val activeDescription = ThreadLocal<Description>()
    private val activeRule = ThreadLocal<FailureScreenshotRule>()
    private val capturedFailure = ThreadLocal<Throwable>()

    /** Captures before an ActivityScenario closes and replaces the failure with a blank screen. */
    fun captureBeforeActivityClose(failure: Throwable) {
      val description = activeDescription.get() ?: return
      capturedFailure.set(failure)
      activeRule.get()?.capture(failure, description)
    }
  }
}

/** Keeps the Compose host alive while the failure rule captures the test's screen. */
fun withFailureScreenshot(composeRule: TestRule): TestRule =
  RuleChain.outerRule(composeRule).around(FailureScreenshotRule())
