package com.simtop.navigation

import android.net.Uri

sealed interface DeepLinkDestination {
  data object BeersList : DeepLinkDestination

  data class BeerDetail(val beerId: String) : DeepLinkDestination
}

// Takes decomposed Uri parts (not android.net.Uri itself) so this stays a pure function testable
// on the plain JVM - android.net.Uri's methods throw when unmocked outside instrumented tests.
object DeepLinkParser {
  const val SCHEME = "billionbeers"
  const val HOST_BEERS = "beers"

  fun parse(scheme: String?, host: String?, pathSegments: List<String>): DeepLinkDestination? {
    if (scheme != SCHEME || host != HOST_BEERS) return null
    val beerId = pathSegments.firstOrNull()
    return if (beerId.isNullOrBlank()) {
      DeepLinkDestination.BeersList
    } else {
      DeepLinkDestination.BeerDetail(beerId)
    }
  }
}

fun Uri.toDeepLinkDestination(): DeepLinkDestination? =
  DeepLinkParser.parse(scheme, host, pathSegments)
