package com.simtop.core.network

import kotlinx.serialization.json.Json

/**
 * The one JSON config for every API response. Lives here rather than inline in `NetworkingModule`
 * so tests parse with the exact same leniency production does - a test-local `Json { … }` drifts,
 * and a stricter test deserializer fails on payloads production would happily coerce.
 */
val NetworkJson: Json = Json {
  ignoreUnknownKeys = true
  coerceInputValues = true
  isLenient = true
}
