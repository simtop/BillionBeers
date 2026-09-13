package com.simtop.billionbeers.buildlogic

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class KmpBrowserFunctionalTest {

  @TempDir lateinit var testProjectDir: Path

  @Test
  fun `Wasm fixture discovers browser tasks`() {
    writeWasmFixture()

    val result = runner().withArguments("tasks", "--all", "--stacktrace").build()

    assertTrue(result.output.contains("wasmJs"), result.output)
    assertTrue(result.output.contains("browser"), result.output)
  }

  @Test
  fun `Wasm fixture compiles and runs browser tests`() {
    writeWasmFixture()

    val result = runner().withArguments("wasmJsBrowserTest", "--stacktrace").build()

    assertTrue(result.task(":wasmJsBrowserTest")?.outcome == TaskOutcome.SUCCESS, result.output)
  }

  @Test
  fun `Chrome proves IndexedDB and Fetch behavior over HTTP`() {
    val browserReport = AtomicReference<String?>()
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/result") { exchange ->
      browserReport.set(
        URLDecoder.decode(exchange.requestURI.query?.removePrefix("report=") ?: "", "UTF-8")
      )
      exchange.sendResponseHeaders(204, -1)
      exchange.close()
    }
    server.createContext("/") { exchange ->
      val path = exchange.requestURI.path
      val body =
        when (path) {
          "/" -> browserHarnessHtml().toByteArray()
          "/api" -> """{"items":[{"id":1}]}""".toByteArray()
          "/redirect" -> {
            exchange.responseHeaders.add("Location", "/api")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
            return@createContext
          }
          "/pixel" -> "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1\" height=\"1\"></svg>".toByteArray()
          else -> "not found".toByteArray()
        }
      if (path == "/api") {
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.responseHeaders.add("X-Total-Count", "1")
      } else if (path == "/") {
        exchange.responseHeaders.add("Content-Type", "text/html")
      } else if (path == "/pixel") {
        exchange.responseHeaders.add("Content-Type", "image/svg+xml")
      }
      val bytes = body
      exchange.sendResponseHeaders(200, bytes.size.toLong())
      exchange.responseBody.use {
        it.write(bytes)
        it.flush()
        if (path == "/") Thread.sleep(10_000)
      }
    }
    server.executor = Executors.newCachedThreadPool()
    server.start()

    try {
      val chrome =
        System.getenv("CHROME_BIN")
          ?: "/Users/simontopchyan/.cache/puppeteer/chrome-headless-shell/mac_arm-151.0.7922.47/chrome-headless-shell-mac-arm64/chrome-headless-shell"
      val process =
        ProcessBuilder(
            chrome,
            "--headless",
            "--disable-gpu",
            "--dump-dom",
            "--virtual-time-budget=30000",
            "--timeout=30000",
            "http://127.0.0.1:${server.address.port}/",
          )
          .redirectErrorStream(true)
          .start()
      repeat(60) {
        if (browserReport.get() != null || !process.isAlive) return@repeat
        Thread.sleep(500)
      }
      process.destroyForcibly()
      val report = browserReport.get()
      println("T1.4_BROWSER_REPORT=$report")
      assertTrue(report != null, "Chrome produced no browser report")
      assertTrue(report!!.contains("INDEXED_DB_COMMIT_REOPEN=PASS"), report)
      assertTrue(report.contains("INDEXED_DB_ABORT_ABSENT=PASS"), report)
      assertTrue(report.contains("FETCH_LOCAL=PASS"), report)
      assertTrue(report.contains("FETCH_HEADER=PASS"), report)
      assertTrue(report.contains("FETCH_REDIRECT=PASS"), report)
      assertTrue(report.contains("IMAGE_RESOURCE=PASS") || report.contains("IMAGE_RESOURCE=NETWORK_ERROR"), report)
      assertTrue(
        report.contains("LIVE_API=PASS") ||
          report.contains("LIVE_API=CORS_BLOCKED") ||
          report.contains("LIVE_API=NETWORK_ERROR") ||
          report.contains("LIVE_API=HTTP_FAILURE"),
        report,
      )
    } finally {
      server.stop(0)
    }
  }

  private fun browserHarnessHtml(): String =
    """
    <!doctype html>
    <html><body><pre id="result">RUNNING</pre>
    <script>
    const image = new Image();
    image.onload = () => window.imageLoaded = true;
    image.onerror = () => window.imageLoaded = false;
    image.src = '/pixel';
    const result = document.getElementById('result');
    const report = {};
    const dbName = 'kmp-feasibility-' + crypto.randomUUID();
    const openDb = (version) => new Promise((resolve, reject) => {
      const request = indexedDB.open(dbName, version);
      request.onupgradeneeded = () => request.result.createObjectStore('records', {keyPath: 'id'});
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
    const txDone = (tx) => new Promise((resolve, reject) => {
      tx.oncomplete = resolve;
      tx.onabort = () => reject(new Error('aborted'));
      tx.onerror = () => reject(tx.error || new Error('transaction failed'));
    });
    (async () => {
      try {
        let db = await openDb(1);
        let tx = db.transaction('records', 'readwrite');
        tx.objectStore('records').put({id: 'first', value: 'one'});
        tx.objectStore('records').put({id: 'second', value: 'two'});
        await txDone(tx);
        db.close();
        db = await openDb(1);
        const readTx = db.transaction('records', 'readonly');
        const first = await new Promise((resolve, reject) => {
          const request = readTx.objectStore('records').get('first');
          request.onsuccess = () => resolve(request.result);
          request.onerror = () => reject(request.error);
        });
        const second = await new Promise((resolve, reject) => {
          const request = readTx.objectStore('records').get('second');
          request.onsuccess = () => resolve(request.result);
          request.onerror = () => reject(request.error);
        });
        report.INDEXED_DB_COMMIT_REOPEN = first.value === 'one' && second.value === 'two' ? 'PASS' : 'FAIL';
        const abortTx = db.transaction('records', 'readwrite');
        abortTx.objectStore('records').put({id: 'sentinel', value: 'must-not-persist'});
        const aborted = new Promise(resolve => { abortTx.onabort = resolve; });
        abortTx.abort();
        await aborted;
        db.close();
        db = await openDb(1);
        const verifyTx = db.transaction('records', 'readonly');
        const sentinel = await new Promise((resolve, reject) => {
          const request = verifyTx.objectStore('records').get('sentinel');
          request.onsuccess = () => resolve(request.result);
          request.onerror = () => reject(request.error);
        });
        report.INDEXED_DB_ABORT_ABSENT = sentinel === undefined ? 'PASS' : 'FAIL';
        db.close();
        const local = await fetch('/api');
        const localBody = await local.text();
        report.FETCH_LOCAL = local.status === 200 && localBody.includes('items') ? 'PASS' : 'HTTP_FAILURE';
        report.FETCH_HEADER = local.headers.get('X-Total-Count') === '1' ? 'PASS' : 'HEADER_NOT_EXPOSED';
        const redirected = await fetch('/redirect');
        report.FETCH_REDIRECT = redirected.url.endsWith('/api') && redirected.status === 200 ? 'PASS' : 'HTTP_FAILURE';
        try {
          const controller = new AbortController();
          setTimeout(() => controller.abort(), 5000);
          const live = await fetch('https://api.brewbuddy.dev/', {signal: controller.signal});
          const liveBody = await live.text();
          report.LIVE_API = live.status >= 200 && live.status < 300 && liveBody.length > 0 ? 'PASS' : 'HTTP_FAILURE';
        } catch (error) {
          report.LIVE_API = String(error).includes('CORS') ? 'CORS_BLOCKED' : 'NETWORK_ERROR';
        }
        await new Promise(resolve => setTimeout(resolve, 1000));
        report.IMAGE_RESOURCE = window.imageLoaded === true ? 'PASS' : 'NETWORK_ERROR';
        result.textContent = Object.entries(report).map(([key, value]) => key + '=' + value).join('\\n');
        fetch('/result?report=' + encodeURIComponent(result.textContent));
      } catch (error) {
        report.UNEXPECTED_ERROR = String(error);
        result.textContent = Object.entries(report).map(([key, value]) => key + '=' + value).join('\\n');
        fetch('/result?report=' + encodeURIComponent(result.textContent));
      }
    })();
    setInterval(() => {}, 1000);
    </script></body></html>
    """.trimIndent()

  private fun runner(): GradleRunner =
    GradleRunner.create()
      .withProjectDir(testProjectDir.toFile())
      .withPluginClasspath()
      .withEnvironment(
        mapOf(
          "CHROME_BIN" to
            (System.getenv("CHROME_BIN")
              ?: "/Users/simontopchyan/.cache/puppeteer/chrome-headless-shell/mac_arm-151.0.7922.47/chrome-headless-shell-mac-arm64/chrome-headless-shell")
        )
      )
      .forwardOutput()

  private fun writeWasmFixture() {
    writeSettings()
    copyRepositoryCatalog()
    writeBuildFile(
      """
      plugins {
        id("org.jetbrains.kotlin.multiplatform") version "2.4.10"
        id("dev.zacsweers.metro")
      }

      kotlin {
        wasmJs {
          browser()
          binaries.executable()
        }
        sourceSets {
          commonMain.dependencies {
            implementation("dev.zacsweers.metro:runtime:1.4.2")
            implementation("org.jetbrains.kotlinx:kotlinx-browser:0.3")
          }
          commonTest.dependencies {
            implementation(kotlin("test"))
          }
        }
      }
      """.trimIndent(),
    )
    writeFile(
      "src/commonMain/kotlin/WasmFixture.kt",
      """
      package wasmfixture

      import dev.zacsweers.metro.Inject
      import kotlinx.browser.window
      @Inject
      class WasmService {
        fun value(): String = "metro-wasm-fixture"
      }

      fun wasmFixtureValue(): String = "wasm-fixture"

      fun metroFixtureValue(): String = WasmService().value()

      fun browserOrigin(): String = window.location.origin.toString()
      """.trimIndent(),
    )
    writeFile(
      "src/commonTest/kotlin/WasmFixtureTest.kt",
      """
      package wasmfixture

      import kotlin.test.Test
      import kotlin.test.assertEquals
      import kotlin.test.assertTrue

      class WasmFixtureTest {
        @Test
        fun commonApiRunsInBrowser() {
          assertEquals("wasm-fixture", wasmFixtureValue())
          assertEquals("metro-wasm-fixture", metroFixtureValue())
        }

        @Test
        fun browserEnvironmentIsAvailable() {
          assertTrue(browserOrigin().startsWith("http://"))
        }
      }
      """.trimIndent(),
    )
  }

  private fun copyRepositoryCatalog() {
    val catalog =
      generateSequence(Path.of(System.getProperty("user.dir"))) { it.parent }
        .map { it.resolve("gradle/libs.versions.toml") }
        .firstOrNull { it.exists() }
    check(catalog != null) { "Could not locate the repository version catalog" }
    testProjectDir.resolve("gradle").createDirectories()
    catalog.copyTo(testProjectDir.resolve("gradle/libs.versions.toml"), overwrite = true)
  }

  private fun writeSettings() {
    testProjectDir.resolve("settings.gradle.kts").writeText(
      """
      pluginManagement {
        repositories {
          google()
          mavenCentral()
          gradlePluginPortal()
        }
      }
      dependencyResolutionManagement {
        repositories {
          google()
          mavenCentral()
        }
      }
      rootProject.name = "kmp-browser-test"
      """.trimIndent(),
    )
  }

  private fun writeBuildFile(contents: String) {
    testProjectDir.resolve("build.gradle.kts").writeText(contents)
  }

  private fun writeFile(relativePath: String, contents: String) {
    val file = testProjectDir.resolve(relativePath)
    file.parent.createDirectories()
    file.writeText(contents)
  }
}
