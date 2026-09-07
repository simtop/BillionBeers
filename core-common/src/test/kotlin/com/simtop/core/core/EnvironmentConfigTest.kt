package com.simtop.core.core

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class EnvironmentConfigTest {

  @Test
  fun `production endpoint passes release validation`() {
    val config = EnvironmentConfig(apiBaseUrl = "https://api.brewbuddy.dev/")

    assertSame(config, config.validateForRelease("https://api.brewbuddy.dev/"))
  }

  @Test
  fun `debug endpoints remain valid until release validation is requested`() {
    val config = EnvironmentConfig(apiBaseUrl = "http://localhost:8080/")

    // Debug hosts may use local HTTP servers; the app graph is responsible for calling the guard
    // only for non-debug builds.
    assertIllegalArgument { config.validateForRelease("https://api.brewbuddy.dev/") }
  }

  @Test
  fun `release validation rejects cleartext endpoints`() {
    assertReleaseRejected("http://api.brewbuddy.dev/")
  }

  @Test
  fun `release validation rejects localhost and loopback endpoints`() {
    assertReleaseRejected("https://localhost/")
    assertReleaseRejected("https://127.0.0.1/")
    assertReleaseRejected("https://[::1]/")
  }

  @Test
  fun `release validation rejects an endpoint other than configured production`() {
    assertReleaseRejected(
      baseUrl = "https://staging.brewbuddy.dev/",
      expectedApiBaseUrl = "https://api.brewbuddy.dev/",
    )
  }

  @Test
  fun `timeouts are part of the typed environment`() {
    val config =
      EnvironmentConfig(
        apiBaseUrl = "https://api.brewbuddy.dev/",
        connectTimeout = 3.seconds,
        readTimeout = 7.seconds,
        writeTimeout = 11.seconds,
      )

    assertEquals(3.seconds, config.connectTimeout)
    assertEquals(7.seconds, config.readTimeout)
    assertEquals(11.seconds, config.writeTimeout)
  }

  @Test
  fun `base URL must be an absolute URL ending in a slash`() {
    assertIllegalArgument { EnvironmentConfig("brewbuddy.dev") }
    assertIllegalArgument {
      EnvironmentConfig("https://api.brewbuddy.dev")
    }
    assertIllegalArgument { EnvironmentConfig("ftp://api.brewbuddy.dev/") }
  }

  @Test
  fun `accepts case-insensitive HTTP schemes`() {
    val config = EnvironmentConfig("HTTPS://api.brewbuddy.dev/")
    EnvironmentConfig("HTTP://api.brewbuddy.dev/")

    assertSame(config, config.validateForRelease("HTTPS://api.brewbuddy.dev/"))
  }

  @Test
  fun `rejects a URL with a query even when it has no fragment`() {
    assertIllegalArgument { EnvironmentConfig("https://api.brewbuddy.dev/?token=secret") }
  }

  @Test
  fun `rejects infinite timeouts`() {
    assertIllegalArgument {
      EnvironmentConfig("https://api.brewbuddy.dev/", connectTimeout = Duration.INFINITE)
    }
  }

  @Test
  fun `rejects every supported localhost form`() {
    assertReleaseRejected("https://localhost/")
    assertReleaseRejected("https://service.localhost/")
    assertReleaseRejected("https://[::1]/")
    assertReleaseRejected("https://127.0.0.1/")
    assertReleaseRejected("https://0.0.0.0/")
  }

  private fun assertReleaseRejected(
    baseUrl: String,
    expectedApiBaseUrl: String = baseUrl,
  ) {
    assertIllegalArgument {
      EnvironmentConfig(apiBaseUrl = baseUrl).validateForRelease(expectedApiBaseUrl)
    }
  }

  private fun assertIllegalArgument(block: () -> Unit) {
    try {
      block()
      fail("Expected IllegalArgumentException")
    } catch (_: IllegalArgumentException) {
      // Expected.
    }
  }
}
