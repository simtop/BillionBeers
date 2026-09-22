package com.simtop.billionbeers.iosshared

import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.navigation.contract.DeepLinkDestination
import com.simtop.navigation.contract.PortableRoute

internal suspend fun resolveIosDeepLink(
  scheme: String?,
  host: String?,
  pathSegments: List<String>,
  repository: BeersRepository,
): PortableRoute? =
  when (val destination = parseIosDeepLink(scheme, host, pathSegments)) {
    DeepLinkDestination.BeersList -> PortableRoute.BeersList
    DeepLinkDestination.Favorites -> PortableRoute.Favorites
    is DeepLinkDestination.BeerDetail ->
      repository.getBeerById(destination.beerId)?.let(Beer::toPortableDetail)
    null -> null
  }

private fun Beer.toPortableDetail(): PortableRoute = PortableRoute.BeerDetail(this)
