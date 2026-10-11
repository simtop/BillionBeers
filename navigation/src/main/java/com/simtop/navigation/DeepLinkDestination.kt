package com.simtop.navigation

import android.net.Uri
import com.simtop.navigation.contract.DeepLinkDestination
import com.simtop.navigation.contract.DeepLinkParser as PortableDeepLinkParser

object DeepLinkParser {
  const val SCHEME = PortableDeepLinkParser.SCHEME
  const val HOST_BEERS = PortableDeepLinkParser.HOST_BEERS
  const val HOST_FAVORITES = PortableDeepLinkParser.HOST_FAVORITES

  fun parse(scheme: String?, host: String?, pathSegments: List<String>): DeepLinkDestination? =
    PortableDeepLinkParser.parse(scheme, host, pathSegments)
}

fun Uri.toDeepLinkDestination(): DeepLinkDestination? =
  DeepLinkParser.parse(scheme, host, pathSegments)
