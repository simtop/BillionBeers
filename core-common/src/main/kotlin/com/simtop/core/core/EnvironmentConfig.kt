package com.simtop.core.core

import java.net.URI
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Runtime configuration for a product's network environment.
 *
 * Endpoints are configuration, not secrets. Hosts can provide different instances of this value for
 * development, staging and production without putting environment-specific constants in the
 * networking implementation or secrets in BuildConfig.
 */
data class EnvironmentConfig(
  val apiBaseUrl: String,
  val connectTimeout: Duration = 10.seconds,
  val readTimeout: Duration = 30.seconds,
  val writeTimeout: Duration = 10.seconds,
) {

  init {
    val uri = parseBaseUrl()
    require(uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", true)) {
      "apiBaseUrl must use HTTP or HTTPS: $apiBaseUrl"
    }
    require(uri.host != null) { "apiBaseUrl must include a host: $apiBaseUrl" }
    require(uri.userInfo == null) { "apiBaseUrl must not include user credentials" }
    require(uri.query == null && uri.fragment == null) {
      "apiBaseUrl must not include a query or fragment"
    }
    require(uri.path.endsWith('/')) {
      "apiBaseUrl must end with '/': $apiBaseUrl"
    }

    requirePositiveFinite(connectTimeout, "connectTimeout")
    requirePositiveFinite(readTimeout, "readTimeout")
    requirePositiveFinite(writeTimeout, "writeTimeout")
  }

  /**
   * Validates the restrictions that apply to a shippable build.
   *
   * Debug builds intentionally do not call this: local HTTP and mock endpoints are useful during
   * development. This method is kept on the shared value rather than in Android BuildConfig code so
   * every host can apply the same release guard.
   */
  fun validateForRelease(expectedApiBaseUrl: String): EnvironmentConfig {
    val uri = parseBaseUrl()
    require(uri.scheme.equals("https", ignoreCase = true)) {
      "Release apiBaseUrl must use HTTPS: $apiBaseUrl"
    }

    val host = requireNotNull(uri.host).normalizeHost()
    require(!host.isLocalhostOrLoopback()) {
      "Release apiBaseUrl must not point to localhost or loopback: $apiBaseUrl"
    }
    require(apiBaseUrl == expectedApiBaseUrl) {
      "Release apiBaseUrl must match the configured production endpoint: $apiBaseUrl"
    }

    return this
  }

  private fun parseBaseUrl(): URI =
    runCatching { URI(apiBaseUrl) }
      .getOrElse { error ->
        throw IllegalArgumentException(
          "apiBaseUrl must be a valid absolute URL: $apiBaseUrl",
          error,
        )
      }

  private companion object {
    private fun requirePositiveFinite(value: Duration, name: String) {
      require(value.isFinite() && value > Duration.ZERO) {
        "$name must be a positive finite duration: $value"
      }
    }

    private fun String.normalizeHost(): String =
      lowercase().removePrefix("[").removeSuffix("]").trimEnd('.')

    private fun String.isLocalhostOrLoopback(): Boolean =
      this == "localhost" ||
        endsWith(".localhost") ||
        this == "::1" ||
        this == "0.0.0.0" ||
        startsWith("127.")
  }
}
