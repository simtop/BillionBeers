package com.simtop.billionbeers.iosshared

import com.simtop.navigation.contract.DeepLinkDestination
import com.simtop.navigation.contract.DeepLinkParser

internal fun parseIosDeepLink(
  scheme: String?,
  host: String?,
  pathSegments: List<String>,
): DeepLinkDestination? = DeepLinkParser.parse(scheme, host, pathSegments)
