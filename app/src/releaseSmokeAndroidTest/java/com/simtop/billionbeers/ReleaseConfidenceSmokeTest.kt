package com.simtop.billionbeers

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReleaseConfidenceSmokeTest {

  private lateinit var server: DeterministicApiServer

  @Before
  fun startDeterministicApi() {
    server = DeterministicApiServer().also { it.start() }
  }

  @After
  fun stopDeterministicApi() {
    if (::server.isInitialized) {
      server.shutdown()
    }
  }

  @Test
  fun minifiedAppLoadsMapsNavigatesAndPersistsAvailability() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val device = UiDevice.getInstance(instrumentation)

    launchApp(device)
    assertTrue(
      "Mapped fixture beer did not appear in the catalog",
      device.wait(Until.hasObject(By.text(FIXTURE_NAME)), UI_TIMEOUT_MS),
    )
    assertTrue("The app never requested the deterministic catalog", server.requestCount > 0)
    assertTrue(
      "Mapped tagline did not appear in the catalog",
      device.hasObject(By.text(FIXTURE_TAGLINE)),
    )
    assertTrue("Initial availability mapping was not shown", device.hasObject(By.text("Available")))

    clickText(device, FIXTURE_NAME)
    assertTrue(
      "Dynamic detail route did not render",
      device.wait(Until.hasObject(By.text("Mark as Empty")), UI_TIMEOUT_MS),
    )
    assertTrue(
      "Mapped detail description did not appear",
      device.hasObject(By.text(FIXTURE_DESCRIPTION)),
    )

    clickText(device, "Mark as Empty")
    assertTrue(
      "Availability edit did not update the detail screen",
      device.wait(Until.hasObject(By.text("Refill Barrels")), UI_TIMEOUT_MS),
    )

    device.pressBack()
    assertTrue(
      "Availability edit did not flow back to the catalog",
      device.wait(Until.hasObject(By.text("Out of stock")), UI_TIMEOUT_MS),
    )

    device.pressHome()
    device.executeShellCommand("am kill $TARGET_PACKAGE")
    launchApp(device)
    assertTrue(
      "Room availability edit did not survive relaunch",
      device.wait(Until.hasObject(By.text("Out of stock")), UI_TIMEOUT_MS),
    )
  }

  private fun launchApp(device: UiDevice) {
    val testContext = InstrumentationRegistry.getInstrumentation().context
    val launchIntent =
      requireNotNull(testContext.packageManager.getLaunchIntentForPackage(TARGET_PACKAGE)) {
        "No launcher activity found for $TARGET_PACKAGE"
      }
    launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
    testContext.startActivity(launchIntent)
    assertTrue(
      "Launcher window for $TARGET_PACKAGE did not appear",
      device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE).depth(0)), UI_TIMEOUT_MS),
    )
  }

  private fun clickText(device: UiDevice, text: String) {
    val target = requireNotNull(device.findObject(By.text(text))) { "Text not found: $text" }
    val bounds = target.visibleBounds
    assertTrue("Could not click text: $text", device.click(bounds.centerX(), bounds.centerY()))
  }

  private class DeterministicApiServer {
    private val serverSocket = ServerSocket(SERVER_PORT)
    private val requests = AtomicInteger()
    private val acceptThread =
      thread(start = false, name = "release-smoke-api") {
        while (!serverSocket.isClosed) {
          try {
            serverSocket.accept().use(::respond)
          } catch (_: Exception) {
            if (!serverSocket.isClosed) {
              throw IllegalStateException("Deterministic API server stopped unexpectedly")
            }
          }
        }
      }

    val requestCount: Int
      get() = requests.get()

    fun start() {
      acceptThread.start()
    }

    fun shutdown() {
      serverSocket.close()
      acceptThread.join(SERVER_SHUTDOWN_TIMEOUT_MS)
    }

    private fun respond(socket: Socket) {
      val input = socket.getInputStream().bufferedReader()
      val requestLine = input.readLine() ?: return
      while (input.readLine().orEmpty().isNotEmpty()) {}

      val path = requestLine.substringAfter(' ').substringBefore(' ').substringBefore('?')
      val isBeersRequest = path == "/beers"
      if (isBeersRequest) {
        requests.incrementAndGet()
      }
      val body = if (isBeersRequest) FIXTURE_BEERS_JSON else ""
      val bodyBytes = body.toByteArray(StandardCharsets.UTF_8)
      val status = if (isBeersRequest) "200 OK" else "404 Not Found"
      val responseHeaders =
        "HTTP/1.1 $status\r\n" +
          "Content-Type: application/json\r\n" +
          "X-Total-Count: 1\r\n" +
          "Content-Length: ${bodyBytes.size}\r\n" +
          "Connection: close\r\n\r\n"
      socket.getOutputStream().use { output ->
        output.write(responseHeaders.toByteArray(StandardCharsets.UTF_8))
        output.write(bodyBytes)
        output.flush()
      }
    }
  }

  private companion object {
    const val TARGET_PACKAGE = "com.simtop.billionbeers"
    const val SERVER_PORT = 18080
    const val SERVER_SHUTDOWN_TIMEOUT_MS = 5_000L
    const val UI_TIMEOUT_MS = 15_000L
    const val FIXTURE_NAME = "Smoke Test Lager"
    const val FIXTURE_TAGLINE = "A deterministic release path."
    const val FIXTURE_DESCRIPTION = "The release smoke server supplied this description."
    val FIXTURE_BEERS_JSON =
      """
      [{
        "id":"release-smoke-beer",
        "name":"Smoke Test Lager",
        "abv":5.5,
        "ibu":42.0,
        "image":{"url":""},
        "available":true,
        "translations":[{
          "language":{"code":"en"},
          "slogan":"A deterministic release path.",
          "description":"The release smoke server supplied this description."
        }],
        "food_pairing":[],
        "typology":{"name":"Lager"},
        "brewery":{"name":"Smoke Brewery"}
      }]
      """
        .trimIndent()
  }
}
