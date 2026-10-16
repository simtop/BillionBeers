package com.simtop.billionbeers.iosshared

import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.navigation.contract.DeepLinkParser
import com.simtop.navigation.contract.PortableRoute

internal suspend fun resolveIosDeepLink(
  scheme: String?,
  host: String?,
  pathSegments: List<String>,
  repository: BeersRepository,
): PortableRoute? {
  val route = DeepLinkParser.parse(scheme, host, pathSegments) ?: return null
  return when (route) {
    is PortableRoute.BeerDetail -> repository.getBeerById(route.beerId)?.let { route }
    else -> route
  }
}
