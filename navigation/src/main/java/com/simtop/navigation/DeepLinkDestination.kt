package com.simtop.navigation

import android.net.Uri
import com.simtop.navigation.contract.DeepLinkDestination as PortableDeepLinkDestination
import com.simtop.navigation.contract.DeepLinkParser as PortableDeepLinkParser

sealed interface DeepLinkDestination {
  data object BeersList : DeepLinkDestination

  data object Favorites : DeepLinkDestination

  data class BeerDetail(val beerId: String) : DeepLinkDestination
}

object DeepLinkParser {
  const val SCHEME = PortableDeepLinkParser.SCHEME
  const val HOST_BEERS = PortableDeepLinkParser.HOST_BEERS
  const val HOST_FAVORITES = PortableDeepLinkParser.HOST_FAVORITES

  fun parse(scheme: String?, host: String?, pathSegments: List<String>): DeepLinkDestination? =
    PortableDeepLinkParser.parse(scheme, host, pathSegments)?.toAndroidDestination()
}

private fun PortableDeepLinkDestination.toAndroidDestination(): DeepLinkDestination =
  when (this) {
    PortableDeepLinkDestination.BeersList -> DeepLinkDestination.BeersList
    PortableDeepLinkDestination.Favorites -> DeepLinkDestination.Favorites
    is PortableDeepLinkDestination.BeerDetail -> DeepLinkDestination.BeerDetail(beerId)
  }

fun Uri.toDeepLinkDestination(): DeepLinkDestination? =
  DeepLinkParser.parse(scheme, host, pathSegments)
